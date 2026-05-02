package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.model.ComplaintResponse;
import com.dong.dongrag.assistant.model.TaskPlan;
import com.dong.dongrag.assistant.model.WorkerResult;
import com.dong.dongrag.assistant.orchestrator.ComplaintOrchestratorService;
import com.dong.dongrag.assistant.service.ComplaintRiskGuardService;
import com.dong.dongrag.assistant.tool.ToolCallTraceContext;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.model.dto.assistant.AssistantChatRequest;
import com.dong.dongrag.service.AssistantService;
import com.dong.dongrag.service.AuthContextService;
import com.dong.dongrag.service.GroupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

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
    private ComplaintOrchestratorService complaintOrchestratorService;

    @Resource
    private ToolCallTraceContext toolCallTraceContext;

    @Resource
    private ComplaintRiskGuardService complaintRiskGuardService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Flux<String> chat(AssistantChatRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || request.getGroupId() == null || StrUtil.isBlank(request.getMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        groupService.checkGroupReadable(userId, request.getGroupId());
        log.info("Assistant chat request accepted, userId={}, groupId={}, topK={}, messageLength={}",
                userId, request.getGroupId(), request.getTopK(), request.getMessage() == null ? 0 : request.getMessage().length());
        return Flux.create(sink -> {
            toolCallTraceContext.start(log -> sendEvent(sink, "tool-log", log));
            sendEvent(sink, "start", "assistant stream started");
            Thread.startVirtualThread(() -> processAssistantRequest(request, sink));
            sink.onDispose(toolCallTraceContext::clear);
            sink.onCancel(toolCallTraceContext::clear);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void processAssistantRequest(AssistantChatRequest request, FluxSink<String> sink) {
        try {
            int topK = request.getTopK() == null ? 5 : request.getTopK();
            String conversationId = UUID.randomUUID().toString();
            log.info("Assistant process started, groupId={}, conversationId={}", request.getGroupId(), conversationId);
            ComplaintProcessResult result = complaintOrchestratorService.process(
                    request.getMessage(), request.getGroupId(), topK, conversationId,
                    new ComplaintOrchestratorService.OrchestratorEventListener() {
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
            complaintRiskGuardService.applyRiskPolicy(request.getMessage(), result);
            ComplaintResponse response = result.getComplaintResponse();
            if (response.isHumanHandoff()) {
                sendEvent(sink, "handoff", toJson(Map.of(
                        "enabled", true,
                        "reason", response.getEscalationReason()
                )));
            }
            streamTextAsToken(response.getReply(), sink);
            sendEvent(sink, "actions", toJson(response.getActions()));
            List<String> logs = toolCallTraceContext.snapshot();
            sendEvent(sink, "tool-log-summary", "tool call logs size=" + logs.size());
            log.info("Assistant process completed, groupId={}, conversationId={}, toolLogCount={}",
                    request.getGroupId(), conversationId, logs.size());
            finishWithDone(sink, "ok");
        } catch (Exception e) {
            sendEvent(sink, "error", e.getMessage());
            log.error("Assistant process failed, groupId={}, error={}", request.getGroupId(), e.getMessage(), e);
            finishWithDone(sink, "error");
        } finally {
            toolCallTraceContext.clear();
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
            // Pace the stream slightly to improve perceived "streaming" and avoid UI jank
            // when the frontend renders on every chunk.
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
        // Newline-delimited JSON (NDJSON) for robust streaming parsing in frontend.
        sink.next(toJson(payload) + "\n");
    }

    private void finishWithDone(FluxSink<String> sink, String status) {
        sendEvent(sink, "done", status);
        if (!sink.isCancelled()) {
            sink.complete();
        }
    }
}
