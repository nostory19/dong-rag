package com.dong.dongrag.assistant.agent;

import com.dong.dongrag.assistant.model.ComplaintResponse;
import com.dong.dongrag.assistant.model.WorkerResult;
import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Merges parallel worker outputs into a single user-facing response (replaces legacy SummaryAgent).
 */
@Component
public class ResponseAggregator {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * @param userPromptBundle 用户侧上下文（可含多轮摘要 + 本轮问题），与 Worker 输出一并交给汇总模型
     */
    public ComplaintResponse aggregate(String userPromptBundle, List<WorkerResult> workerResults, AgentTemplateId templateId) {
        try {
            StringBuilder workerText = new StringBuilder();
            for (WorkerResult workerResult : workerResults) {
                workerText.append("[")
                        .append(workerResult.getAgentType())
                        .append("]")
                        .append(workerResult.getContent())
                        .append("\n\n");
            }
            String system = switch (templateId) {
                case INTERNAL_KB_MULTI -> """
                        你是企业内部多专家协作总结助手。
                        请整合各专家基于知识库的结论，输出 JSON，不得输出其他文本：
                        {
                          "reply": "给员工的可执行答复（语气专业、简洁）",
                          "actions": ["建议下一步1", "建议下一步2"],
                          "humanHandoff": false,
                          "escalationReason": ""
                        }
                        humanHandoff 仅在确有合规/权限/重大风险且专家明确建议升级时为 true。
                        """;
                case COMPLAINT_MULTI_LEGACY -> """
                        你是投诉处理总结专家。
                        请整合多智能体结果并输出 JSON，不得输出其他文本：
                        {
                          "reply": "给用户的最终答复",
                          "actions": ["行动项1", "行动项2"],
                          "humanHandoff": false,
                          "escalationReason": ""
                        }
                        """;
                default -> """
                        你是企业内部知识库问答总结助手。
                        请整合多智能体结果并输出 JSON，不得输出其他文本：
                        {
                          "reply": "给员工的最终答复",
                          "actions": ["行动项1", "行动项2"],
                          "humanHandoff": false,
                          "escalationReason": ""
                        }
                        """;
            };
            String json = chatClientBuilder.build().prompt()
                    .system(system)
                    .user("""
                            用户侧上下文与本轮问题:
                            %s

                            多智能体结果:
                            %s
                            """.formatted(userPromptBundle, workerText))
                    .call()
                    .content();
            return objectMapper.readValue(cleanJson(json), ComplaintResponse.class);
        } catch (Exception e) {
            ComplaintResponse fallback = new ComplaintResponse();
            if (templateId == AgentTemplateId.COMPLAINT_MULTI_LEGACY) {
                fallback.setReply("抱歉给您带来不便。已记录您的投诉，我们将尽快安排人工专员跟进处理。");
                fallback.setActions(List.of("请补充订单号", "请补充故障截图或录屏", "请保持联系方式畅通"));
                fallback.setHumanHandoff(true);
                fallback.setEscalationReason("SUMMARY_PARSE_FAILED");
            } else {
                fallback.setReply("暂时无法生成结构化总结。请根据上方各专家段落自行判断，或联系对口同事。");
                fallback.setActions(List.of("请重试", "若持续失败请联系管理员"));
                fallback.setHumanHandoff(true);
                fallback.setEscalationReason("SUMMARY_PARSE_FAILED");
            }
            return fallback;
        }
    }

    private String cleanJson(String text) {
        if (text == null) {
            return "{}";
        }
        return text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
    }
}
