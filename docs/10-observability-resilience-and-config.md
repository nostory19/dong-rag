# 10 观测、弹性与配置

## 10.1 Spring Boot Actuator

- **依赖**：`spring-boot-starter-actuator`。
- **暴露端点**（`application-dev.yml`）：`health`、`info`、`prometheus`、`metrics`。
- **Prometheus**：`micrometer-registry-prometheus`。

## 10.2 Elasticsearch 健康检查

当远端 ES 与客户端 API 版本不一致时，`/actuator/health` 可能出现 `HealthResponse.unassignedPrimaryShards` 等**反序列化失败**。在未能升级集群前，可配置：

```yaml
management:
  health:
    elasticsearch:
      enabled: false
```

业务检索仍使用 `ElasticsearchOperations`，**不受此项影响**。

## 10.3 Resilience4j

- **依赖**：`resilience4j-spring-boot3`。
- **实例**：`retrieval`（混合检索）、`llm`（RAG 问答中的 Chat 调用）。
- **开关**：`dongrag.ai.retrieval-circuit-breaker-enabled`、`dongrag.ai.llm-circuit-breaker-enabled`。

## 10.4 Micrometer 指标（自定义 Timer / Counter）

| 名称 | 说明 |
|------|------|
| `dongrag.retrieval.hybrid` | 混合检索主体（tag `apply_rerank`） |
| `dongrag.retrieval.judgement` | 带门控的检索入口 |
| `dongrag.rag.qa.llm` | RAG 生成阶段 |
| `dongrag.rag.qa.cache` | 问答缓存命中（counter `result=hit`） |
| `dongrag.rag.qa.cache_write` | 写入缓存 |
| `dongrag.assistant.multi_agent` | 助手整轮耗时 |

## 10.5 `dongrag.ai`（全局 AI 与检索治理）

| 配置项 | 含义 |
|--------|------|
| `chat-temperature` | ChatClient 默认温度 |
| `chat-max-tokens` | 默认 max tokens |
| `retrieval-circuit-breaker-enabled` | 检索熔断 |
| `llm-circuit-breaker-enabled` | LLM 熔断 |
| `retrieval-rerank-enabled` | LLM 重排 |
| `rerank-candidate-limit` | 重排候选条数上限 |
| `qa-answer-cache-ttl-seconds` | RAG 答案缓存 TTL，`0` 关闭 |

实现入口：`DongragAiProperties`、`ChatDefaultsConfig`（`ChatClientCustomizer`）。

## 10.6 `dongrag.assistant`（助手对话）

- `intent-llm-enabled`、`context-message-limit`、`context-max-chars`、`compress-threshold-chars`

## 10.7 DashScope / 模型

- `spring.ai.model.chat`、`embedding` 指向 `dashscope`。
- 环境变量 **`BAILIAN_API_KEY`**（或你环境中所用的 key 名）需与 `spring.ai.dashscope.api-key` 对齐。

上一篇：[09-storage-vectors-and-elasticsearch.md](09-storage-vectors-and-elasticsearch.md)  
下一篇：[11-retrieval-detection-and-eval.md](11-retrieval-detection-and-eval.md)
