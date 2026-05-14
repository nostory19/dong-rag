# 11 检索检测与编排评测

## 11.1 检索检测

- **路径**：`POST /rag/detect/retrieval`
- **权限**：`@SaCheckRole("admin")` + `checkGroupReadable`
- **请求**：`RetrievalDetectRequest`：`groupId`、`topK`、`cases[]`；可选 **`includeRerankComparison`**（在开启 `retrieval-rerank-enabled` 时对比重排前后金标 rank / MRR）。
- **响应**：`RetrievalDetectResponseVO`：`caseCount`、`labeledCount`、`meanHitAt1`、`meanHitAtK`、`mrr`、可选基线汇总、每条 `details`（证据列表正文截断约 500 字）。

**指标定义**：

- `rankOfGold`：金标 `(documentId, chunkIndex)` 在 `hybridRetrieve(..., applyRerank=true)` 结果列表中**首次出现**的 1-based 位置。
- `hitAt1` / `hitAtK` / `reciprocalRank`：由 rank 与请求 `topK` 推导；未命中 RR 为 0。

实现：`RetrievalDetectionServiceImpl`。

## 11.2 管理端页面

- 路由：`/retrieval-detect`
- 组件：`admin-frontend/src/pages/RetrievalDetectPage.vue`
- API：`ragDetectApi.detectRetrieval`

## 11.3 编排评测（助手）

- **路径**：`POST /assistant/eval/complaint?groupId=&templateId=`
- **实现**：`ComplaintEvaluationService`，样例问题集分投诉模板与内部多专家模板，输出 `handoffRate`、`avgSubTaskCount`、`details`。
- **页面**：`/complaint-eval`（`ComplaintEvalPage.vue`）。

## 11.4 与混合检索实现的关系

检测服务直接调用 `HybridRetrievalService`，与线上 `KB_SEARCH`、RAG 使用同一套检索核心，便于离线对比调参。

上一篇：[10-observability-resilience-and-config.md](10-observability-resilience-and-config.md)  
下一篇：[12-database-flyway-and-troubleshooting.md](12-database-flyway-and-troubleshooting.md)
