package com.dong.dongrag.assistant.runtime;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Set;

/**
 * Immutable per-request context for multi-agent runs (auth, tenancy, tracing).
 */
@Value
@Builder
public class AgentRunContext {

    Long userId;

    Long groupId;

    String conversationId;

    String traceId;

    int topK;

    AgentTemplateId templateId;

    /**
     * Intersection of registry workers and policy; Planner output must use only these types.
     */
    Set<String> allowedWorkerTypes;

    public Set<String> allowedWorkerTypesView() {
        return allowedWorkerTypes == null ? Set.of() : Collections.unmodifiableSet(allowedWorkerTypes);
    }
}
