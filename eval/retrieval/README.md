# 检索离线评测集（金标）

用于仓库根目录下 [`scripts/run_retrieval_eval.py`](../../scripts/run_retrieval_eval.py) 批量调用 `POST /rag/detect/retrieval`，得到 **mean Hit@1 / mean Hit@K、MRR**（及可选重排基线），便于写进简历或实验记录。

## 字段说明

- **`meta.datasetName`**：数据集标识，写入结果文件名。
- **`meta.groupId`**：知识组 ID（须与文档入库组一致）。
- **`meta.topK`**：可选，1～10，默认 5。
- **`meta.includeRerankComparison`**：可选；为 `true` 且服务端 `dongrag.ai.retrieval-rerank-enabled=true` 时，接口会返回未重排的基线指标。
- **`cases[].question`**：评测问句。
- **`cases[].goldDocumentId` / `goldChunkIndex`**：金标 chunk；**两者都填**才参与 Hit@K / MRR 统计。缺一或为空则该条仅跑检索明细，不计入 labeled。

## 一键生成金标（推荐）

仓库自带 **模拟企业制度短文 + 问句**（[`datasets/seed_corpus.json`](datasets/seed_corpus.json)），与 [`datasets/example.json`](datasets/example.json) 中的问句一致。你**无需手填** `goldDocumentId` / `goldChunkIndex`，只需在本地服务已启动、账号可访问目标组时执行：

```powershell
$env:DONG_RAG_BASE_URL="http://localhost:8080"
$env:DONG_RAG_TOKEN="<登录 token>"
python scripts/run_retrieval_eval.py seed-dataset
```

默认会：逐条调用 `POST /rag/ingest/text` 入库 → 轮询 `GET /rag/ingest/task/{jobId}` 至成功 → **覆盖写入** `datasets/example.json`（写入每条对应的 `goldDocumentId`，`goldChunkIndex` 固定为 **0**，适用于上述短文单块场景）。

若出现 **`40101 无该组访问权限`**：当前登录用户不是 **admin** 且未加入目标组。脚本默认会在入库前调用 `POST /group/join` 加入 `meta.defaultGroupId` / `--group-id` 指定的组（项目里加入组无需审批）。仍失败时请确认该 `groupId` 在库里存在，或改用你已加入的组：`python scripts/run_retrieval_eval.py seed-dataset --group-id <你的组ID>`。也可用 `--no-join-first` 关闭自动入组。

若 PostgreSQL 报 **`键值对(user_id)=(…)没有在表"users"中出现`**（或接口返回 **`50000` 加入组失败**）：Sa-Token 里仍是**旧用户 id**，但 `users` 表已因重建/迁移换了一批主键。请 **重新登录** 获取新 Token；若用 Redis 存会话，可清空对应 key 后再登录。

可选参数：`--group-id`、`--corpus`、`--out`、`--poll-timeout` 等（见 `python scripts/run_retrieval_eval.py seed-dataset -h`）。

token在redis中存储的，进行查看，不要通过F12检查查看，不准确

## 金标从哪里来（手动方式）

1. 管理端查看文档与分块信息，或查库表 `document_chunk` 的 `document_id`、`chunk_index`。
2. 用管理端「检索检测」页面临跑一两问，核对 `rankOfGold`，减少标错。

## 文件

| 文件 | 说明 |
|------|------|
| [`dataset.schema.json`](dataset.schema.json) | JSON Schema，可选校验 |
| [`datasets/seed_corpus.json`](datasets/seed_corpus.json) | 内置短文 + 问句，供 `seed-dataset` 入库 |
| [`datasets/example.json`](datasets/example.json) | 开箱即用问句；金标为 null 时请先运行 `seed-dataset` |
| [`datasets/example.template.json`](datasets/example.template.json) | 空结构模板，自建数据集时可复制 |

跑批结果默认写入 [`../results/`](../results/)（见脚本 `--out-dir`）。


--- 简历摘要 ---
自建金标检索评测集「dong-rag-eval-demo」共 6 条有效标注，Top-K=5，平均 Hit@1=0.33、Hit@K=1.00、MRR=0.6111。离线/测试环境指标，简历中建议注明数据来源与日期。  

自建小样本金标集验证混合检索：Top-5 内金标召回率 100%（MRR≈0.61），保障 RAG 证据池覆盖。