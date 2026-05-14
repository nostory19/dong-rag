package com.dong.dongrag.assistant.dialogue;

import com.dong.dongrag.config.AssistantProperties;
import com.dong.dongrag.model.entity.AssistantMessage;
import com.dong.dongrag.service.AssistantConversationService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ConversationCompressor {

    private static final Logger log = LoggerFactory.getLogger(ConversationCompressor.class);

    @Resource
    private AssistantConversationService assistantConversationService;

    @Resource
    private AssistantProperties assistantProperties;

    @Resource
    private ChatClient.Builder chatClientBuilder;

    public void maybeCompressAsync(Long conversationId) {
        Thread.startVirtualThread(() -> {
            try {
                List<AssistantMessage> recent = assistantConversationService.listRecentMessagesAsc(conversationId, 80);
                int totalChars = recent.stream().mapToInt(m -> m.getContent() == null ? 0 : m.getContent().length()).sum();
                if (totalChars < assistantProperties.getCompressThresholdChars()) {
                    return;
                }
                String transcript = recent.stream()
                        .map(m -> m.getRole() + ": " + m.getContent())
                        .collect(Collectors.joining("\n"));
                String prior = "";
                var conv = assistantConversationService.getById(conversationId);
                if (conv != null && conv.getRollingSummary() != null) {
                    prior = "旧摘要:\n" + conv.getRollingSummary() + "\n\n";
                }
                String summary = chatClientBuilder.build().prompt()
                        .system("""
                                将下列客服/知识库对话压缩为结构化短摘要（中文），保留：用户核心诉求、已确认事实、未决问题、风险点。
                                只输出摘要正文，不要 JSON。
                                长度控制在 800 字以内。
                                """)
                        .user(prior + "对话记录:\n" + transcript)
                        .call()
                        .content();
                assistantConversationService.updateRollingSummary(conversationId, summary);
                assistantConversationService.updateLastCompressedAt(conversationId);
                log.info("Conversation compressed, conversationId={}, approxChars={}", conversationId, totalChars);
            } catch (Exception e) {
                log.warn("Conversation compress failed, conversationId={}: {}", conversationId, e.getMessage());
            }
        });
    }
}
