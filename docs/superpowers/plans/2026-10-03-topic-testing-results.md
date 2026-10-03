# 专题测试结果工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在专题「开发与测试」节点的故事列表工作台下方，按模板配置启用「测试结果与遗留问题」记录区：构建版本、测试状态、报告链接、逐条遗留问题；同时修复节点保存版本基线被刷新覆盖的并发问题。

**Architecture:** 复用 v2 `contentOrder` 与 `componentConfigs` 机制。模板节点 `story-list` 组件的 `componentConfigs.testingResultsEnabled`（严格布尔）作为功能开关；后端在 `preserveComponentValues` 中用白名单策略合并测试记录，未知字段一律丢弃、历史元数据以服务端为准；前端用纯函数模块做表单与 `__components` 存储的双向转换。测试记录不构成节点完成门禁（`completeNode` 不校验）。

**Tech Stack:** Vue 3 + TypeScript/JavaScript tests + Ant Design Vue；Spring Boot + Java records；Node test runner；Maven。

## Global Constraints

- 旧模板/已发布版本没有 `testingResultsEnabled` 时行为完全不变；已有专题保留原流程版本。
- 开关必须是严格布尔：字符串 `"true"`、缺失、null 均视为关闭。
- 测试记录只允许写入白名单字段：`buildVersion`、`testStatus`、`reportUrl`、`residualIssues`；其余字段丢弃。
- 遗留问题条目必须带唯一非空 id，最多 100 条，说明最长 2000 字符。
- 报告链接必须是 http/https 且不允许 userInfo（防 `javascript:` 等注入）。
- 记录不会自动完成节点；前端提示与后端门禁行为一致。
- 内置专题模板（`workflow-defaults/topic-management.json` v11）默认启用该工作台；已有数据库不自动升级（种子仅在无 PUBLISHED 版本时生效）。

## Review Focus

- 旧配置不隐式启用新工作台。→ Task 1/2。
- 服务端白名单合并：未知 incoming 属性不落库，服务端历史字段不被请求覆盖。→ Task 1。
- 并发保存：详情/任务刷新不得推进表单保存基线，否则会静默覆盖他人编辑。→ Task 3。
- 内置模板默认开启且版本号 bump 为 11。→ Task 4。

### Task 1: 后端测试记录白名单策略与保存接线

**Files:**
- Create: `pms-backend/src/main/java/com/brad/pms/workflow/TopicDevelopmentTestingPolicy.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Test: `pms-backend/src/test/java/com/brad/pms/workflow/TopicDevelopmentTestingPolicyTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceNodeEditTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/service/TopicDevelopmentTestingIntegrationTest.java`

- [x] **Step 1: Policy 校验与合并**（校验 4 字段白名单、长度/枚举/URL/遗留问题上限；merge 保留服务端历史、按 id 合并遗留问题）。
- [x] **Step 2: 接入 `preserveComponentValues`**（仅当 runtimeComponents 含 story-list 且配置为严格布尔 true 时合并）。
- [x] **Step 3: 单测**：PolicyTest 9 例、NodeEditTest 新增 4 例（非法链接、旧配置忽略、字符串配置不启用、未知属性丢弃且保留服务端历史）。
- [x] **Step 4: 集成测试**：真实 MySQL 往返，验证故事/任务/其他节点数据与模板版本不动。

### Task 2: 前端工作台、设计器开关与预览

**Files:**
- Create: `pms-front/src/views/development/detail/topic-testing-results.mjs` / `.d.mts` / `.test.mjs`
- Create: `pms-front/src/views/development/detail/TopicTestingResultsWorkbench.vue`
- Modify: `pms-front/src/views/development/detail/DevelopmentStoryListComponent.vue`
- Modify: `pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Modify: `pms-front/src/views/admin/workflows/index.vue`
- Modify: `pms-front/src/components/workflow/WorkflowWorkbenchPreview.vue`
- Modify: `pms-front/src/locales/zh-CN.ts` / `en-US.ts`

- [x] **Step 1: 纯函数模块**（isTestingResultsEnabled / configureTopicTesting / normalizeTopicTesting / mergeTopicTesting，严格布尔、不共享可变对象）。
- [x] **Step 2: 工作台组件**（构建版本、测试状态、报告链接、遗留问题增删；readOnly/preview 禁用；blur/change 提交）。
- [x] **Step 3: 详情页接线**（modelValue 双向 + commit 触发 saveNode；测试状态由服务端 `__components` 持久化）。
- [x] **Step 4: 模板设计器开关与预览**（topic-management + story-list 时显示复选框；预览分支渲染真实组件禁用态）。

### Task 3: 节点保存版本基线并发修复

**Files:**
- Modify: `pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Test: `pms-front/src/views/development/detail/node-save-concurrency.test.mjs`

- [x] **Step 1: 引入 `nodeFormVersion` 表单基线**（只在自身保存成功后推进；任务/详情刷新不得改动基线）。
- [x] **Step 2: 并发测试**（对等编辑刷新后保留脏表单与旧版本、串行保存排队推进基线）。

### Task 4: 内置模板默认开启与可视化测试

**Files:**
- Modify: `pms-backend/src/main/resources/workflow-defaults/topic-management.json`
- Create: `pms-front/src/views/development/detail/topic-testing-results.visual.test.mjs`
- Modify: `pms-front/src/components/workflow/workflow-renderer.visual.test.mjs`（如需要）

- [x] **Step 1: 内置模板 v11**（开发与测试节点 `componentConfigs.story-list.testingResultsEnabled=true`；已有库不自动升级）。
- [ ] **Step 2: 可视化测试**：工作台渲染（默认态/回填态/禁用态）、增删遗留问题、预览分支的 story-list 渲染。

### Task 5: 全量验证与提交

- [ ] **Step 1: 前端全量**：`pnpm test`、`pnpm typecheck`、`pnpm build`。
- [ ] **Step 2: 后端相关**：Policy/NodeEdit 单测；MySQL 集成测试（有 Docker 环境时）。
- [ ] **Step 3: 分两个 commit 提交**：`feat: add topic testing results workbench`、`fix: keep node save baseline on peer refresh`。