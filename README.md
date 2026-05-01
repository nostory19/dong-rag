# Dong RAG Backend

一个基于 Spring Boot + Spring AI 的企业知识库与智能问答后端，包含：

- 用户与权限体系（Sa-Token + Redis）
- 组（Group）级别的数据隔离
- 文档异步入库（MinIO -> 解析 -> 切分 -> 向量库/ES）
- 混合检索（向量 + 关键词 + RRF）
- RAG 问答与证据回传
- 管理端任务运维接口
- Assistant 多 Agent 流式交互（`Flux<String>`）

---

## 1. 项目定位与核心能力

该项目用于“可落地、可运维”的 RAG 后端实践，重点不是单次 demo，而是完整的线上链路：

1. 上传文档（文本/文件）
2. 异步任务化入库（避免请求阻塞）
3. 统一状态机与重试
4. 索引一致性验收
5. 查询、问答、管理监控

---

## 2. 技术栈

- **框架**: Spring Boot 3.5.x
- **AI**: Spring AI 1.1.x、Spring AI Alibaba（DashScope）
- **向量库**: PgVector（PostgreSQL）
- **关系数据库**: PostgreSQL + Flyway
- **关键词检索**: Elasticsearch
- **对象存储**: MinIO
- **鉴权授权**: Sa-Token + Redis
- **ORM**: MyBatis-Plus
- **解析**: Apache Tika
- **接口文档**: Springdoc OpenAPI

---

## 3. 后端分层架构

代码主路径：`src/main/java/com/dong/dongrag`

- `controller`
  - `UserController`：注册、登录、登出、用户列表（admin）
  - `GroupController`：建组、入组、我的组
  - `RagController`：文档入库、任务查询、问答、管理接口
  - `AssistantController`：流式对话、投诉评测
- `service` / `service.impl`
  - 业务编排核心：用户、组、入库、检索、问答、助手、任务管理
- `mapper`
  - MyBatis-Plus 数据访问层
- `model`
  - `entity`（表映射）、`dto`（请求）、`vo`（响应）、`es`（ES 索引模型）
- `repository`
  - Elasticsearch Repository（如 `RagChunkIndexRepository`）
- `exception` / `common`
  - 统一响应、错误码、全局异常处理
- `config`
  - Sa-Token、MinIO、TypeHandler 等配置

---

## 4. 数据模型与状态机

### 4.1 关键表

- `users`：用户基础信息、密码、角色
- `groups`：知识组
- `group_memberships`：用户与组关系
- `documents`：文档主记录（文件信息、状态、失败原因、hash）
- `document_chunks`：切片记录（文本、偏移、metadata、索引标记）
- `ingestion_jobs`：入库任务记录（状态、重试、错误、执行时间）
- `vector_store`：PgVector 向量数据表（Spring AI 自动管理）

### 4.2 文档状态（`documents.status`）

- `UPLOADED`：上传完成，等待处理
- `PROCESSING`：任务处理中
- `READY`：处理完成并可检索
- `FAILED`：处理失败

### 4.3 任务状态（`ingestion_jobs.status`）

- `PENDING`：待执行
- `RUNNING`：执行中
- `SUCCESS`：成功
- `RETRY_WAITING`：等待重试
- `FAILED`：最终失败

---

## 5. 文档入库全流程（重点）

核心实现：`RagIngestionServiceImpl`

### Step A: 上传接口快速返回

- 接口：
  - `POST /rag/ingest/text`
  - `POST /rag/ingest/file`
- 行为：
  1. 校验登录与组权限
  2. 原文件存入 MinIO（`storage_bucket` + `storage_object_key`）
  3. 写 `documents`（`UPLOADED`）
  4. 写 `ingestion_jobs`（`PENDING`）
  5. 返回 `documentId + jobId + 状态`

### Step B: 异步执行触发

- 双保险机制：
  - 上传后立刻异步触发一次
  - `@Scheduled` 定时轮询拉取 `PENDING/RETRY_WAITING` 任务

### Step C: 任务执行流水线

1. 标记文档 `PROCESSING`
2. 从 MinIO 读取原文件
3. 按类型解析/清洗（`md/txt` 直读，其他经 Tika）
4. 内容质量校验（空文、超短、乱码比例）
5. 切分 chunk（含 overlap）
6. chunk 入库到 `document_chunks`（含 metadata）
7. 向量化并写入 `vector_store`
8. 写 ES 索引 `rag_chunk_index`
9. 一致性校验（DB / Vector / ES 数量一致）
10. 检索抽样验收（当前文档可召回）
11. 成功标记 `READY + SUCCESS`

失败时：

- 写 `failure_reason`、`last_error`
- 根据重试策略进入 `RETRY_WAITING` 或 `FAILED`

---

## 6. 检索与问答流程

核心实现：

- 检索：`HybridRetrievalServiceImpl`
- 问答：`RagQaServiceImpl`

### 6.1 混合检索

1. 向量检索获取候选
2. ES 关键词检索获取候选
3. 使用 RRF（Reciprocal Rank Fusion）融合排序
4. 邻居窗口扩展（上下 chunk）
5. 输出证据列表与置信度

### 6.2 RAG 问答

接口：`POST /rag/qa/ask`

1. 先做混合检索
2. 若证据不足，返回低置信/拒答
3. 若证据充分，构造上下文调用 LLM 生成答案
4. 返回答案 + 证据 + 置信评分

---

## 7. Assistant 流式对话流程

接口：`POST /assistant/chat`（返回 `Flux<String>`）

流程包含：

1. 接收用户问题
2. 多 Agent 编排（orchestrator + worker）
3. 通过工具调用（如知识检索）获取证据
4. 汇总策略输出最终回复
5. 流式回传事件/文本，便于前端实时展示与排障

---

## 8. API 概览

### 用户与权限

- `POST /user/register`
- `POST /user/login`
- `POST /user/logout`
- `GET /user/list`（admin）

### 组管理

- `POST /group/create`
- `POST /group/join`
- `GET /group/my/list`

### RAG

- `POST /rag/ingest/text`
- `POST /rag/ingest/file`
- `GET /rag/ingest/task/{jobId}`（用户查询任务状态）
- `POST /rag/qa/ask`

### 入库运维（admin）

- `GET /rag/ingest/jobs`
- `GET /rag/ingest/jobs/{jobId}`
- `POST /rag/ingest/jobs/{jobId}/retry`
- `POST /rag/ingest/documents/{documentId}/rebuild`
- `GET /rag/ingest/metrics`

### Assistant

- `POST /assistant/chat`
- `POST /assistant/eval/complaint`（admin）

---

## 9. 配置说明

主配置文件：`src/main/resources/application-dev.yml`

重点项：

- 数据源：`spring.datasource.*`
- Redis：`spring.data.redis.*`
- DashScope：
  - `spring.ai.model.chat=dashscope`
  - `spring.ai.model.embedding=dashscope`
  - `spring.ai.dashscope.*`
- PgVector：
  - `spring.ai.vectorstore.pgvector.dimensions=1024`
  - `table-name=vector_store`
- Elasticsearch：`spring.elasticsearch.*`
- MinIO：`storage.minio.*`
- Sa-Token：`sa-token.*`

环境变量：

- `BAILIAN_API_KEY`（DashScope key）

---

## 10. 数据库迁移

Flyway 脚本目录：`src/main/resources/db/migration`

- `v1_init_core_tables.sql`：核心表初始化
- `v2_alter_users_add_auth_fields.sql`：用户认证字段
- `v3_ingestion_hardening.sql`：入库增强字段与索引
- `v4_fix_missing_ingestion_columns.sql`：兜底补齐列
- `v5_reset_vector_store_for_1024_dims.sql`：向量维度对齐（重建向量表）

---

## 11. 本地启动

### 11.1 准备依赖

1. PostgreSQL（安装 pgvector）
2. Redis
3. Elasticsearch（推荐已安装 IK）
4. MinIO（可用 `src/main/resources/docker/minio-docker-compose.yml`）

### 11.2 启动后端

```bash
mvn spring-boot:run
```

OpenAPI:

- [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 12. 观测与排障建议

### 12.1 任务无进展

关注日志是否出现：

- `Ingestion task created ...`
- `Ingestion job started ...`
- `Pipeline step: ...`

如果只有 created，没有 started，重点检查：

- 异步触发线程池是否可用
- 调度器是否在运行
- 数据库事务/锁冲突

### 12.2 常见问题

- `metadata_json is jsonb but expression is varchar`
  - 已通过自定义 jsonb TypeHandler 处理
- `expected 512 dimensions, not 1024`
  - 向量维度与 embedding 输出不一致，需统一为 1024 并重建向量表
- `content_hash 不存在`
  - 迁移未执行，检查 Flyway 历史并重启应用

---

## 13. 后续可增强方向

- 将入库调度改造为独立 worker 服务（与 API 服务解耦）
- 引入消息队列（Kafka/RabbitMQ）提升任务吞吐与可追溯性
- 增强文档解析策略（表格、目录、语义段落级切分）
- 增加更完善的观测（Prometheus 指标 + 链路追踪）
- 加入自动化集成测试（上传->入库->检索->问答）

