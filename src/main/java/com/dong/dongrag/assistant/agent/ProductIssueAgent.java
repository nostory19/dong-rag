package com.dong.dongrag.assistant.agent;

import com.dong.dongrag.assistant.tool.KnowledgeBaseSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ProductIssueAgent implements ComplaintWorkerAgent {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private KnowledgeBaseSearchTool knowledgeBaseSearchTool;

    @Override
    public String type() {
        return "PRODUCT";
    }

    @Override
    public String handle(String message, Long groupId, int topK, String conversationId) {
        return chatClientBuilder.build().prompt()
                .system("""
                        你是产品问题专家，负责缺陷归因、版本建议、复现条件收集。
                        你必须调用 KB_SEARCH 获取证据后再输出建议。
                        输出包含：可能缺陷、临时方案、后续跟进点。
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
