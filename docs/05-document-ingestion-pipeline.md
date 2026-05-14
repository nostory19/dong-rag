# 05 文档入库流水线

## 5.1 核心实现类

- **`RagIngestionServiceImpl`**：上传登记、异步触发、定时轮询、解析、切分、双索引、验收、状态回写。
- **`IngestionJobService`**：任务记录、重试、指标（与 `ingestion_jobs` 表对应）。

## 5.2 上传接口（快速返回）

| 接口 | 说明 |
|------|------|
| `POST /rag/ingest/text` | 纯文本入库，请求体含 `groupId`、正文等 |
| `POST /rag/ingest/file` | `multipart/form-data`，`groupId` + `file` |

行为概要：

1. 鉴权 + `checkGroupWritable`（或等价组权限）
2. 文件写入 **MinIO**，路径写入 `documents.storage_*`
3. `documents` 行插入，状态 `UPLOADED`
4. `ingestion_jobs` 插入，`PENDING`
5. 立即异步提交一次执行 + **定时调度**兜底拉取 `PENDING` / `RETRY_WAITING`

## 5.3 异步执行流水线（Step C）

1. 文档标为 `PROCESSING`
2. MinIO 下载原文
3. 解析：`md`/`txt` 直读 UTF-8；其余走 **Apache Tika**
4. 内容质量校验（空、过短、乱码比例等）
5. **切分**：配置项 `ingestion.chunking.*`（`target-tokens`、`max-tokens`、`overlap-tokens`）
6. 写入 **`document_chunks`**，并生成 `metadata_json`（含 `sectionTitle`、`pageNo`、hash 等）
7. **向量索引**：`vectorStore.delete` 按 `documentId` 清理旧向量后 `add`；metadata 含 `groupId`、`documentId`、`chunkIndex`、`fileName`、`charStart/End`、`documentTitle`、`documentVersionEpoch`、`kbFingerprint` 等（便于引用与缓存失效）
8. **ES 索引**：`rag_chunk_index`，与 chunk 一一对应
9. **一致性校验**：DB chunk 数、已向量化数、ES 条数一致
10. **检索验收**：对当前 `groupId` 用「**文件名 + 首块正文摘要**」做一次 `retrieveWithJudgement`（`topK`≥5），结果中须出现本文档 id（避免仅用文件名时正文不含文件名导致 ES/向量均难命中）
11. 成功：`documents` → `READY`，任务 → `SUCCESS`

失败：写 `failure_reason` / `last_error`，按策略 `RETRY_WAITING` 或 `FAILED`。

## 5.4 文档与任务状态

详见根 README「数据模型与状态机」摘要，或下表：

**`documents.status`**：`UPLOADED` → `PROCESSING` → `READY` | `FAILED`

**`ingestion_jobs.status`**：`PENDING` → `RUNNING` → `SUCCESS` | `RETRY_WAITING` | `FAILED`

## 5.5 管理端运维接口（admin）

- `GET /rag/ingest/jobs`：任务列表
- `GET /rag/ingest/jobs/{jobId}`：详情
- `POST /rag/ingest/jobs/{jobId}/retry`：重试
- `POST /rag/ingest/documents/{documentId}/rebuild`：按文档重建索引
- `GET /rag/ingest/metrics`：聚合指标

## 5.6 向量批量写入

`ingestion.vector.add-batch-size`：控制单次 `VectorStore.add` 的 batch，减轻 embedding 压力。

## 实现思路与技术要点

- **快速返回 + 异步执行**：上传接口只做「落 MinIO + 落库 + 投递任务」，避免阻塞 Tomcat 线程；`@Async` 或调度线程立即拉一次 `PENDING`，定时任务兜住进程重启或瞬时失败。
- **状态机驱动**：`documents.status` 与 `ingestion_jobs.status` 分离，便于「同一文档多次重试/重建」与运营查询；失败写入 `failure_reason` 便于排障而非静默失败。
- **解析策略**：纯文本直读降低依赖；二进制/Office 等走 Tika，统一入口减少格式分支爆炸。
- **先删后写向量**：同一 `documentId` 重索引时清理旧向量与旧 ES 文档，避免检索到过期 chunk；metadata 携带 `documentVersionEpoch`、`kbFingerprint` 等支撑缓存失效与审计。
- **一致性校验 + 检索验收**：数量对齐保证「写库成功但索引半成功」可被发现；用 `retrieveWithJudgement` 做一次真实检索验收，避免「索引有数据但不可搜」的假象（尤其文件名与正文不一致时）。
- **代码入口**：主流程在 `RagIngestionServiceImpl`，任务与指标在 `IngestionJobService`；调参集中在 `application*.yml` 的 `ingestion.*` 与 `dongrag.ai.*`。

上一篇：[04-group-and-data-isolation.md](04-group-and-data-isolation.md)  
下一篇：[06-hybrid-retrieval-and-rag-qa.md](06-hybrid-retrieval-and-rag-qa.md)
