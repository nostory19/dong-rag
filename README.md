# Dong RAG

基于 **Spring Boot 3 + Spring AI / Spring AI Alibaba** 的企业知识库后端：组级隔离、**异步入库**（MinIO → 解析 → PgVector + Elasticsearch）、**混合检索（RRF + 可选 LLM 重排）**、**RAG 问答**与**多专家知识助手**（NDJSON 流式）。用户端与管理端为独立 Vue 3 工程。

**完整设计与实现说明**已拆分到 **[`docs/`](docs/README.md)**，下文为**讲解大纲 + 速查**；阅读详细文档请从 [docs/README.md](docs/README.md) 进入。

---

## 讲解大纲（按学习 / 交付顺序）

| 步骤 | 主题 | 文档 |
|:----:|------|------|
| 1 | 项目定位、能力边界、与前后端关系 | [docs/01-project-overview.md](docs/01-project-overview.md) |
| 2 | 技术栈、后端包结构、关键类索引 | [docs/02-tech-stack-and-structure.md](docs/02-tech-stack-and-structure.md) |
| 3 | Sa-Token 认证、角色、接口保护 | [docs/03-authentication-and-authorization.md](docs/03-authentication-and-authorization.md) |
| 4 | 组（Group）模型与数据隔离 | [docs/04-group-and-data-isolation.md](docs/04-group-and-data-isolation.md) |
| 5 | 文档入库流水线、状态机、运维接口 | [docs/05-document-ingestion-pipeline.md](docs/05-document-ingestion-pipeline.md) |
| 6 | 混合检索、证据门控、RAG 问答、缓存与熔断指标 | [docs/06-hybrid-retrieval-and-rag-qa.md](docs/06-hybrid-retrieval-and-rag-qa.md) |
| 7 | 知识助手多专家编排（Planner / Worker / Policy） | [docs/07-knowledge-assistant-multi-agent.md](docs/07-knowledge-assistant-multi-agent.md) |
| 8 | 会话表、意图、引导、上下文与压缩 | [docs/08-assistant-session-dialogue.md](docs/08-assistant-session-dialogue.md) |
| 9 | MinIO、PgVector、Elasticsearch | [docs/09-storage-vectors-and-elasticsearch.md](docs/09-storage-vectors-and-elasticsearch.md) |
| 10 | Actuator、Prometheus、Resilience4j、配置项、ES 健康检查说明 | [docs/10-observability-resilience-and-config.md](docs/10-observability-resilience-and-config.md) |
| 11 | 检索检测、编排评测 | [docs/11-retrieval-detection-and-eval.md](docs/11-retrieval-detection-and-eval.md) |
| 12 | Flyway、表演进、常见问题 | [docs/12-database-flyway-and-troubleshooting.md](docs/12-database-flyway-and-troubleshooting.md) |
| 13 | 用户端 Vue（路由、助手流式） | [docs/13-frontend-user-app.md](docs/13-frontend-user-app.md) |
| 14 | 管理端 Vue（任务、检测、评测） | [docs/14-admin-frontend.md](docs/14-admin-frontend.md) |

---

## 仓库结构

```
dong-rag/
├── src/main/java/com/dong/dongrag/   # 后端主代码
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── db/migration/                 # Flyway
│   └── prompts/                     # 外置 Prompt（如 rag-qa-system.txt）
├── docs/                            # 专题文档（本 README 的详细展开）
├── frontend/                        # 用户端 Vue
└── admin-frontend/                  # 管理端 Vue
```

---

## 技术栈（摘要）

| 类别 | 选型 |
|------|------|
| 运行时 | JDK 21，Spring Boot 3.5.x |
| AI | Spring AI 1.1.x，Spring AI Alibaba（DashScope），可选 Ollama/OpenAI starter |
| 数据 | PostgreSQL + Flyway + MyBatis-Plus |
| 向量 | PgVector（`vector_store`） |
| 全文 | Elasticsearch（`rag_chunk_index`） |
| 对象存储 | MinIO |
| 鉴权 | Sa-Token + Redis |
| 解析 | Apache Tika |
| API 文档 | Springdoc OpenAPI |
| 观测 | Actuator、Micrometer Prometheus、Resilience4j |

---

## 快速启动

1. 准备：PostgreSQL（**pgvector**）、Redis、Elasticsearch（推荐 **IK**）、MinIO。  
2. 配置：复制并修改 [`src/main/resources/application-dev.yml`](src/main/resources/application-dev.yml)（库、Redis、ES、MinIO、DashScope Key）。  
3. 环境变量：`BAILIAN_API_KEY`（或与 yml 中 `${...}` 一致）。  
4. 启动：

```bash
mvn spring-boot:run
```

- Swagger：[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)  
- OpenAPI：[http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

用户端 / 管理端：

```bash
cd frontend && npm install && npm run dev
cd admin-frontend && npm install && npm run dev
```

---

## HTTP API 速查

**约定**：以下路径在实际部署时常以 **`/api` 前缀**经网关或前端代理访问（以各 `vite.config` / Nginx 为准）。

### 用户与组

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/user/register` | 注册 |
| POST | `/user/login` | 登录 |
| POST | `/user/logout` | 登出 |
| GET | `/user/list` | 用户列表（**admin**） |
| POST | `/group/create` | 建组 |
| POST | `/group/join` | 入组 |
| GET | `/group/my/list` | 我的组 |

### RAG 与入库

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/rag/ingest/text` | 文本入库 |
| POST | `/rag/ingest/file` | 文件入库 |
| GET | `/rag/ingest/task/{jobId}` | 任务状态 |
| POST | `/rag/qa/ask` | 单次混合检索 + 生成 |
| GET | `/rag/ingest/jobs` | 任务列表（**admin**） |
| GET | `/rag/ingest/jobs/{jobId}` | 任务详情（**admin**） |
| POST | `/rag/ingest/jobs/{jobId}/retry` | 重试（**admin**） |
| POST | `/rag/ingest/documents/{documentId}/rebuild` | 重建索引（**admin**） |
| GET | `/rag/ingest/metrics` | 入库指标（**admin**） |
| POST | `/rag/detect/retrieval` | 检索检测（**admin**，JSON body） |

### 知识助手

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/assistant/chat` | NDJSON 流式；`templateId`、`conversationId` 可选 |
| POST | `/assistant/eval/complaint` | 编排评测（**admin**） |

**助手模板**：默认将 `INTERNAL_KB_SIMPLE` 归一为 **`INTERNAL_KB_MULTI`**；显式 **`COMPLAINT_MULTI_LEGACY`** 走投诉编排。详见 [docs/07-knowledge-assistant-multi-agent.md](docs/07-knowledge-assistant-multi-agent.md)。

---

## NDJSON 事件（助手，速查）

| `event` | 含义 |
|---------|------|
| `start` | `traceId`、`conversationId`、`template` |
| `intent` | 意图 + `routeKind`（`KNOWLEDGE_RAG` / `TOOL_HEAVY` / `MIXED_KNOWLEDGE`） |
| `guide` | 引导问句、缺槽列表 |
| `route-plan` / `worker-*` / `policy-hit` | 编排与策略 |
| `token` / `actions` / `tool-log*` | 答复与工具轨迹 |
| `done` / `error` | 结束 |

完整表与序列图见 [docs/07-knowledge-assistant-multi-agent.md](docs/07-knowledge-assistant-multi-agent.md) 与 [docs/08-assistant-session-dialogue.md](docs/08-assistant-session-dialogue.md)。

---

## 配置与环境变量（速查）

| 位置 | 内容 |
|------|------|
| [`application-dev.yml`](src/main/resources/application-dev.yml) | 数据源、Redis、Flyway、DashScope、PgVector、ES、MinIO、Sa-Token、`dongrag.assistant.*`、`dongrag.ai.*`、`management.*`、`resilience4j.*`、入库分块参数等 |
| 环境变量 | **`BAILIAN_API_KEY`**（与 DashScope 配置引用一致） |

`dongrag.ai` 与 Actuator、熔断、重排、问答缓存的说明见 [docs/10-observability-resilience-and-config.md](docs/10-observability-resilience-and-config.md)。

---

## Flyway 版本（速查）

| 文件 | 说明 |
|------|------|
| `v1` ~ `v4` | 核心表、用户字段、入库增强、补列 |
| `v5` | 向量维度对齐 |
| `v6` | 助手会话与消息 |

详情与排障：[docs/12-database-flyway-and-troubleshooting.md](docs/12-database-flyway-and-troubleshooting.md)。

---

## 后续可增强（路线图摘要）

- 入库调度独立 Worker / 消息队列  
- 更强解析与语义切分  
- 全链路追踪（Tracing）  
- 自动化集成测试（上传 → 入库 → 检索 → 问答 → 助手）

更细的讨论点已分散在各 `docs/*.md` 正文中。

---

**再次提示**：若需「按模块讲清楚实现方式」，请以 **[docs/README.md](docs/README.md)** 为目录逐篇阅读，避免与根 README 重复维护两处长文。
