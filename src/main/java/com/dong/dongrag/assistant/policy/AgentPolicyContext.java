package com.dong.dongrag.assistant.policy;

import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AgentPolicyContext {

    String userMessage;

    ComplaintProcessResult processResult;

    AgentTemplateId templateId;
}
