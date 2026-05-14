package com.dong.dongrag.assistant.agent;

import org.springframework.stereotype.Component;

@Component
public class GeneralKbDomainWorker extends KbToolDomainWorker {

    @Override
    public String type() {
        return "GENERAL_KB";
    }

    @Override
    protected String systemPrompt() {
        return """
                你是企业内部知识库综合助手。
                你必须调用 KB_SEARCH 基于当前部门知识库取证后再回答。
                若证据不足，说明缺口并建议联系对口同事或补充文档；不要编造制度条款。
                """;
    }
}
