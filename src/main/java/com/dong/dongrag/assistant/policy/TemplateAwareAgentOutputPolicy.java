package com.dong.dongrag.assistant.policy;

import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.model.ComplaintResponse;
import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Post-aggregation guardrails: complaint high-risk keywords vs internal KB (worker-only escalation).
 */
@Component
public class TemplateAwareAgentOutputPolicy implements AgentOutputPolicy {

    private static final List<String> HIGH_RISK_KEYWORDS = List.of("赔偿", "起诉", "曝光", "维权", "律师", "监管", "媒体");

    @Override
    public void apply(AgentPolicyContext ctx) {
        if (ctx.getProcessResult() == null || ctx.getProcessResult().getComplaintResponse() == null) {
            return;
        }
        ComplaintResponse response = ctx.getProcessResult().getComplaintResponse();
        AgentTemplateId templateId = ctx.getTemplateId() == null ? AgentTemplateId.INTERNAL_KB_SIMPLE : ctx.getTemplateId();

        boolean workerEscalation = ctx.getProcessResult().getWorkerResults() != null
                && ctx.getProcessResult().getWorkerResults().stream().anyMatch(item -> item.isRequiresEscalation());

        if (templateId == AgentTemplateId.COMPLAINT_MULTI_LEGACY) {
            boolean highRisk = ctx.getUserMessage() != null
                    && HIGH_RISK_KEYWORDS.stream().anyMatch(ctx.getUserMessage()::contains);
            if (highRisk || workerEscalation) {
                response.setHumanHandoff(true);
                if (highRisk) {
                    response.setEscalationReason("HIGH_RISK_KEYWORD");
                } else if (response.getEscalationReason() == null || response.getEscalationReason().isBlank()) {
                    response.setEscalationReason("WORKER_REQUESTED_ESCALATION");
                }
            }
            return;
        }

        if (templateId == AgentTemplateId.INTERNAL_KB_MULTI && workerEscalation) {
            response.setHumanHandoff(true);
            if (response.getEscalationReason() == null || response.getEscalationReason().isBlank()) {
                response.setEscalationReason("WORKER_REQUESTED_ESCALATION");
            }
        }
    }

    /**
     * Convenience for services that only have user message + result (complaint eval path).
     */
    public void applyComplaintLegacy(String userMessage, ComplaintProcessResult processResult) {
        apply(AgentPolicyContext.builder()
                .userMessage(userMessage)
                .processResult(processResult)
                .templateId(AgentTemplateId.COMPLAINT_MULTI_LEGACY)
                .build());
    }
}
