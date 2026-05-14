package com.dong.dongrag.assistant.agent;

import com.dong.dongrag.assistant.model.TaskPlan;
import com.dong.dongrag.assistant.model.WorkerResult;
import com.dong.dongrag.assistant.runtime.AgentRunContext;
import com.dong.dongrag.assistant.runtime.DomainWorker;
import com.dong.dongrag.assistant.tool.KnowledgeBaseSearchTool;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Shared KB_SEARCH-backed worker execution.
 */
public abstract class KbToolDomainWorker implements DomainWorker {

    @Resource
    protected ChatClient.Builder chatClientBuilder;

    @Resource
    protected KnowledgeBaseSearchTool knowledgeBaseSearchTool;

    protected abstract String systemPrompt();

    @Override
    public WorkerResult execute(TaskPlan.SubTask subTask, String originalUserMessage, AgentRunContext ctx) {
        long start = System.currentTimeMillis();
        WorkerResult result = new WorkerResult();
        result.setSubTaskId(subTask.getId());
        result.setAgentType(type());
        String message = subTask.getDescription() + "\n原始用户问题: " + originalUserMessage;
        try {
            String content = chatClientBuilder.build().prompt()
                    .system(systemPrompt())
                    .user("""
                            groupId: %d
                            topK: %d
                            conversationId: %s
                            traceId: %s
                            用户问题: %s
                            """.formatted(ctx.getGroupId(), ctx.getTopK(), ctx.getConversationId(), ctx.getTraceId(), message))
                    .tools(knowledgeBaseSearchTool)
                    .call()
                    .content();
            result.setContent(content);
            boolean escalate = content.contains("转人工") || content.contains("工单");
            result.setRequiresEscalation(escalate);
            if (escalate) {
                result.setEscalationReasonCode("WORKER_KEYWORD");
            }
        } catch (Exception e) {
            result.setContent("子任务处理失败: " + e.getMessage());
            result.setRequiresEscalation(true);
            result.setEscalationReasonCode("WORKER_ERROR");
        }
        result.setCostMs(System.currentTimeMillis() - start);
        return result;
    }
}
