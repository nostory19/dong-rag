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

## 实现思路与技术要点

- **MinIO 存原文**：对象存储适合大文件与二进制；DB 仅存元数据与路径，减轻 PostgreSQL 体积与备份压力；下载流式解析控制内存峰值。
- **PgVector 与 Spring AI**：使用官方 starter 统一向量 CRUD；表名与维度由配置驱动，迁移 `v5` 处理维度变更时的「破坏性对齐」决策（宁可重建向量也不静默截断）。
- **metadata 设计**：`groupId`、`documentId`、`chunkIndex` 为检索过滤与去重最小集；附加标题、版本指纹等支撑产品功能而不过度依赖 JOIN。
- **Elasticsearch 与混合检索**：`RagChunkIndex` 映射与 IK 分词在集群侧安装；查询层用 `ElasticsearchOperations` 组装 NativeQuery，与向量路解耦，便于单独调优。
- **与入库一致性**：同一 chunk 序写入 DB、`vector_store`、`rag_chunk_index`；检索层只读索引，不在查询时修复写入不一致（由入库验收发现）。

上一篇：[08-assistant-session-dialogue.md](08-assistant-session-dialogue.md)  
下一篇：[10-observability-resilience-and-config.md](10-observability-resilience-and-config.md)
