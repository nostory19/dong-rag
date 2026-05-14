# 08 助手会话、意图、引导与上下文

## 8.1 数据表（Flyway `v6_assistant_conversation.sql`）

- **`assistant_conversations`**：`user_id`、`group_id`、`template_id`、`rolling_summary`、`slot_state_json`、`last_compressed_at`、`status` 等。
- **`assistant_messages`**：`conversation_id`、`role`（`user`/`assistant`/`system`）、`content`、`intent`、`trace_id`、`metadata_json`。

服务：`AssistantConversationService` / `AssistantConversationServiceImpl`。

## 8.2 会话生命周期（API 行为）

1. 请求体 **`conversationId` 可选**。未传时 `ensureConversation` 创建新行，首个 NDJSON `start` 的 `data` 返回 `conversationId`。
2. 传入时：解析为 Long，校验归属 `user_id` + `group_id`。
3. 每轮：`appendMessage` 写入用户消息 → 编排 → `appendMessage` 写入助手回复；异常可写 `system` 错误行。

## 8.3 意图路由（`IntentRoutingServiceImpl`）

- **规则优先**：关键词映射 `LEGAL_RISK`、`LOGISTICS`、`REFUND_BILLING`、`QUALITY_PRODUCT`、`COMPLAINT_GENERAL`（投诉模板）、默认 `GENERAL`。
- **LLM 补充**：`dongrag.assistant.intent-llm-enabled=true` 且规则置信不足时，短 prompt 输出 JSON 分类。
- **`routeKind`**：`KNOWLEDGE_RAG` / `TOOL_HEAVY` / `MIXED_KNOWLEDGE`（启发式，供前端或后续混合编排扩展），随 `intent` 事件与消息 `metadata` 下发。

## 8.4 投诉槽位与引导（`COMPLAINT_MULTI_LEGACY`）

- **`ComplaintSlots`** + **`ComplaintSlotMergeUtil`**：从用户文本正则合并订单号、渠道等。
- **`GuidanceServiceImpl`**：缺槽时生成 `questions`、`missingSlots`，经 NDJSON **`guide`** 下发。
- 编排侧：`plannerPayload` / `aggregatorPayload` 拼接 `ContextBuilder` 块、槽位摘要行、`【仍缺信息】` 等（见 `AssistantServiceImpl`）。

## 8.5 上下文滑窗（`ContextBuilder`）

- 从 `assistant_messages` 取最近 `dongrag.assistant.context-message-limit` 条（时间升序）。
- 前置 `rolling_summary`（若有）。
- 总字符上限 `context-max-chars`，超长截尾部。

注入点：`MultiAgentOrchestratorService.run` 的 `plannerPayload` 与 `aggregatorPayload`；Worker 仍以**本轮用户原文**参与检索提示，避免过长历史污染子任务。

## 8.6 异步压缩（`ConversationCompressor`）

当近期消息总字符超过 `compress-threshold-chars` 时，虚拟线程异步调用 LLM 生成新 `rolling_summary`，更新 `last_compressed_at`。

## 8.7 Micrometer

`AssistantServiceImpl` 对整轮多专家处理打点：`dongrag.assistant.multi_agent` Timer。

## 实现思路与技术要点

- **会话表与消息表分离**：`assistant_conversations` 存滚动摘要、槽位、状态；`assistant_messages` 存多轮轨迹，便于按轮回放与压缩，避免单表 JSON 无限膨胀。
- **`conversationId` 可选**：首次由服务端生成并在 `start` 事件返回，前端持久化后后续轮次携带；服务端校验 `user_id` + `group_id`，防止会话劫持。
- **意图路由**：规则优先保证低延迟与可解释；在规则置信不足时再走短 LLM 分类（可配置关闭），平衡成本与准确率。
- **投诉槽位**：正则 + 合并工具从自然语言抽取结构化槽位，`GuidanceServiceImpl` 在缺槽时发 `guide` 事件，驱动前端多轮补全后再编排。
- **ContextBuilder 滑窗**：限制条数与总字符，防止 Planner/聚合阶段被历史淹没；Worker 仍用**本轮用户原文**做检索，避免历史噪声污染 embedding 查询。
- **异步压缩**：超阈值后用虚拟线程异步摘要写回 `rolling_summary`，不阻塞本轮响应；与 `last_compressed_at` 配合可观测压缩频率。
- **JSONB 与 MyBatis**：复杂 metadata 走 jsonb + `JsonbTypeHandler`，避免手写字符串拼接 SQL。

上一篇：[07-knowledge-assistant-multi-agent.md](07-knowledge-assistant-multi-agent.md)  
下一篇：[09-storage-vectors-and-elasticsearch.md](09-storage-vectors-and-elasticsearch.md)
