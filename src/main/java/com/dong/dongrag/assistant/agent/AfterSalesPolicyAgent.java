package com.dong.dongrag.assistant.agent;

import com.dong.dongrag.assistant.tool.KnowledgeBaseSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class AfterSalesPolicyAgent implements ComplaintWorkerAgent {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private KnowledgeBaseSearchTool knowledgeBaseSearchTool;

    @Override
    public String type() {
        return "AFTER_SALES";
    }

    @Override
    public String handle(String message, Long groupId, int topK, String conversationId) {
        return chatClientBuilder.build().prompt()
                .system("""
                        你是售后政策专家，负责退换修规则、补偿策略和人工转接建议。
                        你必须调用 KB_SEARCH 获取证据后再输出建议。
                        输出包含：可执行策略、所需材料、是否建议转人工。
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
