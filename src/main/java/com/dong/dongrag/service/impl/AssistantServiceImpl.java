package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dong.dongrag.assistant.dialogue.ComplaintSlotMergeUtil;
import com.dong.dongrag.assistant.dialogue.ComplaintSlots;
import com.dong.dongrag.assistant.dialogue.ContextBuilder;
import com.dong.dongrag.assistant.dialogue.ConversationCompressor;
import com.dong.dongrag.assistant.dialogue.GuidanceResult;
import com.dong.dongrag.assistant.dialogue.GuidanceService;
import com.dong.dongrag.assistant.dialogue.IntentRoutingResult;
import com.dong.dongrag.assistant.dialogue.IntentRoutingService;
import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.model.ComplaintResponse;
import com.dong.dongrag.assistant.model.TaskPlan;
import com.dong.dongrag.assistant.model.WorkerResult;
import com.dong.dongrag.assistant.policy.AgentPolicyContext;
import com.dong.dongrag.assistant.policy.TemplateAwareAgentOutputPolicy;
import com.dong.dongrag.assistant.runtime.AgentRunContext;
import com.dong.dongrag.assistant.runtime.AgentRunContextFactory;
import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import com.dong.dongrag.assistant.runtime.MultiAgentOrchestratorService;
import com.dong.dongrag.assistant.tool.ToolCallTraceContext;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.model.dto.assistant.AssistantChatRequest;
import com.dong.dongrag.model.entity.AssistantConversation;
import com.dong.dongrag.service.AssistantConversationService;
import com.dong.dongrag.service.AssistantService;
import com.dong.dongrag.service.AuthContextService;
import com.dong.dongrag.service.GroupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssistantServiceImpl implements AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantServiceImpl.class);

    @Resource
    private AuthContextService authContextService;

    @Resource
    private GroupService groupService;

    @Resource
    private MultiAgentOrchestratorService multiAgentOrchestratorService;

    @Resource
    private AgentRunContextFactory agentRunContextFactory;

    @Resource
    private TemplateAwareAgentOutputPolicy templateAwareAgentOutputPolicy;

    @Resource
    private ToolCallTraceContext toolCallTraceContext;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private AssistantConversationService assistantConversationService;

    @Resource
    private IntentRoutingService intentRoutingService;

    @Resource
    private GuidanceService guidanceService;

    @Resource
    private ContextBuilder contextBuilder;

    @Resource
    private ConversationCompressor conversationCompressor;

    @Resource
    private MeterRegistry meterRegistry;

    /**
     * 知识助手固定走多专家编排；仅当显式传入 {@link AgentTemplateId#COMPLAINT_MULTI_LEGACY} 时使用投诉编排模板。
     * 已废弃的 INTERNAL_KB_SIMPLE 请求值会被归一为 INTERNAL_KB_MULTI。
     */
    private static AgentTemplateId resolveAssistantTemplate(String templateIdCode) {
        AgentTemplateId raw = AgentTemplateId.fromCode(templateIdCode);
        if (raw == AgentTemplateId.INTERNAL_KB_SIMPLE) {
            return AgentTemplateId.INTERNAL_KB_MULTI;
        }
        return raw;
    }

    @Override
    public Flux<String> chat(AssistantChatRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || request.getGroupId() == null || StrUtil.isBlank(request.getMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        groupService.checkGroupReadable(userId, request.getGroupId());
        AgentTemplateId templateId = resolveAssistantTemplate(request.getTemplateId());
        Long conversationId = assistantConversationService.ensureConversation(
                userId, request.getGroupId(), templateId, request.getConversationId());
        request.setConversationId(String.valueOf(conversationId));

        String traceId = UUID.randomUUID().toString();
        log.info("Assistant chat request accepted, traceId={}, conversationId={}, userId={}, groupId={}, template={}, topK={}, messageLength={}",
                traceId, conversationId, userId, request.getGroupId(), templateId, request.getTopK(),
                request.getMessage() == null ? 0 : request.getMessage().length());
        return Flux.create(sink -> {
            sendEvent(sink, "start", Map.of(
                    "traceId", traceId,
                    "conversationId", String.valueOf(conversationId),
                    "template", templateId.name()
            ));
            toolCallTraceContext.start(logLine -> sendEvent(sink, "tool-log", logLine));
            Thread.startVirtualThread(() -> processMultiAgent(request, sink, userId, traceId, templateId, conversationId));
            sink.onDispose(toolCallTraceContext::clear);
            sink.onCancel(toolCallTraceContext::clear);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void processMultiAgent(AssistantChatRequest request, FluxSink<String> sink, Long userId, String traceId,
                                   AgentTemplateId templateId, Long conversationId) {
        Timer.Sample timer = Timer.start(meterRegistry);
        try {
            AssistantConversation convRow = assistantConversationService.getById(conversationId);
            ComplaintSlots slots = parseSlots(convRow == null ? null : convRow.getSlotStateJson());
            ComplaintSlotMergeUtil.mergeFromUserText(slots, request.getMessage());

            IntentRoutingResult intent = intentRoutingService.route(request.getMessage(), templateId);
            sendEvent(sink, "intent", toJson(Map.of(
                    "intent", intent.getIntent(),
                    "confidence", intent.getConfidence(),
                    "source", intent.getSource(),
                    "needsClarification", intent.isNeedsClarification(),
                    "routeKind", intent.getRouteKind()
            )));

            GuidanceResult guidance = guidanceService.build(templateId, slots, intent);
            sendEvent(sink, "guide", toJson(Map.of(
                    "questions", guidance.getQuestions(),
                    "missingSlots", guidance.getMissingSlots(),
                    "slotSummaryLine", guidance.getSlotSummaryLine() == null ? "" : guidance.getSlotSummaryLine()
            )));

            Map<String, Object> userMeta = new HashMap<>();
            userMeta.put("intent", intent.getIntent());
            userMeta.put("confidence", intent.getConfidence());
            userMeta.put("source", intent.getSource());
            userMeta.put("routeKind", intent.getRouteKind());
            assistantConversationService.appendMessage(
                    conversationId, "user", request.getMessage(), intent.getIntent(), traceId, userMeta);

            AssistantConversation convReloaded = assistantConversationService.getById(conversationId);
            String contextBlock = contextBuilder.buildContextBlock(conversationId, convReloaded);
            StringBuilder planner = new StringBuilder();
            if (StrUtil.isNotBlank(contextBlock)) {
                planner.append(contextBlock).append("\n");
            }
            if (StrUtil.isNotBlank(guidance.getSlotSummaryLine())) {
                planner.append(guidance.getSlotSummaryLine()).append("\n");
            }
            planner.append("【本轮用户输入】\n").append(request.getMessage());
            String plannerPayload = planner.toString();

            String aggregatorPayload = plannerPayload;
            if (guidance.getMissingSlots() != null && !guidance.getMissingSlots().isEmpty()) {
                aggregatorPayload = plannerPayload + "\n【仍缺信息】" + String.join(",", guidance.getMissingSlots());
            }

            int topK = request.getTopK() == null ? 5 : request.getTopK();
            AgentRunContext ctx = agentRunContextFactory.build(
                    userId,
                    request.getGroupId(),
                    topK,
                    request.getConversationId(),
                    traceId,
                    templateId
            );
            ComplaintProcessResult result = multiAgentOrchestratorService.run(
                    ctx,
                    request.getMessage(),
                    plannerPayload,
                    aggregatorPayload,
                    new MultiAgentOrchestratorService.OrchestratorEventListener() {
                        @Override
                        public void onPlan(TaskPlan taskPlan) {
                            sendEvent(sink, "route-plan", toJson(Map.of(
                                    "subTaskCount", taskPlan.getSubTasks() == null ? 0 : taskPlan.getSubTasks().size(),
                                    "plan", taskPlan
                            )));
                        }

                        @Override
                        public void onWorkerStart(TaskPlan.SubTask subTask) {
                            sendEvent(sink, "worker-start", toJson(subTask));
                        }

                        @Override
                        public void onWorkerDone(WorkerResult workerResult) {
                            sendEvent(sink, "worker-done", toJson(workerResult));
                        }
                    });

            templateAwareAgentOutputPolicy.apply(AgentPolicyContext.builder()
                    .userMessage(request.getMessage())
                    .processResult(result)
                    .templateId(templateId)
                    .build());

            ComplaintResponse response = result.getComplaintResponse();
            if (response.isHumanHandoff()) {
                sendEvent(sink, "policy-hit", toJson(Map.of(
                        "humanHandoff", true,
                        "reason", response.getEscalationReason()
                )));
            }
            streamTextAsToken(response.getReply(), sink);
            sendEvent(sink, "actions", toJson(response.getActions() == null ? List.of() : response.getActions()));
            List<String> logs = toolCallTraceContext.snapshot();
            sendEvent(sink, "tool-log-summary", "tool call logs size=" + logs.size());

            Map<String, Object> asstMeta = new HashMap<>();
            asstMeta.put("humanHandoff", response.isHumanHandoff());
            asstMeta.put("subTaskCount", result.getTaskPlan().getSubTasks() == null ? 0 : result.getTaskPlan().getSubTasks().size());
            assistantConversationService.appendMessage(
                    conversationId, "assistant", response.getReply(), intent.getIntent(), traceId, asstMeta);

            assistantConversationService.updateSlotStateJson(conversationId, objectMapper.writeValueAsString(slots));

            conversationCompressor.maybeCompressAsync(conversationId);

            log.info("[traceId={}] Assistant MULTI completed, conversationId={}, toolLogCount={}",
                    traceId, conversationId, logs.size());
            finishWithDone(sink, "ok");
        } catch (Exception e) {
            sendEvent(sink, "error", e.getMessage());
            log.error("[traceId={}] Assistant MULTI failed, groupId={}, error={}", traceId, request.getGroupId(), e.getMessage(), e);
            try {
                assistantConversationService.appendMessage(
                        conversationId, "system", "[error] " + e.getMessage(), null, traceId, Map.of("error", true));
            } catch (Exception ignored) {
                // ignore
            }
            finishWithDone(sink, "error");
        } finally {
            timer.stop(Timer.builder("dongrag.assistant.multi_agent")
                    .description("Assistant multi-agent round trip")
                    .register(meterRegistry));
            toolCallTraceContext.clear();
        }
    }

    private ComplaintSlots parseSlots(String json) {
        if (StrUtil.isBlank(json)) {
            return new ComplaintSlots();
        }
        try {
            return objectMapper.readValue(json, ComplaintSlots.class);
        } catch (JsonProcessingException e) {
            return new ComplaintSlots();
        }
    }

    private void streamTextAsToken(String text, FluxSink<String> sink) {
        if (text == null) {
            return;
        }
        int step = 24;
        for (int i = 0; i < text.length(); i += step) {
            if (sink.isCancelled()) {
                return;
            }
            int end = Math.min(text.length(), i + step);
            sendEvent(sink, "token", text.substring(i, end));
            try {
                Thread.sleep(12);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }

    private void sendEvent(FluxSink<String> sink, String event, Object data) {
        if (sink.isCancelled()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "event", event,
                "data", data == null ? "" : data
        );
        sink.next(toJson(payload) + "\n");
    }

    private void finishWithDone(FluxSink<String> sink, String status) {
        sendEvent(sink, "done", status);
        if (!sink.isCancelled()) {
            sink.complete();
        }
    }
}
