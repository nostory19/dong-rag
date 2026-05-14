package com.dong.dongrag.assistant.dialogue;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 投诉场景槽位（持久化在 assistant_conversations.slot_state_json）。
 */
@Data
public class ComplaintSlots {

    private String orderId;

    private String channel;

    private String skuOrProduct;

    private String incidentTime;

    private String contactPhone;

    public List<String> missingRequiredForComplaint() {
        List<String> missing = new ArrayList<>();
        if (isBlank(orderId)) {
            missing.add("orderId");
        }
        if (isBlank(channel)) {
            missing.add("channel");
        }
        return missing;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
