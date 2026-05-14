# 06 混合检索与 RAG 问答

## 6.1 服务与接口

| 组件 | 类 | 接口 |
|------|-----|------|
| 混合检索 | `HybridRetrievalServiceImpl` | 被 `RagQaServiceImpl`、`KnowledgeBaseSearchTool`、`RetrievalDetectionServiceImpl` 等调用 |
| RAG 问答 | `RagQaServiceImpl` | `POST /rag/qa/ask` |

## 6.2 混合检索算法（实现要点）

1. **向量检索**：`VectorStore.similaritySearch`，按 `groupId` 过滤 metadata。
2. **ES 检索**：`match` on `content` + `term` filter `groupId`。
3. **RRF 融合**：`k=60` 的倒数排名融合，合并两路候选，生成 `hybrid` 分数。
4. **可选 LLM 重排**：`dongrag.ai.retrieval-rerank-enabled=true` 时，`EvidenceRerankerImpl` 对前 N 条片段请求模型输出下标排序 JSON，再截断到 `topK`；关闭时等价于按 RRF 分数截断。
5. **邻接窗口**：对 Top 核 chunk 扩展 ±1 `chunk_index`，从 `document_chunks` 取邻块，分数衰减系数 `0.85`，再排序截断（上限与 `topK` 相关）。

接口 `hybridRetrieve(groupId, question, topK, applyRerank)`：`applyRerank=false` 用于检索检测基线对比。

## 6.3 证据门控（`retrieveWithJudgement`）

- `confidenceScore`：当前证据列表中 **RRF 分数最大值**。
- `confidenceLevel`：`HIGH` / `MEDIUM` / `LOW`（阈值约 `0.03` / `0.015`）。
- `evidenceEnough`：分数 ≥ `0.012` 且列表非空才为真。

`RagQaServiceImpl` 在 `evidenceEnough == false` 时**不调 LLM**，直接返回固定拒答文案，降低幻觉风险。

## 6.4 RAG 问答流程

1. 登录 + 组可读校验
2. `retrieveWithJudgement`
3. 证据不足 → 返回 `QaAnswerVO`（无生成）
4. 证据足 → 拼装「问题 + 编号证据」user prompt
5. `ChatClient`：`system` 来自 `classpath:prompts/rag-qa-system.txt`（`PromptResourceLoader`），失败回退内置文案
6. 默认 Chat 参数由 `ChatDefaultsConfig` + `DongragAiProperties`（`chat-temperature`、`chat-max-tokens`）注入

## 6.5 熔断与指标

- **Resilience4j**：检索包 `retrieval` 熔断器；问答 LLM 包 `llm` 熔断器（可用 `dongrag.ai.*-circuit-breaker-enabled` 关闭）。
- **Micrometer**：`dongrag.retrieval.hybrid`、`dongrag.retrieval.judgement`、`dongrag.rag.qa.llm` 等 Timer。

## 6.6 答案缓存（可选）

- 配置 `dongrag.ai.qa-answer-cache-ttl-seconds > 0` 启用 Redis 缓存。
- Key 组成：`GroupKnowledgeRevisionService.fingerprint(groupId)`（READY 文档数 + 最近更新时间）+ 问题 hash + `topK`，避免知识更新后仍命中旧答案。
- 命中时 `QaAnswerVO.fromCache=true`；`usage` 字段在缓存命中时通常为 null。

## 6.7 Token 用量

当模型 `ChatResponse` 返回 `Usage` 时，写入 `QaAnswerVO` 的 `promptTokens`、`completionTokens`、`totalTokens`。

## 实现思路与技术要点

- **双路召回 + RRF**：向量路补语义、ES 路补关键词；倒数排名融合（`k=60`）对各路分数尺度不敏感，比线性加权更稳健。
- **可选 LLM 重排**：高价值场景下用模型对候选片段排序，代价是延迟与费用；通过 `dongrag.ai.retrieval-rerank-enabled` 与候选上限控制开关与成本；`applyRerank=false` 为检测脚本提供「基线对照」入口。
- **邻接窗口扩展**：Top 命中块向前后扩展相邻 chunk，恢复被切分打断的段落上下文，衰减系数避免无限膨胀。
- **证据门控**：用 RRF 最大分等启发式判断「是否值得调用生成模型」，证据不足时**直接拒答**，优先可预期行为而非「胡编」。
- **熔断与指标**：检索与 LLM 分属 Resilience4j 实例，避免一路拖垮另一路；Micrometer 记录各阶段耗时便于定界（检索慢 vs 生成慢）。
- **答案缓存**：Key 绑定 `GroupKnowledgeRevisionService.fingerprint(groupId)`，知识变更后自动失活；TTL 由 `qa-answer-cache-ttl-seconds` 控制。
- **核心类**：`HybridRetrievalServiceImpl`、`RagQaServiceImpl`、`EvidenceRerankerImpl`（若启用）、`PromptResourceLoader`（外置 system prompt）。

上一篇：[05-document-ingestion-pipeline.md](05-document-ingestion-pipeline.md)  
下一篇：[07-knowledge-assistant-multi-agent.md](07-knowledge-assistant-multi-agent.md)
