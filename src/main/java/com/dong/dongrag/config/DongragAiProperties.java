package com.dong.dongrag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全局 Chat 默认参数（经 {@link com.dong.dongrag.config.ChatDefaultsConfig} 注入到 {@link org.springframework.ai.chat.client.ChatClient.Builder}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dongrag.ai")
public class DongragAiProperties {

    /**
     * 问答类任务默认温度，建议偏低以减少漂移。
     */
    private double chatTemperature = 0.2d;

    /**
     * 单次生成最大 token 上限（具体生效依赖底层模型）。
     */
    private int chatMaxTokens = 2048;

    /**
     * 是否对混合检索包一层熔断（失败率过高时快速失败）。
     */
    private boolean retrievalCircuitBreakerEnabled = true;

    /**
     * 是否对 RAG 问答中的 LLM 调用包一层熔断。
     */
    private boolean llmCircuitBreakerEnabled = true;

    /**
     * 是否启用检索后 LLM 重排（会增加一次模型调用）。
     */
    private boolean retrievalRerankEnabled = false;

    /**
     * 重排时送入模型的最大片段数。
     */
    private int rerankCandidateLimit = 12;

    /**
     * /rag/qa 答案缓存 TTL（秒），0 表示关闭。
     */
    private int qaAnswerCacheTtlSeconds = 0;
}
