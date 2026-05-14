package com.dong.dongrag.assistant.dialogue;

import cn.hutool.core.util.StrUtil;
import com.dong.dongrag.config.AssistantProperties;
import com.dong.dongrag.model.entity.AssistantConversation;
import com.dong.dongrag.model.entity.AssistantMessage;
import com.dong.dongrag.service.AssistantConversationService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextBuilder {

    @Resource
    private AssistantConversationService assistantConversationService;

    @Resource
    private AssistantProperties assistantProperties;

    public String buildContextBlock(Long conversationId, AssistantConversation conversation) {
        StringBuilder sb = new StringBuilder();
        if (conversation != null && StrUtil.isNotBlank(conversation.getRollingSummary())) {
            sb.append("【对话摘要】\n").append(conversation.getRollingSummary().trim()).append("\n\n");
        }
        sb.append("【近期对话】\n");
        List<AssistantMessage> messages = assistantConversationService.listRecentMessagesAsc(
                conversationId, assistantProperties.getContextMessageLimit());
        for (AssistantMessage m : messages) {
            sb.append(m.getRole()).append(": ").append(m.getContent()).append("\n");
        }
        String block = sb.toString();
        int max = assistantProperties.getContextMaxChars();
        if (block.length() > max) {
            return block.substring(block.length() - max);
        }
        return block;
    }
}
