# 12 数据库、Flyway 与常见问题

## 12.1 Flyway 脚本列表

目录：`src/main/resources/db/migration`

| 版本文件 | 作用 |
|----------|------|
| `v1_init_core_tables.sql` | 核心表初始化 |
| `v2_alter_users_add_auth_fields.sql` | 用户认证字段 |
| `v3_ingestion_hardening.sql` | 入库增强字段与索引 |
| `v4_fix_missing_ingestion_columns.sql` | 兜底补列 |
| `v5_reset_vector_store_for_1024_dims.sql` | 向量维度与 embedding 对齐（重建向量相关存储） |
| `v6_assistant_conversation.sql` | 助手会话与消息表、摘要与槽位字段 |

启动时 `spring.flyway.enabled=true`（`application-dev.yml`）自动迁移。

## 12.2 JSONB 与 MyBatis

助手消息等字段使用 **jsonb** 时，通过自定义 **`JsonbTypeHandler`**（`config/typehandler`）避免「jsonb but expression is varchar」类错误。

## 12.3 常见问题

| 现象 | 可能原因 | 处理方向 |
|------|----------|----------|
| `expected 512 dimensions, not 1024` | 向量表维度与 embedding 不一致 | 对齐维度并执行 v5 或等价重建 |
| `content_hash 不存在` | 迁移未执行到最新 | 检查 Flyway 历史、环境是否连错库 |
| 入库任务一直 PENDING | 调度/线程池未跑、DB 锁 | 查日志 `Ingestion job started`、线程池配置 |
| ES 健康检查失败 | ES 与 client 版本不一致 | 关闭 `management.health.elasticsearch.enabled` 或升级集群（见 docs/10） |
| 助手无多轮记忆 | 未传 `conversationId` 或点了「新会话」 | 持久化 `start` 返回的 id |

## 12.4 本地依赖清单

PostgreSQL（含 pgvector）、Redis、Elasticsearch（推荐 IK）、MinIO。可参考仓库内 Docker Compose（若存在 `minio-docker-compose.yml` 等）。

## 实现思路与技术要点

- **Flyway 只增不改历史**：已应用的迁移脚本视为不可变；修正通过新 `vN_*.sql`，避免团队环境迁移历史分叉。
- **版本脚本职责拆分**：`v1`–`v4` 渐进补全入库与认证字段；`v5` 单独处理向量维度与表重建类高风险变更，文档化「为何破坏性」；`v6` 引入助手会话与消息，与助手功能开关解耦（表可先存在、功能后开）。
- **JsonbTypeHandler**：PostgreSQL jsonb 与 JDBC 类型映射在框架边界统一处理，实体字段用对象或 String 序列化，避免每个 Mapper XML 重复 `::jsonb`。
- **排障表驱动**：常见问题（维度不一致、迁移未跑、任务卡住、ES health）与现象一一对应，缩短「问人」路径；严重问题优先查 Flyway 历史表与日志中的 SQL 状态。
- **本地依赖**：与 `application-dev.yml` 默认指向一致，减少「代码能跑但环境缺一块」的摩擦。

上一篇：[11-retrieval-detection-and-eval.md](11-retrieval-detection-and-eval.md)  
下一篇：[13-frontend-user-app.md](13-frontend-user-app.md)
