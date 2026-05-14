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
10. **检索验收**：对当前 `groupId` 用文件名等问题做一次 `retrieveWithJudgement`，要求能召回本文档
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

上一篇：[04-group-and-data-isolation.md](04-group-and-data-isolation.md)  
下一篇：[06-hybrid-retrieval-and-rag-qa.md](06-hybrid-retrieval-and-rag-qa.md)
