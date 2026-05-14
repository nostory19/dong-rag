package com.dong.dongrag.assistant.runtime;

import java.util.Set;

/**
 * Assistant routing templates: simple RAG vs multi-agent orchestration profiles.
 */
public enum AgentTemplateId {

    INTERNAL_KB_SIMPLE,
    INTERNAL_KB_MULTI,
    COMPLAINT_MULTI_LEGACY;

    public static AgentTemplateId fromCode(String code) {
        if (code == null || code.isBlank()) {
            return INTERNAL_KB_SIMPLE;
        }
        try {
            return AgentTemplateId.valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            return INTERNAL_KB_SIMPLE;
        }
    }

    /**
     * Default allowed worker {@code type()} values per template (Planner must stay within this set).
     */
    public Set<String> defaultAllowedWorkerTypes() {
        return switch (this) {
            case INTERNAL_KB_MULTI -> Set.of("GENERAL_KB", "TECH_SUPPORT", "PRODUCT", "AFTER_SALES");
            case COMPLAINT_MULTI_LEGACY -> Set.of("TECH_SUPPORT", "PRODUCT", "AFTER_SALES");
            case INTERNAL_KB_SIMPLE -> Set.of();
        };
    }
}
