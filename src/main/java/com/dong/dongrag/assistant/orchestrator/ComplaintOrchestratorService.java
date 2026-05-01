package com.dong.dongrag.assistant.orchestrator;

import com.dong.dongrag.assistant.agent.ComplaintWorkerAgent;
import com.dong.dongrag.assistant.agent.SummaryAgent;
import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.model.ComplaintResponse;
import com.dong.dongrag.assistant.model.TaskPlan;
import com.dong.dongrag.assistant.model.WorkerResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ComplaintOrchestratorService {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private SummaryAgent summaryAgent;

    @Resource
    private List<ComplaintWorkerAgent> workerAgents;

    public ComplaintProcessResult process(String userMessage, Long groupId, int topK, String conversationId) {
        return process(userMessage, groupId, topK, conversationId, null);
    }

    public ComplaintProcessResult process(String userMessage, Long groupId, int topK, String conversationId,
                                          OrchestratorEventListener eventListener) {
        TaskPlan taskPlan = createPlan(userMessage);
        if (eventListener != null) {
            eventListener.onPlan(taskPlan);
        }
        Map<String, ComplaintWorkerAgent> workerMap = workerAgents.stream()
                .collect(java.util.stream.Collectors.toMap(ComplaintWorkerAgent::type, item -> item));

        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();
        for (TaskPlan.SubTask subTask : taskPlan.getSubTasks()) {
            if (eventListener != null) {
                eventListener.onWorkerStart(subTask);
            }
            futures.add(CompletableFuture.supplyAsync(() -> {
                ComplaintWorkerAgent agent = workerMap.get(subTask.getAssignedAgent());
                long start = System.currentTimeMillis();
                WorkerResult result = new WorkerResult();
                result.setSubTaskId(subTask.getId());
                result.setAgentType(subTask.getAssignedAgent());
                try {
                    if (agent == null) {
                        result.setContent("未找到可处理该任务的 Agent。");
                        result.setRequiresEscalation(true);
                    } else {
                        String content = agent.handle(subTask.getDescription() + "\n原始投诉: " + userMessage, groupId, topK, conversationId);
                        result.setContent(content);
                        result.setRequiresEscalation(content.contains("转人工") || content.contains("工单"));
                    }
                } catch (Exception e) {
                    result.setContent("子任务处理失败: " + e.getMessage());
                    result.setRequiresEscalation(true);
                }
                result.setCostMs(System.currentTimeMillis() - start);
                if (eventListener != null) {
                    eventListener.onWorkerDone(result);
                }
                return result;
            }, executor));
        }
        List<WorkerResult> workerResults = futures.stream().map(CompletableFuture::join)
                .sorted(Comparator.comparing(WorkerResult::getSubTaskId))
                .toList();
        ComplaintResponse complaintResponse = summaryAgent.summarize(userMessage, workerResults);

        ComplaintProcessResult processResult = new ComplaintProcessResult();
        processResult.setTaskPlan(taskPlan);
        processResult.setWorkerResults(workerResults);
        processResult.setComplaintResponse(complaintResponse);
        return processResult;
    }

    private TaskPlan createPlan(String userMessage) {
        try {
            String json = chatClientBuilder.build().prompt()
                    .system("""
                            你是投诉任务调度器。请把用户投诉拆解成可并行子任务，并输出 JSON：
                            {
                              "originalRequest": "...",
                              "subTasks": [
                                {"id":1,"description":"...","assignedAgent":"TECH_SUPPORT|PRODUCT|AFTER_SALES","keywords":["..."]}
                              ]
                            }
                            至少生成1个子任务，最多3个。
                            """)
                    .user(userMessage)
                    .call()
                    .content();
            TaskPlan plan = objectMapper.readValue(cleanJson(json), TaskPlan.class);
            if (plan.getSubTasks() == null || plan.getSubTasks().isEmpty()) {
                return fallbackPlan(userMessage);
            }
            return plan;
        } catch (Exception e) {
            return fallbackPlan(userMessage);
        }
    }

    private TaskPlan fallbackPlan(String userMessage) {
        TaskPlan plan = new TaskPlan();
        plan.setOriginalRequest(userMessage);
        List<TaskPlan.SubTask> subTasks = new ArrayList<>();
        TaskPlan.SubTask tech = new TaskPlan.SubTask();
        tech.setId(1);
        tech.setAssignedAgent("TECH_SUPPORT");
        tech.setDescription("分析投诉中的技术问题并给出排查建议");
        tech.setKeywords(List.of("故障", "排查"));
        subTasks.add(tech);
        TaskPlan.SubTask product = new TaskPlan.SubTask();
        product.setId(2);
        product.setAssignedAgent("PRODUCT");
        product.setDescription("分析投诉中的产品缺陷与版本影响");
        product.setKeywords(List.of("闪退", "缺陷"));
        subTasks.add(product);
        TaskPlan.SubTask afterSales = new TaskPlan.SubTask();
        afterSales.setId(3);
        afterSales.setAssignedAgent("AFTER_SALES");
        afterSales.setDescription("给出售后处理与补偿政策建议");
        afterSales.setKeywords(List.of("售后", "补偿"));
        subTasks.add(afterSales);
        plan.setSubTasks(subTasks);
        return plan;
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
