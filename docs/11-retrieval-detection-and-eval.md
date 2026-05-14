# 11 检索检测与编排评测

## 11.1 检索检测

- **路径**：`POST /rag/detect/retrieval`
- **权限**：`@SaCheckRole("admin")` + `checkGroupReadable`
- **请求**：`RetrievalDetectRequest`：`groupId`、`topK`、`cases[]`；可选 **`includeRerankComparison`**（在开启 `retrieval-rerank-enabled` 时对比重排前后金标 rank / MRR）。
- **响应**：`RetrievalDetectResponseVO`：`caseCount`、`labeledCount`、`meanHitAt1`、`meanHitAtK`、`mrr`、可选基线汇总、每条 `details`（证据列表正文截断约 500 字）。

**指标定义**：

- `rankOfGold`：金标 `(documentId, chunkIndex)` 在 `hybridRetrieve(..., applyRerank=true)` 结果列表中**首次出现**的 1-based 位置。
- `hitAt1` / `hitAtK` / `reciprocalRank`：由 rank 与请求 `topK` 推导；未命中 RR 为 0。

实现：`RetrievalDetectionServiceImpl`。

## 11.2 管理端页面

- 路由：`/retrieval-detect`
- 组件：`admin-frontend/src/pages/RetrievalDetectPage.vue`
- API：`ragDetectApi.detectRetrieval`

## 11.3 编排评测（助手）

- **路径**：`POST /assistant/eval/complaint?groupId=&templateId=`
- **实现**：`ComplaintEvaluationService`，样例问题集分投诉模板与内部多专家模板，输出 `handoffRate`、`avgSubTaskCount`、`details`。
- **页面**：`/complaint-eval`（`ComplaintEvalPage.vue`）。

## 11.4 与混合检索实现的关系

检测服务直接调用 `HybridRetrievalService`，与线上 `KB_SEARCH`、RAG 使用同一套检索核心，便于离线对比调参。

## 11.5 命令行跑批（评测集 + 简历摘要）

仓库提供 **Python 3 标准库** 脚本，读取金标 JSON、分批调用 `POST /rag/detect/retrieval`，按 `labeledCount` **加权合并**各批的 mean Hit@1 / mean Hit@K / MRR（及可选重排基线），并写出结果 JSON 与中文 `resume-snippet` 文本。

- **脚本**：[scripts/run_retrieval_eval.py](../scripts/run_retrieval_eval.py)
- **数据集说明与 Schema**：[eval/retrieval/README.md](../eval/retrieval/README.md)、[eval/retrieval/dataset.schema.json](../eval/retrieval/dataset.schema.json)
- **示例模板**：[eval/retrieval/datasets/example.template.json](../eval/retrieval/datasets/example.template.json)
- **默认输出目录**：`eval/results/`（可用 `--out-dir` 修改）

### 环境变量

| 变量 | 含义 |
|------|------|
| `DONG_RAG_BASE_URL` | 后端根地址，如 `http://localhost:8080`（无尾部 `/`） |
| `DONG_RAG_TOKEN` | **admin** 账号登录后 Sa-Token，与前端 `Authorization` 请求头一致 |

也可用命令行 `--base-url`、`--token` 覆盖。

### 检索评测

```powershell
# Windows PowerShell 示例
$env:DONG_RAG_BASE_URL="http://localhost:8080"
$env:DONG_RAG_TOKEN="<登录返回的 token>"
python scripts/run_retrieval_eval.py retrieval --dataset eval/retrieval/datasets/my.json
```

常用参数：`--batch-size`（默认 20，避免单次请求过大超时）、`--timeout`（单请求秒数）、`--out-dir`。

在同一轮结果中**附带**助手编排评测（写入同一 JSON 的 `assistantEval`，并在简历摘要中多一行）：

```powershell
python scripts/run_retrieval_eval.py retrieval -d eval/retrieval/datasets/my.json `
  --assistant-group-id 1 --assistant-template-id INTERNAL_KB_MULTI
```

### 自动生成金标评测集（`seed-dataset`）

内置语料 [`eval/retrieval/datasets/seed_corpus.json`](../eval/retrieval/datasets/seed_corpus.json) 含多条短文及对应问句。执行：

```powershell
python scripts/run_retrieval_eval.py seed-dataset
```

会逐条 `POST /rag/ingest/text` 入库并轮询任务完成后，**覆盖写入** [`eval/retrieval/datasets/example.json`](../eval/retrieval/datasets/example.json)（填充 `goldDocumentId`，`goldChunkIndex` 默认为 0）。再运行 `retrieval` 子命令即可得到 Hit@K / MRR。详见 [`eval/retrieval/README.md`](../eval/retrieval/README.md)。

### 仅助手编排评测

```powershell
python scripts/run_retrieval_eval.py assistant --group-id 1
python scripts/run_retrieval_eval.py assistant --group-id 1 --template-id COMPLAINT_MULTI_LEGACY
```

输出：`assistant-eval-<groupId>-<时间戳>.json` 与 `resume-snippet-assistant-*.txt`。

## 实现思路与技术要点

- **与线上一致的核心**：`RetrievalDetectionServiceImpl` 直接调用 `HybridRetrievalService`，保证检测页、脚本批跑与真实 `KB_SEARCH`/问答使用同一套融合与重排逻辑，避免「离线很好、线上不对」。
- **金标指标**：以 `(documentId, chunkIndex)` 首次命中位置定义 rank，再推导 Hit@1、Hit@K、MRR；定义简单可自动化，便于对比调参前后 JSON 结果。
- **可选重排对比**：`includeRerankComparison` 在开启重排时输出前后 rank，用于判断「重排是否整体有益」还是仅对部分 query 有效。
- **管理端表单**：`RetrievalDetectPage.vue` 将 JSON cases 提交给后端，降低非研发使用门槛；admin 角色 + `checkGroupReadable` 防止跨组探测。
- **Python 脚本**：仅用标准库减少环境摩擦；分批请求避免超大 body 与超时；加权合并各批均值保持与单批大请求可比的统计口径；`seed-dataset` 自动化生成金标缩短冷启动成本。

上一篇：[10-observability-resilience-and-config.md](10-observability-resilience-and-config.md)  
下一篇：[12-database-flyway-and-troubleshooting.md](12-database-flyway-and-troubleshooting.md)
