# 09 存储：MinIO、PgVector 与 Elasticsearch

## 9.1 MinIO

- **用途**：保存入库原始文件（二进制），`documents` 表记录 `storage_bucket`、`storage_object_key`。
- **配置**：`storage.minio.*`（`application-dev.yml`）。
- **实现**：`MinioStorageService` / `MinioStorageServiceImpl`，入库流水线下载字节流后解析。

## 9.2 PgVector（`vector_store`）

- **Starter**：`spring-ai-starter-vector-store-pgvector`。
- **表**：默认 `vector_store`（`spring.ai.vectorstore.pgvector.table-name`）。
- **维度**：与 embedding 一致（如 `1024`）；迁移 `v5_reset_vector_store_for_1024_dims.sql` 用于维度对齐场景。
- **写入**：`RagIngestionServiceImpl.indexVector`，按文档先删后加；metadata 必须含 `groupId`、`documentId`、`chunkIndex` 等以便检索过滤与审计。

## 9.3 Elasticsearch

- **索引模型**：`RagChunkIndex`（`@Document(indexName = "rag_chunk_index")`），字段含 `groupId`、`documentId`、`chunkIndex`、`fileName`、`content`（IK 分词器需在集群侧安装）。
- **写入**：`RagChunkIndexRepository` + `indexEs` 全量替换某文档的 chunk 文档。
- **检索**：`ElasticsearchOperations` + NativeQuery。

## 9.4 与混合检索的关系

向量路召回语义相似片段，ES 路召回关键词命中片段，RRF 合并后（可选重排）再邻接扩展，最终得到 `ChunkEvidenceVO` 列表供问答与 `KB_SEARCH` 使用。

## 9.5 Actuator 与 ES 版本

若 ES 集群版本与 `elasticsearch-java` 客户端不兼容，**健康检查**解析 `_cluster/health` 可能失败。可在 `management.health.elasticsearch.enabled: false` 临时关闭（见 [10-observability-resilience-and-config.md](10-observability-resilience-and-config.md)）。

上一篇：[08-assistant-session-dialogue.md](08-assistant-session-dialogue.md)  
下一篇：[10-observability-resilience-and-config.md](10-observability-resilience-and-config.md)
