package com.dong.dongrag.assistant.runtime;

import com.dong.dongrag.assistant.service.GroupAgentConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
public class AgentRunContextFactory {

    @Resource
    private WorkerRegistry workerRegistry;

    @Resource
    private GroupAgentConfigService groupAgentConfigService;

    public AgentRunContext build(Long userId, Long groupId, int topK, String conversationId, String traceId,
                                 AgentTemplateId templateId) {
        String conv = conversationId != null && !conversationId.isBlank()
                ? conversationId : UUID.randomUUID().toString();
        String tid = traceId != null && !traceId.isBlank()
                ? traceId : UUID.randomUUID().toString();
        Set<String> allowed = resolveAllowed(groupId, templateId);
        return AgentRunContext.builder()
                .userId(userId)
                .groupId(groupId)
                .conversationId(conv)
                .traceId(tid)
                .topK(topK)
                .templateId(templateId)
                .allowedWorkerTypes(allowed)
                .build();
    }

    private Set<String> resolveAllowed(Long groupId, AgentTemplateId templateId) {
        Set<String> registered = workerRegistry.registeredTypes();
        Set<String> base = new LinkedHashSet<>(templateId.defaultAllowedWorkerTypes());
        Set<String> override = groupAgentConfigService.overrideAllowedWorkers(groupId, templateId);
        Set<String> effective = override != null ? new LinkedHashSet<>(override) : new LinkedHashSet<>(base);
        effective.retainAll(registered);
        if (effective.isEmpty()) {
            effective.addAll(base);
            effective.retainAll(registered);
        }
        if (effective.isEmpty() && registered.contains("GENERAL_KB")) {
            effective.add("GENERAL_KB");
        }
        if (effective.isEmpty() && !registered.isEmpty()) {
            effective.add(registered.iterator().next());
        }
        return Set.copyOf(effective);
    }
}
