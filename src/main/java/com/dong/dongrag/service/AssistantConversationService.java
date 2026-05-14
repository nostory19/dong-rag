package com.dong.dongrag.service;

import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import com.dong.dongrag.model.entity.AssistantConversation;
import com.dong.dongrag.model.entity.AssistantMessage;

import java.util.List;
import java.util.Map;

public interface AssistantConversationService {

    Long ensureConversation(Long userId, Long groupId, AgentTemplateId templateId, String clientConversationId);

    AssistantConversation getById(Long conversationId);

    void appendMessage(Long conversationId, String role, String content, String intent, String traceId, Map<String, Object> metadata);

    List<AssistantMessage> listRecentMessagesAsc(Long conversationId, int limit);

    void updateRollingSummary(Long conversationId, String summary);

    void updateSlotStateJson(Long conversationId, String slotStateJson);

    void touchUpdatedAt(Long conversationId);

    void updateLastCompressedAt(Long conversationId);
}
