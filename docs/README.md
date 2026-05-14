# Dong RAG 文档中心

本目录按**讲解项目的逻辑**拆分主题：从总览到认证、数据隔离、入库、检索、助手、存储、观测、检测、库表、前端与排障。根目录 [README.md](../README.md) 提供**大纲导航**与快速上手；**具体设计与实现细节**以本目录为准。

**工程约定与代码规范**见 [00-coding-standards.md](00-coding-standards.md)。各专题文档末尾附有 **「实现思路与技术要点」**，说明该模块在代码里如何落地、为何这样设计。

## 文档目录

| 编号 | 文档 | 内容概要 |
|------|------|----------|
| 00 | [00-coding-standards.md](00-coding-standards.md) | 提交规范、后端/前端/脚本约定、配置与日志、临时文件与多租户安全 |
| 01 | [01-project-overview.md](01-project-overview.md) | 项目定位、能力边界、与前端关系、概念链路 |
| 02 | [02-tech-stack-and-structure.md](02-tech-stack-and-structure.md) | 技术栈、包结构、关键类索引 |
| 03 | [03-authentication-and-authorization.md](03-authentication-and-authorization.md) | Sa-Token、登录/角色、与业务校验 |
| 04 | [04-group-and-data-isolation.md](04-group-and-data-isolation.md) | 组模型、接口、组级隔离在检索中的落实 |
| 05 | [05-document-ingestion-pipeline.md](05-document-ingestion-pipeline.md) | 入库全流程、状态机、重试、一致性验收 |
| 06 | [06-hybrid-retrieval-and-rag-qa.md](06-hybrid-retrieval-and-rag-qa.md) | 混合检索、RRF、重排、RAG 问答、缓存与 Prompt |
| 07 | [07-knowledge-assistant-multi-agent.md](07-knowledge-assistant-multi-agent.md) | 多专家编排、Planner、Worker、Policy、NDJSON |
| 08 | [08-assistant-session-dialogue.md](08-assistant-session-dialogue.md) | 会话表、意图、引导、上下文、压缩 |
| 09 | [09-storage-vectors-and-elasticsearch.md](09-storage-vectors-and-elasticsearch.md) | MinIO、PgVector、ES 索引与元数据 |
| 10 | [10-observability-resilience-and-config.md](10-observability-resilience-and-config.md) | Actuator、Prometheus、Resilience4j、`dongrag.ai`、ES 健康检查关闭说明 |
| 11 | [11-retrieval-detection-and-eval.md](11-retrieval-detection-and-eval.md) | 检索检测 API、金标指标、编排评测 |
| 12 | [12-database-flyway-and-troubleshooting.md](12-database-flyway-and-troubleshooting.md) | Flyway 版本、常见问题 |
| 13 | [13-frontend-user-app.md](13-frontend-user-app.md) | 用户端 Vue 路由、助手流式与会话 |
| 14 | [14-admin-frontend.md](14-admin-frontend.md) | 管理端功能与路由 |

建议阅读顺序：**00（规范）→ 01 → 02 → 04 → 05 → 06 → 07 → 08**；运维与配置重点看 **10、12**；前端对接看 **13、14**。
