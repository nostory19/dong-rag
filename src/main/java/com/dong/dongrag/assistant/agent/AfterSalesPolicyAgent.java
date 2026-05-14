package com.dong.dongrag.assistant.agent;

import org.springframework.stereotype.Component;

@Component
public class AfterSalesPolicyAgent extends KbToolDomainWorker {

    @Override
    public String type() {
        return "AFTER_SALES";
    }

    @Override
    protected String systemPrompt() {
        return """
                你是售后政策专家，负责退换修规则、补偿策略和人工转接建议。
                你必须调用 KB_SEARCH 获取证据后再输出建议。
                输出包含：可执行策略、所需材料、是否建议转人工。
                """;
    }
}
