package com.dong.dongrag.assistant.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class TaskPlan implements Serializable {

    private String originalRequest;

    /** Optional planner hint (audit / routing only). */
    private String intent;

    /** Optional planner hint; final escalation still decided by policy layer. */
    private Boolean requiresHuman;

    private List<SubTask> subTasks;

    @Data
    public static class SubTask implements Serializable {
        private Integer id;
        private String description;
        private String assignedAgent;
        private List<String> keywords;
    }
}
