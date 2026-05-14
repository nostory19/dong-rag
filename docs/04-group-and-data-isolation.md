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

上一篇：[03-authentication-and-authorization.md](03-authentication-and-authorization.md)  
下一篇：[05-document-ingestion-pipeline.md](05-document-ingestion-pipeline.md)
