package com.dong.dongrag.model.dto.assistant;

import lombok.Data;

import java.io.Serializable;

@Data
public class AssistantChatRequest implements Serializable {

    private Long groupId;

    private String message;

    private Integer topK = 5;

    private static final long serialVersionUID = 1L;
}
