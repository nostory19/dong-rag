package com.dong.dongrag.assistant.dialogue;

import com.dong.dongrag.assistant.runtime.AgentTemplateId;
import com.dong.dongrag.config.AssistantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class IntentRoutingServiceImpl implements IntentRoutingService {

    private static final Logger log = LoggerFactory.getLogger(IntentRoutingServiceImpl.class);

    @Resource
    private AssistantProperties assistantProperties;

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public IntentRoutingResult route(String userMessage, AgentTemplateId templateId) {
        if (userMessage == null) {
            return enrichRouteKind("", IntentRoutingResult.rule("UNKNOWN", 0.3));
        }
        String text = userMessage.toLowerCase(Locale.ROOT);
        IntentRoutingResult rule = classifyByRules(text, templateId);
        if (rule.getConfidence() >= 0.85) {
            return enrichRouteKind(userMessage, rule);
        }
        if (assistantProperties.isIntentLlmEnabled()) {
            try {
                return enrichRouteKind(userMessage, classifyByLlm(userMessage, templateId));
            } catch (Exception e) {
                log.warn("LLM intent failed, fallback to rule: {}", e.getMessage());
            }
        }
        return enrichRouteKind(userMessage, rule);
    }

    private IntentRoutingResult enrichRouteKind(String raw, IntentRoutingResult r) {
        return r.toBuilder().routeKind(resolveRouteKind(raw == null ? "" : raw)).build();
    }

    private static String resolveRouteKind(String raw) {
        String t = raw.toLowerCase(Locale.ROOT);
        if (containsAny(t, "订单号", "运单", "物流单号", "单号查询", "查库存", "sku")) {
            return "TOOL_HEAVY";
        }
        if (containsAny(t, "仅查制度", "只看规定", "不要工具")) {
            return "KNOWLEDGE_RAG";
        }
        return "MIXED_KNOWLEDGE";
    }

    private IntentRoutingResult classifyByRules(String lower, AgentTemplateId templateId) {
        if (containsAny(lower, "律师", "起诉", "法院", "维权", "曝光", "监管")) {
            return IntentRoutingResult.rule("LEGAL_RISK", 0.95);
        }
        if (containsAny(lower, "物流", "发货", "快递", "配送", "延误")) {
            return IntentRoutingResult.rule("LOGISTICS", 0.88);
        }
        if (containsAny(lower, "退款", "退货", "补偿", "发票")) {
            return IntentRoutingResult.rule("REFUND_BILLING", 0.86);
        }
        if (containsAny(lower, "质量", "坏了", "故障", "闪退", "无法使用")) {
            return IntentRoutingResult.rule("QUALITY_PRODUCT", 0.84);
        }
        if (templateId == AgentTemplateId.COMPLAINT_MULTI_LEGACY && containsAny(lower, "投诉", "不满", "差评")) {
            return IntentRoutingResult.rule("COMPLAINT_GENERAL", 0.82);
        }
        return IntentRoutingResult.rule("GENERAL", 0.55);
    }

    private static boolean containsAny(String hay, String... needles) {
        for (String n : needles) {
            if (hay.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private IntentRoutingResult classifyByLlm(String userMessage, AgentTemplateId templateId) throws Exception {
        String json = chatClientBuilder.build().prompt()
                .system("""
                        你是意图分类器。只输出 JSON，不要其他文本：
                        {"intent":"GENERAL|LOGISTICS|REFUND_BILLING|QUALITY_PRODUCT|LEGAL_RISK|COMPLAINT_GENERAL","confidence":0.0-1.0,"needsClarification":false}
                        """)
                .user("模板: " + templateId.name() + "\n用户输入:\n" + userMessage)
                .call()
                .content();
        String cleaned = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        JsonNode node = objectMapper.readTree(cleaned);
        String intent = node.path("intent").asText("GENERAL");
        double conf = node.path("confidence").asDouble(0.6);
        boolean clarify = node.path("needsClarification").asBoolean(false);
        return IntentRoutingResult.builder()
                .intent(intent)
                .confidence(conf)
                .source("LLM")
                .needsClarification(clarify)
                .build();
    }
}
