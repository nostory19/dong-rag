# 07 知识助手：多专家编排

## 7.1 接口与协议

- **路径**：`POST /assistant/chat`
- **响应**：`Content-Type: application/x-ndjson`，每行 `{"event":"...","data":...}`

## 7.2 模板与归一

- **`AssistantServiceImpl.resolveAssistantTemplate`**：`INTERNAL_KB_SIMPLE` 归一为 **`INTERNAL_KB_MULTI`**。
- 显式 **`COMPLAINT_MULTI_LEGACY`**：投诉编排（Planner 允许的 Worker 集合不同，话术与策略不同）。

## 7.3 运行时组件

| 组件 | 作用 |
|------|------|
| `AgentRunContextFactory` | 合并模板默认 Worker、`GroupAgentConfigService` 覆盖、与 `WorkerRegistry` 求交 → `allowedWorkerTypes` |
| `MultiAgentOrchestratorService` | Planner 生成 `TaskPlan` → `sanitizePlan` → 虚拟线程并行 `DomainWorker` → `ResponseAggregator` |
| `DomainWorker` | 子任务执行：`execute(SubTask, originalUserMessage, ctx)` |
| `KbToolDomainWorker` | 统一 `ChatClient` + `.tools(knowledgeBaseSearchTool)`，强制 KB 取证 |
| `KnowledgeBaseSearchTool` | `@Tool` `KB_SEARCH`，内部 `HybridRetrievalService.retrieveWithJudgement` |
| `TemplateAwareAgentOutputPolicy` | 输出策略：投诉高危、内部多专家升级等 |

## 7.4 Planner 与 sanitize

- Planner 输出 JSON `TaskPlan`（含 `subTasks`：`id`、`description`、`assignedAgent`）。
- **sanitize**：最多 3 个子任务；`assignedAgent` 必须在 `allowedWorkerTypes`；非法类型回退到 `GENERAL_KB` 等。

## 7.5 Worker 类型（当前）

- `GENERAL_KB` — `GeneralKbDomainWorker`
- `TECH_SUPPORT` — `TechSupportAgent`
- `PRODUCT` — `ProductIssueAgent`
- `AFTER_SALES` — `AfterSalesPolicyAgent`

## 7.6 与 `AgentRunContext` 的安全约束

`KbToolDomainWorker` 将 **`groupId`、`topK`、`conversationId`、`traceId`** 写入 user prompt，避免模型随意改租户。

## 7.7 NDJSON 事件（常用）

含 `start`、`intent`、`guide`、`route-plan`、`worker-start`、`worker-done`、`policy-hit`、`token`、`actions`、`tool-log`、`tool-log-summary`、`done`、`error`。详见根 README 表格或本文档姊妹篇 [08-assistant-session-dialogue.md](08-assistant-session-dialogue.md) 中意图/引导与多轮部分。

## 7.8 评测（管理端）

`POST /assistant/eval/complaint`：`ComplaintEvaluationService.quickEvaluate`，固定样例集统计子任务数、转人工率等。

## 7.9 其他编排入口

`ComplaintOrchestratorService`：评测或兼容路径下可能直接以投诉模板调用编排（与主助手流共享 `MultiAgentOrchestratorService` 能力）。

## 实现思路与技术要点

- **NDJSON 流式**：助手链路长、含多事件；逐行 JSON 比 SSE 自定义更少依赖，前端 `fetch` + 流即可消费；`event` 字段稳定协议便于扩展。
- **模板归一**：`INTERNAL_KB_SIMPLE` 合并到 `INTERNAL_KB_MULTI`，减少前端/配置分叉，统一走多专家路径；投诉模板显式保留以加载不同 Planner 约束与话术。
- **Planner → sanitize → 并行 Worker**：Planner 产出结构化子任务；`sanitize` 限制数量与 `assignedAgent` 白名单，防止模型胡列 agent 名导致运行时异常；并行缩短_wall clock_。
- **KbToolDomainWorker 模式**：每个领域 Worker 绑定 `ChatClient` + `KnowledgeBaseSearchTool`，强制通过 `@Tool` 检索，便于日志与策略层审计「用过哪些证据」。
- **上下文注入**：`groupId`、`topK`、`conversationId`、`traceId` 写入模型侧提示，降低越权与串组风险；与 [08](08-assistant-session-dialogue.md) 的会话、意图信息组合使用。
- **Policy 层**：`TemplateAwareAgentOutputPolicy` 在汇总后做模板相关约束（如投诉高危话术），与纯模型生成解耦。
- **编排评测**：`ComplaintEvaluationService` 用固定样例集统计子任务数、转人工率等，用于回归对比 Prompt/Planner 变更。

上一篇：[06-hybrid-retrieval-and-rag-qa.md](06-hybrid-retrieval-and-rag-qa.md)  
下一篇：[08-assistant-session-dialogue.md](08-assistant-session-dialogue.md)
