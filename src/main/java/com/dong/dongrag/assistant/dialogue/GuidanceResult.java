package com.dong.dongrag.assistant.dialogue;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GuidanceResult {

    List<String> questions;

    List<String> missingSlots;

    String slotSummaryLine;

    public static GuidanceResult empty() {
        return GuidanceResult.builder()
                .questions(List.of())
                .missingSlots(List.of())
                .slotSummaryLine("")
                .build();
    }
}
