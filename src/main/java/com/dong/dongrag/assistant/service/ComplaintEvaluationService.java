package com.dong.dongrag.assistant.service;

import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.orchestrator.ComplaintOrchestratorService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ComplaintEvaluationService {

    @Resource
    private ComplaintOrchestratorService complaintOrchestratorService;

    @Resource
    private ComplaintRiskGuardService complaintRiskGuardService;

    public Map<String, Object> quickEvaluate(Long groupId) {
        List<String> cases = List.of(
                "手机充不进电，而且App一直闪退，你们质量太差了",
                "我要投诉你们虚假宣传，考虑曝光并维权",
                "耳机连接后断断续续，客服态度也不好"
        );
        List<Map<String, Object>> details = new ArrayList<>();
        int handoffCount = 0;
        int totalSubTasks = 0;
        for (String message : cases) {
            ComplaintProcessResult result = complaintOrchestratorService.process(message, groupId, 5, UUID.randomUUID().toString());
            complaintRiskGuardService.applyRiskPolicy(message, result);
            if (result.getComplaintResponse().isHumanHandoff()) {
                handoffCount++;
            }
            int subTaskSize = result.getTaskPlan().getSubTasks() == null ? 0 : result.getTaskPlan().getSubTasks().size();
            totalSubTasks += subTaskSize;
            details.add(Map.of(
                    "message", message,
                    "subTaskCount", subTaskSize,
                    "handoff", result.getComplaintResponse().isHumanHandoff(),
                    "reason", result.getComplaintResponse().getEscalationReason() == null ? "" : result.getComplaintResponse().getEscalationReason()
            ));
        }
        return Map.of(
                "caseCount", cases.size(),
                "handoffRate", (double) handoffCount / cases.size(),
                "avgSubTaskCount", (double) totalSubTasks / cases.size(),
                "details", details
        );
    }
}
