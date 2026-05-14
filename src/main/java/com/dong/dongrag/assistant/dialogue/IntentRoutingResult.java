package com.dong.dongrag.assistant.dialogue;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class IntentRoutingResult {

    String intent;

    double confidence;

    /**
     * RULE or LLM
     */
    String source;

    boolean needsClarification;

    /**
     * KNOWLEDGE_RAG：偏制度检索；TOOL_HEAVY：偏实时数据/订单物流等（预留工具链）；MIXED_KNOWLEDGE：混合。
     */
    @Builder.Default
    String routeKind = "MIXED_KNOWLEDGE";

    public static IntentRoutingResult rule(String intent, double confidence) {
        return IntentRoutingResult.builder()
                .intent(intent)
                .confidence(confidence)
                .source("RULE")
                .needsClarification(false)
                .build();
    }
}
