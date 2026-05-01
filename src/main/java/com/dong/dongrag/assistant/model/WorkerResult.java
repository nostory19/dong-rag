package com.dong.dongrag.assistant.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class WorkerResult implements Serializable {

    private Integer subTaskId;

    private String agentType;

    private String content;

    private boolean requiresEscalation;

    private long costMs;
}
