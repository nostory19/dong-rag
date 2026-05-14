package com.dong.dongrag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dongrag.assistant")
public class AssistantProperties {

    /**
     * 当规则置信不足时是否调用 LLM 做意图分类（增加一次模型调用）。
     */
    private boolean intentLlmEnabled = false;

    /**
     * 上下文滑窗：最多取近期消息条数（双向合计，含 user/assistant）。
     */
    private int contextMessageLimit = 24;

    /**
     * 拼接后上下文块最大字符数（硬截断尾部）。
     */
    private int contextMaxChars = 12000;

    /**
     * 近期对话总字符超过该阈值时触发异步摘要压缩。
     */
    private int compressThresholdChars = 8000;
}
