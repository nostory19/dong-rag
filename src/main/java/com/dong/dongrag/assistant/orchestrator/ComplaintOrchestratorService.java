package com.dong.dongrag.assistant.orchestrator;

import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.runtime.AgentRunContext;
import com.dong.dongrag.assistant.runtime.AgentRunContextFactory;
import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import com.dong.dongrag.assistant.runtime.MultiAgentOrchestratorService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Backward-compatible facade for complaint-style runs; delegates to {@link MultiAgentOrchestratorService}.
 */
@Service
public class ComplaintOrchestratorService {

    @Resource
    private MultiAgentOrchestratorService multiAgentOrchestratorService;

    @Resource
    private AgentRunContextFactory agentRunContextFactory;

    public ComplaintProcessResult process(String userMessage, Long groupId, int topK, String conversationId) {
        return process(userMessage, groupId, topK, conversationId, null);
    }

    public ComplaintProcessResult process(String userMessage, Long groupId, int topK, String conversationId,
                                          MultiAgentOrchestratorService.OrchestratorEventListener eventListener) {
        AgentRunContext ctx = agentRunContextFactory.build(
                null,
                groupId,
                topK,
                conversationId,
                UUID.randomUUID().toString(),
                AgentTemplateId.COMPLAINT_MULTI_LEGACY
        );
        return multiAgentOrchestratorService.run(ctx, userMessage, eventListener);
    }
}
