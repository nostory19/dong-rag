# 00 项目代码规范与工程约定

本文档汇总 **Dong RAG** 仓库内后端（Spring Boot）、前端（Vue 3）与脚本侧应共同遵守的约定，便于协作与 Code Review。具体模块的设计动机见各专题文档末尾的 **「实现思路与技术要点」**。

---

## 0.1 版本管理与提交信息

- 提交信息遵循 **Conventional Commits**：`feat:`、`fix:`、`docs:`、`refactor:`、`chore:`、`test:` 等；主题用英文或中文均可，但需**一句话说清变更意图**。
- 单次提交尽量**原子化**（一个意图一个 commit），避免把无关重构与功能混在同一 diff 中。

---

## 0.2 后端（Java / Spring Boot）

### 包与分层

- **`controller`**：只做参数绑定、鉴权注解、调用 `service`，不写业务分支与持久化细节。
- **`service` / `service.impl`**：用例编排、事务边界、与多数据源（DB / Redis / MinIO / ES / Vector）的协调。
- **`mapper` / `repository`**：数据访问；避免在 Mapper 中堆叠复杂业务判断。
- **`config`**：Bean 装配、与 `application*.yml` 绑定的 `@ConfigurationProperties`（如 `DongragAiProperties`、`AssistantProperties`）。
- **`assistant.*`、`rag.*`**：领域子包，按职责再分子包（`orchestrator`、`tool`、`dialogue` 等），新增能力优先**扩展现有抽象**而非复制粘贴一整条流水线。

### 配置与常量

- **禁止魔法数/魔法字符串**散落在业务代码中：可配置项放入 `application*.yml` 或对应的 `@ConfigurationProperties`；纯常量用 `private static final` 或小型常量类，并注明含义。
- 环境相关密钥优先 **环境变量** 或本地覆盖文件（勿将真实密钥提交仓库）。

### 命名

- 类名 **PascalCase**，方法/字段 **camelCase**；布尔含义用 `is`/`has`/`can` 前缀。
- 避免无意义命名（如 `data1`、`handler2`、`cursor` 作变量名）；集合用复数或 `xxxList`/`xxxById` 等可读后缀。

### 函数与文件体量

- **单函数建议 ≤ 80 行**；超过时拆私有方法或独立组件类，保持「一个函数一件事」。
- **单文件建议 ≤ 2000 行**；逼近上限时按职责拆文件（与现有 `assistant` 子包风格一致）。

### 异常与日志

- 业务可预期失败用**明确异常或统一错误响应**，禁止 `catch` 后空吞或只打 debug。
- 日志：**关键结果与错误**必打；排查向可适当加密（如 traceId、jobId、documentId），避免刷屏同一循环内无差别的 INFO。
- 助手与入库等长链路代码路径上，已有 Micrometer Timer 的模块，新增阶段耗时优先考虑**指标**而非仅 println。

### 测试

- **核心业务路径**（鉴权边界、组隔离、检索门控、入库状态迁移等）应有单元或集成测试；目标为关键模块可回归，而非追求形式覆盖率数字。
- 测试数据与 SQL 草稿若仅本地使用，放在仓库约定的**临时目录**（见 0.5），勿混入 `src/main`。

---

## 0.3 前端（Vue 3）

- **组合式 API** + TypeScript（若页面已用 TS 则新代码保持一致）；组件按「页面 / 布局 / 通用组件」分目录。
- HTTP：统一经封装的 `request`/`api` 层，**不在组件内散落** `axios` 默认实例配置。
- 鉴权头与 `localStorage`/`sessionStorage` 键名与后端约定一致（用户端与管理端分离，见 13 / 14 文档）。
- NDJSON 流式消费：**按行解析**，对 `done`/`error` 做终结处理，避免泄漏 `ReadableStream`。

---

## 0.4 脚本与评测（Python 等）

- 评测与批跑脚本放在 **`scripts/`**；数据集与 schema 放在 **`eval/`** 下对应子目录；批跑输出默认 **`eval/results/`**（可通过参数改路径）。
- 脚本应支持**环境变量与命令行参数**两种方式传入 baseUrl、token，便于 CI 与本机。

---

## 0.5 临时文件与 .gitignore

- 一次性 SQL、说明草稿、实验性导出等，放在仓库根下 **`临时/`**（或团队统一约定的临时目录），**不要**提交到 `src/main` 或 `docs` 正文。
- 日志目录、虚拟环境、IDE 私有文件、评测生成物等必须已在 **`.gitignore`** 中排除（随新增工具链及时补充）。

---

## 0.6 文档维护

- **根 README**：保持「大纲 + 速查」，避免与 `docs/` 长篇重复；新增专题时在 `docs/README.md` 登记。
- **专题文档（01–14）**：描述「是什么、怎么配」；**实现动机与关键代码路径**写在各篇末尾 **「实现思路与技术要点」**，与本文 0.2–0.4 互补。

---

## 0.7 安全与多租户

- 凡涉及 `groupId` 的接口：**先登录 → 再校验组可读/可写**，并在向量 metadata、ES filter、SQL 条件中**一致**落实，不依赖前端传的「信任」。
- 助手 `conversationId` 必须校验归属，防止水平越权（见 [08-assistant-session-dialogue.md](08-assistant-session-dialogue.md)）。

---

下一篇：[01-project-overview.md](01-project-overview.md)  
[返回文档中心](README.md)
