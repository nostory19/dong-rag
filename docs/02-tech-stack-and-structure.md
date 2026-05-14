# 02 技术栈与代码结构

## 2.1 技术栈一览

| 类别 | 技术选型 | 说明 |
|------|----------|------|
| 运行时 | JDK 21、Spring Boot 3.5.x | 虚拟线程等用于助手异步执行 |
| AI | Spring AI 1.1.x、Spring AI Alibaba（DashScope） | Chat、Embedding；可配合 Ollama/OpenAI starter |
| 关系库 | PostgreSQL + Flyway + MyBatis-Plus | 业务数据与迁移 |
| 向量 | PgVector（`spring-ai-starter-vector-store-pgvector`） | 表名默认 `vector_store` |
| 全文 | Spring Data Elasticsearch | 索引 `rag_chunk_index`，建议 IK 分词 |
| 对象存储 | MinIO | 原始文件 |
| 会话与鉴权 | Sa-Token + Redis | Token 走 Header |
| 文档解析 | Apache Tika | 非纯文本格式 |
| API 文档 | Springdoc OpenAPI | `/swagger-ui.html`、`/v3/api-docs` |
| 弹性与观测 | Resilience4j、Micrometer、Actuator | 熔断与 Prometheus 指标 |

## 2.2 后端包结构（`com.dong.dongrag`）

| 包 / 目录 | 职责 |
|-----------|------|
| `controller` | `UserController`、`GroupController`、`RagController`、`AssistantController` |
| `service` / `service.impl` | 业务接口与实现：用户、组、入库、混合检索、RAG 问答、助手、会话、任务、检测等 |
| `mapper` | MyBatis-Plus Mapper |
| `model.entity` / `dto` / `vo` | 表实体、入参、出参 |
| `model.es` | ES 文档模型，如 `RagChunkIndex` |
| `repository` | ES Repository，如 `RagChunkIndexRepository` |
| `assistant.*` | 多专家运行时：`runtime`、`agent`、`dialogue`、`policy`、`tool`、`orchestrator`、`service` |
| `rag` | 检索侧扩展，如 `EvidenceReranker` |
| `config` | Sa-Token、MinIO、AssistantProperties、`DongragAiProperties`、`ChatDefaultsConfig` 等 |
| `support` | 横切工具，如 `PromptResourceLoader` |
| `exception` / `common` | 全局异常、统一响应、分页等 |

## 2.3 关键类索引（便于代码跳转）

| 主题 | 主要类 |
|------|--------|
| 入库 | `RagIngestionServiceImpl`、`IngestionJobService` |
| 混合检索 | `HybridRetrievalService`、`HybridRetrievalServiceImpl` |
| RAG 问答 | `RagQaServiceImpl` |
| 助手主流程 | `AssistantServiceImpl` |
| 编排 | `MultiAgentOrchestratorService`、`ResponseAggregator` |
| Worker 基类 | `KbToolDomainWorker`、`KnowledgeBaseSearchTool` |
| 会话 | `AssistantConversationServiceImpl`、`ContextBuilder`、`ConversationCompressor` |
| 意图与引导 | `IntentRoutingServiceImpl`、`GuidanceServiceImpl`、`ComplaintSlots` |
| 知识版本指纹 | `GroupKnowledgeRevisionServiceImpl`（缓存键等） |
| 检索检测 | `RetrievalDetectionServiceImpl` |
| 编排评测 | `ComplaintEvaluationService` |

上一篇：[01-project-overview.md](01-project-overview.md)  
下一篇：[03-authentication-and-authorization.md](03-authentication-and-authorization.md)
