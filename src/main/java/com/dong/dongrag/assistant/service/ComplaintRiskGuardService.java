package com.dong.dongrag.assistant.service;

import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.model.ComplaintResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComplaintRiskGuardService {

    private static final List<String> HIGH_RISK_KEYWORDS = List.of("赔偿", "起诉", "曝光", "维权", "律师", "监管", "媒体");

    public void applyRiskPolicy(String userMessage, ComplaintProcessResult processResult) {
        if (processResult == null || processResult.getComplaintResponse() == null) {
            return;
        }
        ComplaintResponse response = processResult.getComplaintResponse();
        boolean highRisk = HIGH_RISK_KEYWORDS.stream().anyMatch(userMessage::contains);
        boolean workerEscalation = processResult.getWorkerResults() != null
                && processResult.getWorkerResults().stream().anyMatch(item -> item.isRequiresEscalation());
        if (highRisk || workerEscalation) {
            response.setHumanHandoff(true);
            if (highRisk) {
                response.setEscalationReason("HIGH_RISK_KEYWORD");
            } else if (response.getEscalationReason() == null || response.getEscalationReason().isBlank()) {
                response.setEscalationReason("WORKER_REQUESTED_ESCALATION");
            }
        }
    }
}
