package com.dong.dongrag.assistant.agent;

import com.dong.dongrag.assistant.model.ComplaintResponse;
import com.dong.dongrag.assistant.model.WorkerResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SummaryAgent {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ObjectMapper objectMapper;

    public ComplaintResponse summarize(String originalRequest, List<WorkerResult> workerResults) {
        try {
            StringBuilder workerText = new StringBuilder();
            for (WorkerResult workerResult : workerResults) {
                workerText.append("[")
                        .append(workerResult.getAgentType())
                        .append("]")
                        .append(workerResult.getContent())
                        .append("\n\n");
            }
            String json = chatClientBuilder.build().prompt()
                    .system("""
                            你是投诉处理总结专家。
                            请整合多智能体结果并输出 JSON，不得输出其他文本：
                            {
                              "reply": "给用户的最终答复",
                              "actions": ["行动项1", "行动项2"],
                              "humanHandoff": false,
                              "escalationReason": ""
                            }
                            """)
                    .user("""
                            用户原始投诉:
                            %s

                            多智能体结果:
                            %s
                            """.formatted(originalRequest, workerText))
                    .call()
                    .content();
            return objectMapper.readValue(cleanJson(json), ComplaintResponse.class);
        } catch (Exception e) {
            ComplaintResponse fallback = new ComplaintResponse();
            fallback.setReply("抱歉给您带来不便。已记录您的投诉，我们将尽快安排人工专员跟进处理。");
            fallback.setActions(List.of("请补充订单号", "请补充故障截图或录屏", "请保持联系方式畅通"));
            fallback.setHumanHandoff(true);
            fallback.setEscalationReason("SUMMARY_PARSE_FAILED");
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
