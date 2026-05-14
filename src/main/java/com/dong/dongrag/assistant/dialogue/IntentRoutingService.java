package com.dong.dongrag.assistant.dialogue;

import com.dong.dongrag.assistant.runtime.AgentTemplateId;

public interface IntentRoutingService {

    IntentRoutingResult route(String userMessage, AgentTemplateId templateId);
}
