package com.dong.dongrag.assistant.dialogue;

import com.dong.dongrag.assistant.runtime.AgentTemplateId;

public interface GuidanceService {

    GuidanceResult build(AgentTemplateId templateId, ComplaintSlots slots, IntentRoutingResult intent);
}
