# 03 认证与权限

## 3.1 Sa-Token 集成

- **Starter**：`sa-token-spring-boot3-starter` + `sa-token-redis-template`，Token 存 Redis，支持多端登录策略（见 `application-dev.yml` 中 `sa-token.*`）。
- **读取方式**：`is-read-header: true`，前端在请求头携带 `Authorization: <token>`（用户端与管理端各自 localStorage 键名不同，见前端文档）。
- **实现类**：`config/SaTokenPermissionImpl`（若项目中有自定义权限逻辑可在此扩展）。

## 3.2 登录与用户接口

- `POST /user/register`：注册
- `POST /user/login`：登录，返回 `LoginUserVO`（含 `token`、角色等）
- `POST /user/logout`：登出
- `GET /user/list`：**admin** 角色（`@SaCheckRole("admin")`）

控制器：`UserController`。

## 3.3 接口级保护模式

| 模式 | 说明 | 示例 |
|------|------|------|
| `@SaCheckLogin` | 必须登录 | `RagController` 类级别、`AssistantController` |
| `@SaCheckRole("admin")` | 必须管理员 | 入库任务列表、重试、重建索引、检索检测、编排评测 |

业务内二次校验：如 `GroupService.checkGroupReadable(userId, groupId)`，防止用户越权访问其他组数据。

## 3.4 与助手会话的关系

助手接口在登录后根据 `userId` + `groupId` 创建或绑定 `assistant_conversations`，防止 `conversationId` 被其他用户冒用。

上一篇：[02-tech-stack-and-structure.md](02-tech-stack-and-structure.md)  
下一篇：[04-group-and-data-isolation.md](04-group-and-data-isolation.md)
