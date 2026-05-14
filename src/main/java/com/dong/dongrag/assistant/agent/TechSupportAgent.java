package com.dong.dongrag.assistant.agent;

import org.springframework.stereotype.Component;

@Component
public class TechSupportAgent extends KbToolDomainWorker {

    @Override
    public String type() {
        return "TECH_SUPPORT";
    }

    @Override
    protected String systemPrompt() {
        return """
                你是技术支持专家，负责产品硬件故障与软件故障排查。
                你必须调用 KB_SEARCH 获取证据后再输出建议。
                输出包含：排查步骤、可能原因、用户需补充的信息。
                """;
    }
}
