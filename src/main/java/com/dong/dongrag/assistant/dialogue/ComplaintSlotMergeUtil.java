package com.dong.dongrag.assistant.dialogue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComplaintSlotMergeUtil {

    private static final Pattern ORDER = Pattern.compile("(?:订单|单号)[号\\s:：]*([A-Za-z0-9\\-]{6,32})");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");

    private ComplaintSlotMergeUtil() {
    }

    public static void mergeFromUserText(ComplaintSlots slots, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (isBlank(slots.getOrderId())) {
            Matcher m = ORDER.matcher(text);
            if (m.find()) {
                slots.setOrderId(m.group(1));
            }
        }
        if (isBlank(slots.getContactPhone())) {
            Matcher m2 = PHONE.matcher(text);
            if (m2.find()) {
                slots.setContactPhone(m2.group(1));
            }
        }
        if (isBlank(slots.getChannel())) {
            if (text.contains("天猫") || text.contains("淘宝")) {
                slots.setChannel("天猫/淘宝");
            } else if (text.contains("京东")) {
                slots.setChannel("京东");
            } else if (text.contains("抖音")) {
                slots.setChannel("抖音");
            } else if (text.contains("线下") || text.contains("门店")) {
                slots.setChannel("线下门店");
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
