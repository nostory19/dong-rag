# 04 组与数据隔离

## 4.1 概念模型

- **组（Group）**：知识空间的边界，文档、向量元数据、ES 文档均带 `groupId`。
- **组成员（GroupMembership）**：用户与组的多对多关系，决定能否读/写该组资源。

## 4.2 接口

- `POST /group/create`：创建组
- `POST /group/join`：入组，请求体 `JoinGroupRequest`（含 `groupId`）
- `GET /group/my/list`：当前用户可见的组列表

控制器：`GroupController`。

## 4.3 服务端校验链

典型顺序：

1. `AuthContextService.requireLoginUserId()` 取当前用户
2. `GroupService.checkGroupReadable` 或写入前等价校验
3. 检索/入库/助手均传入 `groupId`，在向量过滤、ES `term` 过滤、SQL 条件中落实

## 4.4 向量与 ES 中的体现

- **向量检索**：`HybridRetrievalServiceImpl.retrieveFromVector` 中比对 metadata 的 `groupId`。
- **ES 检索**：`bool` 查询中带 `filter`：`groupId` 精确匹配。

详见 [06-hybrid-retrieval-and-rag-qa.md](06-hybrid-retrieval-and-rag-qa.md)、[09-storage-vectors-and-elasticsearch.md](09-storage-vectors-and-elasticsearch.md)。

## 实现思路与技术要点

- **纵深防御**：即使前端隐藏了其他组的入口，仍必须在服务端每条链路上带 `groupId` 并校验成员关系；向量与 ES 侧用 **filter/metadata** 固定组条件，避免仅靠应用层排序后「误泄漏」。
- **校验顺序**：`AuthContextService.requireLoginUserId()` → `GroupService` 成员校验 → 业务逻辑；Controller 层注解无法表达「该 `groupId` 是否属于当前用户」，故第二步不可省略。
- **向量路实现要点**：`HybridRetrievalServiceImpl` 在相似度召回后按 metadata 的 `groupId` 过滤，防止 Spring AI 返回跨组邻居（若底层未强约束）。
- **ES 路实现要点**：`bool` 查询中 `groupId` 放在 **filter** 上下文，不参与算分，稳定且利于缓存。
- **数据模型**：`Group`、`GroupMembership` 与文档/任务表外键或逻辑关联在 Service 层维护一致性（入组、退组策略按产品演进）。

上一篇：[03-authentication-and-authorization.md](03-authentication-and-authorization.md)  
下一篇：[05-document-ingestion-pipeline.md](05-document-ingestion-pipeline.md)
