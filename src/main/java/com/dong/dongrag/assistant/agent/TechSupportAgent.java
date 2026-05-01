package com.dong.dongrag.assistant.agent;

import com.dong.dongrag.assistant.tool.KnowledgeBaseSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class TechSupportAgent implements ComplaintWorkerAgent {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private KnowledgeBaseSearchTool knowledgeBaseSearchTool;

    @Override
    public String type() {
        return "TECH_SUPPORT";
    }

    @Override
    public String handle(String message, Long groupId, int topK, String conversationId) {
        return chatClientBuilder.build().prompt()
                .system("""
                        你是技术支持专家，负责产品硬件故障与软件故障排查。
                        你必须调用 KB_SEARCH 获取证据后再输出建议。
                        输出包含：排查步骤、可能原因、用户需补充的信息。
                        """)
                .user("""
                        groupId: %d
                        topK: %d
                        conversationId: %s
                        用户问题: %s
                        """.formatted(groupId, topK, conversationId, message))
                .tools(knowledgeBaseSearchTool)
                .call()
                .content();
    }
}
