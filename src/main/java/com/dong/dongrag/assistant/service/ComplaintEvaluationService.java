package com.dong.dongrag.assistant.service;

import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.orchestrator.ComplaintOrchestratorService;
import com.dong.dongrag.assistant.policy.AgentPolicyContext;
import com.dong.dongrag.assistant.policy.TemplateAwareAgentOutputPolicy;
import com.dong.dongrag.assistant.runtime.AgentRunContext;
import com.dong.dongrag.assistant.runtime.AgentRunContextFactory;
import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import com.dong.dongrag.assistant.runtime.MultiAgentOrchestratorService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ComplaintEvaluationService {

    private static final List<String> COMPLAINT_CASES = List.of(
            "手机充不进电，而且App一直闪退，你们质量太差了",
            "我要投诉你们虚假宣传，考虑曝光并维权",
            "耳机连接后断断续续，客服态度也不好"
    );

    private static final List<String> INTERNAL_KB_CASES = List.of(
            "新员工入职需要提交哪些材料？",
            "年假天数如何计算？",
            "VPN 无法连接该如何排查？"
    );

    @Resource
    private ComplaintOrchestratorService complaintOrchestratorService;

    @Resource
    private MultiAgentOrchestratorService multiAgentOrchestratorService;

    @Resource
    private AgentRunContextFactory agentRunContextFactory;

    @Resource
    private TemplateAwareAgentOutputPolicy templateAwareAgentOutputPolicy;

    public Map<String, Object> quickEvaluate(Long groupId) {
        return quickEvaluate(groupId, AgentTemplateId.COMPLAINT_MULTI_LEGACY.name());
    }

    /**
     * Batch smoke evaluation for admin. {@code templateId} uses {@link AgentTemplateId} enum name.
     */
    public Map<String, Object> quickEvaluate(Long groupId, String templateIdCode) {
        AgentTemplateId templateId = AgentTemplateId.fromCode(templateIdCode);
        if (templateId == AgentTemplateId.INTERNAL_KB_SIMPLE) {
            return Map.of(
                    "error", "Choose INTERNAL_KB_MULTI or COMPLAINT_MULTI_LEGACY for multi-agent eval",
                    "templateId", templateId.name()
            );
        }

        List<String> cases = templateId == AgentTemplateId.COMPLAINT_MULTI_LEGACY ? COMPLAINT_CASES : INTERNAL_KB_CASES;

        List<Map<String, Object>> details = new ArrayList<>();
        int handoffCount = 0;
        int totalSubTasks = 0;
        for (String message : cases) {
            ComplaintProcessResult result = runOnce(groupId, templateId, message);
            templateAwareAgentOutputPolicy.apply(AgentPolicyContext.builder()
                    .userMessage(message)
                    .processResult(result)
                    .templateId(templateId)
                    .build());
            if (result.getComplaintResponse().isHumanHandoff()) {
                handoffCount++;
            }
            int subTaskSize = result.getTaskPlan().getSubTasks() == null ? 0 : result.getTaskPlan().getSubTasks().size();
            totalSubTasks += subTaskSize;
            details.add(Map.of(
                    "message", message,
                    "subTaskCount", subTaskSize,
                    "handoff", result.getComplaintResponse().isHumanHandoff(),
                    "reason", result.getComplaintResponse().getEscalationReason() == null ? "" : result.getComplaintResponse().getEscalationReason(),
                    "templateId", templateId.name()
            ));
        }
        return Map.of(
                "caseCount", cases.size(),
                "templateId", templateId.name(),
                "handoffRate", (double) handoffCount / cases.size(),
                "avgSubTaskCount", (double) totalSubTasks / cases.size(),
                "details", details
        );
    }

    private ComplaintProcessResult runOnce(Long groupId, AgentTemplateId templateId, String message) {
        if (templateId == AgentTemplateId.COMPLAINT_MULTI_LEGACY) {
            return complaintOrchestratorService.process(message, groupId, 5, UUID.randomUUID().toString());
        }
        AgentRunContext ctx = agentRunContextFactory.build(
                null,
                groupId,
                5,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                templateId
        );
        ComplaintProcessResult result = multiAgentOrchestratorService.run(ctx, message, null);
        return result;
    }
}
