# 13 用户端前端（frontend）

## 13.1 技术栈

- Vue 3 + Vue Router + Pinia
- Ant Design Vue
- Vite 构建；`vite.config` 中将 `/api` 代理到后端（本地开发）

## 13.2 路由与功能页

| 路径 | 页面 | 说明 |
|------|------|------|
| `/login`、`/register` | 登录/注册 | Sa-Token，localStorage 存 `dong-rag-user-auth` |
| `/groups` | 我的组 | 创建/加入组、切换 `currentGroupId`（Pinia `group` store） |
| `/documents` | 文档入库 | 上传与任务进度 |
| `/qa` | 知识问答 | 调用 `POST /rag/qa/ask` |
| `/assistant` | 知识助手 | `POST /assistant/chat` **NDJSON 流式**；维护 `conversationId`（`sessionStorage` 按组）；展示意图、引导、对话气泡、事件流 |

## 13.3 知识助手流式协议（前端侧）

1. `fetch` + `ReadableStream` 按行 `JSON.parse`。
2. 处理 `start` → 持久化 `conversationId`。
3. `intent` / `guide` → 展示卡片。
4. `token` → 拼接答复（`requestAnimationFrame` 节流可选）。
5. `done` / `error` → 结束状态。

## 13.4 OpenAPI 生成（可选）

`frontend` 下可有 `openapi.json` 与 `src/api/generated`（以项目脚本为准），与后端 `/v3/api-docs` 同步。

## 实现思路与技术要点

- **Vite 代理 `/api`**：开发态同源路径，避免 CORS 预检与 Cookie 域问题；生产由网关或 Nginx 等价转发，前端不写死后端主机。
- **Pinia 管理当前组**：`groupId` 作为后续入库、问答、助手请求体的一部分，与后端组隔离模型对齐；切换组时清空或隔离会话存储，防止串组。
- **助手 NDJSON 消费**：`ReadableStream` 按行 `JSON.parse`，对异常行容错或记录；`start` 持久化 `conversationId` 到 `sessionStorage`（按组键）实现多轮；`token` 拼接可用 `requestAnimationFrame` 节流减轻渲染压力。
- **鉴权存储**：用户端独立 localStorage 键，避免与管理端 token 混用；登出清理存储与内存状态。
- **与后端协议对齐**：事件名、字段与 [07](07-knowledge-assistant-multi-agent.md)、[08](08-assistant-session-dialogue.md) 一致，前端只做展示与轻量状态机，不把业务规则复制一份。

上一篇：[12-database-flyway-and-troubleshooting.md](12-database-flyway-and-troubleshooting.md)  
下一篇：[14-admin-frontend.md](14-admin-frontend.md)
