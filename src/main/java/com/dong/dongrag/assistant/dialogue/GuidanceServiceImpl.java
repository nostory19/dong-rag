package com.dong.dongrag.assistant.dialogue;

import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GuidanceServiceImpl implements GuidanceService {

    @Override
    public GuidanceResult build(AgentTemplateId templateId, ComplaintSlots slots, IntentRoutingResult intent) {
        if (templateId != AgentTemplateId.COMPLAINT_MULTI_LEGACY) {
            return GuidanceResult.empty();
        }
        List<String> missing = slots.missingRequiredForComplaint();
        if (missing.isEmpty()) {
            return GuidanceResult.builder()
                    .questions(List.of())
                    .missingSlots(List.of())
                    .slotSummaryLine(buildSlotSummary(slots))
                    .build();
        }
        List<String> questions = new ArrayList<>();
        if (missing.contains("orderId")) {
            questions.add("请提供相关订单号或购买凭证编号，便于核对。");
        }
        if (missing.contains("channel")) {
            questions.add("请说明购买渠道（如天猫、京东、线下门店等）。");
        }
        return GuidanceResult.builder()
                .questions(questions.size() > 2 ? questions.subList(0, 2) : questions)
                .missingSlots(missing)
                .slotSummaryLine(buildSlotSummary(slots))
                .build();
    }

    private static String buildSlotSummary(ComplaintSlots slots) {
        StringBuilder sb = new StringBuilder();
        if (notBlank(slots.getOrderId())) {
            sb.append("已知订单号: ").append(slots.getOrderId()).append("；");
        }
        if (notBlank(slots.getChannel())) {
            sb.append("渠道: ").append(slots.getChannel()).append("；");
        }
        if (notBlank(slots.getSkuOrProduct())) {
            sb.append("商品: ").append(slots.getSkuOrProduct()).append("；");
        }
        if (notBlank(slots.getContactPhone())) {
            sb.append("联系电话已提供；");
        }
        if (sb.isEmpty()) {
            return "";
        }
        return "【已知槽位】" + sb;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
