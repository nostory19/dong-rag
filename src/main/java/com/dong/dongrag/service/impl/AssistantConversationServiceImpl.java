package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.mapper.AssistantConversationMapper;
import com.dong.dongrag.mapper.AssistantMessageMapper;
import com.dong.dongrag.model.entity.AssistantConversation;
import com.dong.dongrag.model.entity.AssistantMessage;
import com.dong.dongrag.service.AssistantConversationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class AssistantConversationServiceImpl implements AssistantConversationService {

    @Resource
    private AssistantConversationMapper conversationMapper;

    @Resource
    private AssistantMessageMapper messageMapper;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public Long ensureConversation(Long userId, Long groupId, AgentTemplateId templateId, String clientConversationId) {
        if (StrUtil.isNotBlank(clientConversationId)) {
            Long id;
            try {
                id = Long.parseLong(clientConversationId.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "conversationId 格式无效");
            }
            AssistantConversation existing = conversationMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
            }
            if (!userId.equals(existing.getUserId()) || !groupId.equals(existing.getGroupId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "无权访问该会话");
            }
            return id;
        }
        AssistantConversation row = new AssistantConversation();
        row.setUserId(userId);
        row.setGroupId(groupId);
        row.setTemplateId(templateId.name());
        row.setStatus("ACTIVE");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(row);
        return row.getId();
    }

    @Override
    public AssistantConversation getById(Long conversationId) {
        return conversationMapper.selectById(conversationId);
    }

    @Override
    public void appendMessage(Long conversationId, String role, String content, String intent, String traceId,
                              Map<String, Object> metadata) {
        AssistantMessage m = new AssistantMessage();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        m.setIntent(intent);
        m.setTraceId(traceId);
        if (metadata != null && !metadata.isEmpty()) {
            try {
                m.setMetadataJson(objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException e) {
                m.setMetadataJson("{}");
            }
        }
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
        touchUpdatedAt(conversationId);
    }

    @Override
    public List<AssistantMessage> listRecentMessagesAsc(Long conversationId, int limit) {
        QueryWrapper<AssistantMessage> q = new QueryWrapper<>();
        q.eq("conversation_id", conversationId).orderByDesc("created_at").last("limit " + limit);
        List<AssistantMessage> desc = messageMapper.selectList(q);
        List<AssistantMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    @Override
    public void updateRollingSummary(Long conversationId, String summary) {
        AssistantConversation patch = new AssistantConversation();
        patch.setId(conversationId);
        patch.setRollingSummary(summary);
        patch.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(patch);
    }

    @Override
    public void updateSlotStateJson(Long conversationId, String slotStateJson) {
        AssistantConversation patch = new AssistantConversation();
        patch.setId(conversationId);
        patch.setSlotStateJson(slotStateJson);
        patch.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(patch);
    }

    @Override
    public void touchUpdatedAt(Long conversationId) {
        conversationMapper.update(null, new UpdateWrapper<AssistantConversation>()
                .eq("id", conversationId)
                .set("updated_at", LocalDateTime.now()));
    }

    @Override
    public void updateLastCompressedAt(Long conversationId) {
        conversationMapper.update(null, new UpdateWrapper<AssistantConversation>()
                .eq("id", conversationId)
                .set("last_compressed_at", LocalDateTime.now())
                .set("updated_at", LocalDateTime.now()));
    }
}
