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

## 实现思路与技术要点

- **Actuator 暴露面**：生产仅开放必要端点（`health`、`prometheus` 等），敏感端点需网络层或认证保护；`info` 用于版本/构建信息展示。
- **ES 健康检查与业务分离**：Spring Boot 自带 ES health 与业务客户端版本可能不一致导致反序列化失败；关闭 health **不等于**关闭检索，避免运维误判「整个应用不可用」。
- **Resilience4j 分实例**：`retrieval` 与 `llm` 分离，防止检索雪崩拖死生成或相反；可通过配置快速降级（关闭熔断或调阈值）做现场排障。
- **Micrometer 命名**：`dongrag.*` 前缀自定义指标与框架默认指标区分，便于 Grafana 面板聚合；关键路径用 Timer，缓存用 Counter 区分 hit/miss。
- **配置绑定**：`DongragAiProperties`、`AssistantProperties` 与 `ChatDefaultsConfig` 集中管理 AI 行为，避免在 Service 内散落温度、topK、阈值；新增开关优先走配置类而非环境分支硬编码。

上一篇：[09-storage-vectors-and-elasticsearch.md](09-storage-vectors-and-elasticsearch.md)  
下一篇：[11-retrieval-detection-and-eval.md](11-retrieval-detection-and-eval.md)
