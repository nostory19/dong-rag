package com.dong.dongrag.assistant.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class WorkerResult implements Serializable {

    private Integer subTaskId;

    private String agentType;

    private String content;

    private boolean requiresEscalation;

    /**
     * Machine-readable reason when {@link #requiresEscalation} is true, e.g. WORKER_KEYWORD, AGENT_MISSING, WORKER_ERROR.
     */
    private String escalationReasonCode;

    private long costMs;
}
