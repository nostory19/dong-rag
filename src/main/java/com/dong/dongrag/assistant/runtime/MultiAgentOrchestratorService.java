package com.dong.dongrag.assistant.runtime;

import com.dong.dongrag.assistant.agent.ResponseAggregator;
import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.model.ComplaintResponse;
import com.dong.dongrag.assistant.model.TaskPlan;
import com.dong.dongrag.assistant.model.WorkerResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class MultiAgentOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentOrchestratorService.class);

    private static final int MAX_SUBTASKS = 3;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private ResponseAggregator responseAggregator;

    @Resource
    private WorkerRegistry workerRegistry;

    public ComplaintProcessResult run(AgentRunContext ctx, String latestUserMessage, OrchestratorEventListener eventListener) {
        return run(ctx, latestUserMessage, latestUserMessage, latestUserMessage, eventListener);
    }

    /**
     * @param latestUserMessage 本轮用户原文（Worker 检索与追问仍以该文本为主）
     * @param plannerPayload    传给 Planner 的用户侧全文（可含历史摘要 + 槽位 + 本轮）
     * @param aggregatorPayload 传给汇总模型的用户侧全文
     */
    public ComplaintProcessResult run(AgentRunContext ctx, String latestUserMessage, String plannerPayload,
                                      String aggregatorPayload, OrchestratorEventListener eventListener) {
        log.info("[traceId={}] Multi-agent run start template={} groupId={}", ctx.getTraceId(), ctx.getTemplateId(), ctx.getGroupId());
        TaskPlan taskPlan = createPlan(plannerPayload, ctx);
        taskPlan = sanitizePlan(taskPlan, latestUserMessage, ctx);
        if (eventListener != null) {
            eventListener.onPlan(taskPlan);
        }

        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();
        for (TaskPlan.SubTask subTask : taskPlan.getSubTasks()) {
            if (eventListener != null) {
                eventListener.onWorkerStart(subTask);
            }
            futures.add(CompletableFuture.supplyAsync(() -> {
                DomainWorker agent = workerRegistry.resolve(subTask.getAssignedAgent());
                WorkerResult result;
                if (agent == null) {
                    result = missingAgentResult(subTask);
                } else {
                    result = agent.execute(subTask, latestUserMessage, ctx);
                }
                if (eventListener != null) {
                    eventListener.onWorkerDone(result);
                }
                return result;
            }, executor));
        }
        List<WorkerResult> workerResults = futures.stream().map(CompletableFuture::join)
                .sorted(Comparator.comparing(WorkerResult::getSubTaskId))
                .toList();
        ComplaintResponse response = responseAggregator.aggregate(aggregatorPayload, workerResults, ctx.getTemplateId());

        ComplaintProcessResult processResult = new ComplaintProcessResult();
        processResult.setTaskPlan(taskPlan);
        processResult.setWorkerResults(workerResults);
        processResult.setComplaintResponse(response);
        log.info("[traceId={}] Multi-agent run done subtasks={}", ctx.getTraceId(), workerResults.size());
        return processResult;
    }

    private WorkerResult missingAgentResult(TaskPlan.SubTask subTask) {
        WorkerResult result = new WorkerResult();
        result.setSubTaskId(subTask.getId());
        result.setAgentType(subTask.getAssignedAgent());
        result.setContent("未找到可处理该任务的 Agent。");
        result.setRequiresEscalation(true);
        result.setEscalationReasonCode("AGENT_MISSING");
        result.setCostMs(0);
        return result;
    }

    private TaskPlan createPlan(String userMessage, AgentRunContext ctx) {
        try {
            Set<String> allowed = ctx.getAllowedWorkerTypes();
            Objects.requireNonNull(allowed);
            String allowedCsv = allowed.stream().sorted().collect(Collectors.joining(", "));
            String system = switch (ctx.getTemplateId()) {
                case COMPLAINT_MULTI_LEGACY -> """
                        你是投诉任务调度器。请把用户投诉拆解成可并行子任务，并输出 JSON：
                        {
                          "originalRequest": "...",
                          "intent": "可选简短意图",
                          "requiresHuman": false,
                          "subTasks": [
                            {"id":1,"description":"...","assignedAgent":"TECH_SUPPORT","keywords":["..."]}
                          ]
                        }
                        assignedAgent 必须是以下之一（字符串精确匹配，勿发明未列出类型）: %s
                        至少生成1个子任务，最多3个。
                        """.formatted(allowedCsv);
                case INTERNAL_KB_MULTI -> """
                        你是企业内部多任务调度器。请将员工问题拆解为可并行子任务，并输出 JSON：
                        {
                          "originalRequest": "...",
                          "intent": "可选简短意图",
                          "requiresHuman": false,
                          "subTasks": [
                            {"id":1,"description":"...","assignedAgent":"GENERAL_KB","keywords":["..."]}
                          ]
                        }
                        assignedAgent 必须是以下之一（字符串精确匹配，勿发明未列出类型）: %s
                        GENERAL_KB 适合制度/流程综合问题；TECH_SUPPORT 偏软硬件故障；PRODUCT 偏产品缺陷；AFTER_SALES 偏售后政策。
                        至少生成1个子任务，最多3个。
                        """.formatted(allowedCsv);
                default -> """
                        你是任务调度器。输出 JSON：
                        {"originalRequest":"...","subTasks":[{"id":1,"description":"...","assignedAgent":"GENERAL_KB","keywords":[]}]}
                        assignedAgent 必须是: %s
                        """.formatted(allowedCsv);
            };
            String json = chatClientBuilder.build().prompt()
                    .system(system)
                    .user(userMessage)
                    .call()
                    .content();
            TaskPlan plan = objectMapper.readValue(cleanJson(json), TaskPlan.class);
            if (plan.getSubTasks() == null || plan.getSubTasks().isEmpty()) {
                return fallbackPlan(userMessage, ctx);
            }
            return plan;
        } catch (Exception e) {
            log.warn("[traceId={}] Planner parse failed, fallback: {}", ctx.getTraceId(), e.getMessage());
            return fallbackPlan(userMessage, ctx);
        }
    }

    private TaskPlan sanitizePlan(TaskPlan plan, String userMessage, AgentRunContext ctx) {
        Set<String> allowed = ctx.getAllowedWorkerTypes();
        if (allowed == null || allowed.isEmpty()) {
            return fallbackPlan(userMessage, ctx);
        }
        if (plan == null || plan.getSubTasks() == null || plan.getSubTasks().isEmpty()) {
            return fallbackPlan(userMessage, ctx);
        }
        List<TaskPlan.SubTask> trimmed = new ArrayList<>();
        for (TaskPlan.SubTask subTask : plan.getSubTasks()) {
            if (trimmed.size() >= MAX_SUBTASKS) {
                break;
            }
            if (subTask.getAssignedAgent() == null || !allowed.contains(subTask.getAssignedAgent())) {
                subTask.setAssignedAgent(pickFallbackAgentType(allowed));
            }
            trimmed.add(subTask);
        }
        if (trimmed.isEmpty()) {
            return fallbackPlan(userMessage, ctx);
        }
        for (int i = 0; i < trimmed.size(); i++) {
            trimmed.get(i).setId(i + 1);
        }
        plan.setSubTasks(trimmed);
        if (plan.getOriginalRequest() == null || plan.getOriginalRequest().isBlank()) {
            plan.setOriginalRequest(userMessage);
        }
        return plan;
    }

    private TaskPlan fallbackPlan(String userMessage, AgentRunContext ctx) {
        Set<String> allowed = ctx.getAllowedWorkerTypes();
        TaskPlan plan = new TaskPlan();
        plan.setOriginalRequest(userMessage);
        TaskPlan.SubTask one = new TaskPlan.SubTask();
        one.setId(1);
        one.setAssignedAgent(pickFallbackAgentType(allowed));
        one.setDescription(ctx.getTemplateId() == AgentTemplateId.COMPLAINT_MULTI_LEGACY
                ? "综合理解用户投诉并基于知识库给出处理建议"
                : "综合理解员工问题并基于部门知识库给出答复要点");
        one.setKeywords(List.of("fallback"));
        plan.setSubTasks(new ArrayList<>(List.of(one)));
        return plan;
    }

    private String pickFallbackAgentType(Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return "GENERAL_KB";
        }
        if (allowed.contains("GENERAL_KB")) {
            return "GENERAL_KB";
        }
        return new TreeSet<>(allowed).first();
    }

    private String cleanJson(String text) {
        if (text == null) {
            return "{}";
        }
        return text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
    }

    public interface OrchestratorEventListener {
        void onPlan(TaskPlan taskPlan);

        void onWorkerStart(TaskPlan.SubTask subTask);

        void onWorkerDone(WorkerResult workerResult);
    }
}
