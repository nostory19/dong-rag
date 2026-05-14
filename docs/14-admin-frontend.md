# 14 管理端前端（admin-frontend）

## 14.1 技术栈

与同仓库用户端类似：Vue 3、Ant Design Vue、Vite；鉴权信息存 `dong-rag-admin-auth`。

## 14.2 路由与菜单

| 路径 | 功能 |
|------|------|
| `/dashboard` | 仪表盘 |
| `/users` | 用户管理（admin） |
| `/ingestion-jobs` | 入库任务列表与详情抽屉 |
| `/complaint-eval` | 编排评测（固定样例 + `templateId`） |
| `/retrieval-detect` | **检索检测**：`groupId`、`topK`、JSON `cases`、可选重排前后对比开关 |
| `/system-runtime` | 系统运行信息（若已接 Actuator 等） |

布局：`AdminLayout.vue`；路由守卫要求 `isAdmin`。

## 14.3 调用的主要 API

- `GET /user/list`、`/rag/ingest/jobs`、`/rag/ingest/metrics`
- `POST /rag/detect/retrieval`
- `POST /assistant/eval/complaint`

封装见 `admin-frontend/src/api/services.ts`（`request` 基类在 `api/http.ts`，默认 `baseURL: '/api'`）。

## 14.4 构建

```bash
cd admin-frontend && npm install && npm run build
```

## 实现思路与技术要点

- **路由守卫 `isAdmin`**：管理端能力依赖后端 `admin` 角色；前端守卫提升体验，**真正的权限以后端 `@SaCheckRole` 为准**。
- **API 聚合在 `services.ts`**：入库任务、检测、评测等路径集中封装，便于统一加 `Authorization`、错误提示与 baseURL。
- **检索检测页**：提交 JSON cases 与后端 `RetrievalDetectRequest` 对齐；复杂 JSON 用文本域或 Monaco 类编辑器（若已引入）减少格式错误。
- **运维向页面**：任务列表、指标、系统运行时信息帮助定位「入库卡住还是 ES 慢」；与 Actuator/Prometheus 文档联动做闭环。
- **与用户端隔离**：独立构建产物、独立 auth 键名，避免运营误用用户端入口操作高危接口。

上一篇：[13-frontend-user-app.md](13-frontend-user-app.md)  
返回：[文档中心 README](README.md)
