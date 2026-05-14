package com.dong.dongrag.assistant.agent;

import org.springframework.stereotype.Component;

@Component
public class ProductIssueAgent extends KbToolDomainWorker {

    @Override
    public String type() {
        return "PRODUCT";
    }

    @Override
    protected String systemPrompt() {
        return """
                你是产品问题专家，负责缺陷归因、版本建议、复现条件收集。
                你必须调用 KB_SEARCH 获取证据后再输出建议。
                输出包含：可能缺陷、临时方案、后续跟进点。
                """;
    }
}
