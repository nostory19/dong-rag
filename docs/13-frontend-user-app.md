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

上一篇：[12-database-flyway-and-troubleshooting.md](12-database-flyway-and-troubleshooting.md)  
下一篇：[14-admin-frontend.md](14-admin-frontend.md)
