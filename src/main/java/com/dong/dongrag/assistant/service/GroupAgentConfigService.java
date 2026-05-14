package com.dong.dongrag.assistant.service;

import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Optional per-group overrides for allowed worker types. Extend with persistence later.
 */
@Service
public class GroupAgentConfigService {

    /**
     * @return override set, or {@code null} to use {@link AgentTemplateId#defaultAllowedWorkerTypes()}
     */
    public Set<String> overrideAllowedWorkers(Long groupId, AgentTemplateId templateId) {
        return null;
    }
}
