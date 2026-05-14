package com.dong.dongrag.model.dto.assistant;

import lombok.Data;

import java.io.Serializable;

@Data
public class AssistantChatRequest implements Serializable {

    private Long groupId;

    private String message;

    private Integer topK = 5;

    /**
     * {@link com.dong.dongrag.assistant.runtime.AgentTemplateId} name。
     * 知识助手固定多专家：{@code INTERNAL_KB_SIMPLE} 或未传时由服务端视为 {@code INTERNAL_KB_MULTI}；
     * 仅当需要投诉编排评测时可传 {@code COMPLAINT_MULTI_LEGACY}。
     */
    private String templateId;

    /** Client-supplied session id for multi-turn (optional). */
    private String conversationId;

    private static final long serialVersionUID = 1L;
}
