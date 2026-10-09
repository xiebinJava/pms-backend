# 故事节点专属工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为已存在的 7 节点故事流程模板（`wf-5f5496d0ab8d46c29f5ff737bdf26e79`，当前 4 版）的每个节点配置专属工作台，实现方式与项目/专题/需求对齐；故事级测试结果汇总到专题；配置完成后固化为系统默认，使清库重建后工作台不丢失。

**Architecture:** 复用 v2 `contentOrder` + `componentConfigs` + 字段绑定，照搬需求侧"每节点专属入口、共用运行时组件"模式。`story-node-workbench` 承载 6 个节点结构化变体（写卡/迭代/开发/验收/发布/上线），`story-testing` 独立承载「测试中」的结构化测试记录。后端对每个 variant 做严格白名单校验再合并，placement 按节点名强校验（与需求一致）；故事测试状态**读时聚合**到专题测试面板。

**Tech Stack:** Vue 3 + TypeScript/JavaScript tests + Ant Design Vue；Spring Boot + Java records；Node test runner；Maven。

## 决策记录（已确认）

- 工作台形态：**对齐需求——每节点结构化变体**（一个 `story-node-workbench` 组件 + 6 variant；`story-testing` 独立组件）。
- 后端合并：**严格白名单 policy**（与 `TopicDevelopmentTestingPolicy` 同规格）。
- 测试结果关系：**故事汇总到专题**（读时聚合，只读展示）。
- placement：**A. 按节点名强校验**（与需求一致；节点改名将导致发布失败）。
- Task 3 复用方式：**新建独立 `story-testing-results.mjs`**，不动正在工作的专题测试模块。

## Global Constraints

- 节点字段区仍是字段唯一数据源，工作台只放结构化记录，不复制字段编辑模型。
- 后端严格白名单：未知字段丢弃，长度/枚举/URL 校验；**policy 层不做"必填"**（避免阻塞自动保存），必填若需要则放节点完成门禁。
- 节点完成与故事状态解耦：工作台记录与节点完成都不自动改故事状态。
- 测试汇总为读时聚合，不改写专题手动字段，不新增写时同步。
- `story-node-workbench` / `story-testing` 只允许 `story-management` 流程；通用组件不放给 story。
- 老库不覆盖：种子只在无 PUBLISHED 版本时生效；固化写出的 JSON 必须提交 git。
- 第 1 个节点（故事写卡）也要有专属工作台。

## Review Focus

- 6 个 variant 的白名单/长度/枚举/URL 校验分别生效，未知字段不落库。→ Task 1/5。
- placement 按节点名强校验，variant 与节点名一致；错挂被拦住。→ Task 1。
- 调色板对 story 生效，且按节点只显示 own 入口（applicable）。→ Task 2。
- 故事详情页真正渲染两类工作台。→ Task 3。
- 故事测试汇总为只读聚合，专题手动字段不被覆盖。→ Task 4。
- 固化 JSON 能在清库后完整恢复 7 节点工作台，且提交的 JSON 被 CI 校验。→ Task 7。

---

### Task 1: 后端组件键、校验、placement 与状态合并

**Files:**
- Modify: `pms-backend/src/main/java/com/brad/pms/workflow/WorkflowComponentKey.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java`
- Create: `pms-backend/src/main/java/com/brad/pms/workflow/StoryNodeWorkbenchPolicy.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Test: `pms-backend/src/test/java/com/brad/pms/workflow/StoryNodeWorkbenchPolicyTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceNodeEditTest.java`

**Interfaces:**
- `WorkflowComponentKey.STORY_NODE_WORKBENCH = "story-node-workbench"`、`STORY_TESTING = "story-testing"`。
- `StoryNodeWorkbenchPolicy.validate(JsonNode state, String variant)` 与 `merge(JsonNode existing, JsonNode incoming, String variant)`；variant 取自 `componentConfigs['story-node-workbench'].variant`。
- 白名单（均为**可选**；TEXTAREA ≤2000、TEXT ≤200/1000、DATE `YYYY-MM-DD`、URL 仅 http/https 无 userInfo）：

| variant | 可写字段 |
|---|---|
| writing | `acceptanceCriteria`、`background` |
| iteration | `iterationName`、`meetingNote`、`dependencies` |
| development | `implementationNote`、`selfTestResult`、`codeLink`(URL) |
| acceptance | `acceptanceConclusion`、`acceptanceNote` |
| release | `releaseVersion`、`releaseWindow`、`releaseNote` |
| launch | `launchDate`(DATE)、`launchVerification`、`retrospective` |

- placement（按节点名强校验）：variant ↔ 节点名包含关系 `writing→写卡 / iteration→迭代 / development→开发 / acceptance→验收 / release→发布 / launch→上线`；`story-testing` 节点名包含`测试`；`config.variant` 必须与节点名推导结果一致，否则发布失败。
- `story-testing` 复用 `TopicDevelopmentTestingPolicy.validate/merge`。

- [ ] **Step 1: 写失败测试**：两 key 的 SUPPORTED/processType；policy 各 variant 合法/超长/非法枚举/URL/未知字段丢弃/服务端历史保留；placement 错挂与 variant 不一致被拒；`story-testing` 配置。
- [ ] **Step 2: 跑聚焦测试确认失败**（`mvn -o -Dtest=StoryNodeWorkbenchPolicyTest,WorkflowTemplateDefinitionValidatorTest test`）。
- [ ] **Step 3: 实现**：加 key；`SUPPORTED_COMPONENTS` 加两 key；`validateForProcessType` 限 story-management；`validateComponentConfigs` 校验 `story-node-workbench`（`nodeKey/nodeName/variant/purpose/activities`，variant 枚举）与 `story-testing`（`testingResultsEnabled` 布尔）；`validateStoryWorkbenchPlacement`（按节点名强校验 + variant 一致）；`StoryNodeWorkbenchPolicy`；`preserveComponentValues` 接入两分支（**`definition==null` 或 variant 缺失时安全跳过**，不改动现有 topic/requirement 分支）。
- [ ] **Step 4: 跑 NodeEdit/集成相关测试确认通过。**（必填校验暂不加；如需，后续在 `completeNode` 增加，见"后续"。）
- [ ] **Step 5: Commit** `feat: validate story node workbenches`。

### Task 2: 前端注册表、调色板与节点蓝图

**Files:**
- Modify: `pms-front/src/components/workflow/workflow-component-registry.ts`
- Modify: `pms-front/src/views/admin/workflows/workflow-template-model.mjs`
- Modify: `pms-front/src/views/admin/workflows/index.vue`
- Modify: `pms-front/src/types/workflow.ts`
- Modify: `pms-front/src/locales/zh-CN.ts` / `en-US.ts`（`admin.workflow.componentLabels/componentHints.*`）
- Create: `pms-front/src/components/workflow/story-node-workbench.mjs`
- Test: `pms-front/src/components/workflow/story-node-workbench.test.mjs`
- Test: `pms-front/src/views/admin/workflows/workflow-template-model.workbench.test.mjs`

**Interfaces:**
- 注册 `story-node-workbench`、`story-testing`（`processTypeCodes:['story-management']`、`workbenchTypes:['story']`）。
- `STORY_WORKBENCH_PALETTE`：7 入口（`story-writing/iteration/development/testing/acceptance/release/launch-workbench`），testing 用 `story-testing`，其余 `runtimeKey:'story-node-workbench'`。
- `getStoryNodeWorkbenchBlueprint(node)` / `createStoryNodeWorkbenchConfig(node)` → `{nodeKey,nodeName,variant,purpose,activities}`；variant 由节点名推导（与后端 placement 一致）。
- `getAvailableWorkflowComponents`：`candidates` 支持 story palette；registry 过滤放行两 key；story 的 `applicable` 按节点名匹配 own 入口。
- `index.vue` 按 `workflowSource` 传对应 palette（不再硬编码需求 palette）。

- [ ] **Step 1: 写失败测试**：每节点 blueprint variant/purpose/activities；story palette 每节点仅 own 入口 applicable=true；registry 放行；未匹配节点时安全回退（不报错、不串用）。
- [ ] **Step 2: 跑 `node --test` 确认失败。**
- [ ] **Step 3: 实现蓝图、registry、palette 与 applicable、index.vue 传参。**
- [ ] **Step 4: 补 `componentLabels/componentHints` 双语 i18n（覆盖 7 个入口 key）。**
- [ ] **Step 5: 跑聚焦前端测试通过。**
- [ ] **Step 6: Commit** `feat: add story workbench palette and bindings`。

### Task 3: 前端组件、详情页渲染与预览

**Files:**
- Create: `pms-front/src/views/development/detail/StoryNodeWorkbenchComponent.vue`
- Create: `pms-front/src/views/development/detail/story-testing-results.mjs` / `.d.mts`
- Create: `pms-front/src/views/development/detail/StoryTestingResultsWorkbench.vue`
- Modify: `pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Modify: `pms-front/src/components/workflow/WorkflowWorkbenchPreview.vue`
- Modify: `pms-front/src/locales/zh-CN.ts` / `en-US.ts`（6 变体字段文案 + story-testing 文案）
- Test: `pms-front/src/views/development/detail/story-node-workbench.visual.test.mjs`
- Test: `pms-front/src/views/development/detail/story-testing-results.test.mjs`

**Interfaces:**
- `StoryNodeWorkbenchComponent`：按 `componentConfigs.variant` 渲染 6 变体；`disabled/preview` 只读；`update:modelValue` + `commit`。
- `story-testing-results.mjs`：**独立模块**（复制并改名 topic 模块，组件键 `story-testing`），不改动 `topic-testing-results.mjs`。
- 详情页新增 `itemType==='story'` 分支挂载两组件；预览组件新增 story 分支。

- [ ] **Step 1: 写失败的可视化/契约测试**（story 分支存在、两组件注册、variant 渲染、只读态、story-testing 纯函数）。
- [ ] **Step 2: 跑聚焦测试确认失败。**
- [ ] **Step 3: 实现组件 + 独立纯函数模块 + 详情页/预览分支。**
- [ ] **Step 4: 补 6 变体字段与 story-testing 双语 i18n。**
- [ ] **Step 5: 跑前端全量 `pnpm test`/`typecheck`。**
- [ ] **Step 6: Commit** `feat: render story node workbenches`。

### Task 4: 故事测试结果汇总到专题（读时聚合）

**Files:**
- Modify: `pms-backend/src/main/java/com/brad/pms/dto/response/DevelopmentTopicStoryDTO.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/service/DevelopmentStoryManagementService.java`
- Modify: `pms-front/src/views/development/detail/TopicTestingResultsWorkbench.vue`
- Modify: `pms-front/src/locales/*`（汇总文案）
- Test: `pms-backend/src/test/java/com/brad/pms/service/DevelopmentStoryManagementServiceTest.java`
- Test: `pms-front/src/views/development/detail/topic-testing-results.test.mjs`

**Interfaces（聚合口径已明确）:**
- 范围：专题下**全部故事**（按 `topic_id`），不限当前节点。
- 数据：每个故事的 story workflow（`item_type='STORY'`, `item_id=storyId`）中**节点名含"测试"**的节点的 `field_values_json.__components['story-testing'].testStatus`。
- 批量：按 storyIds 查 workflows，再按 workflowIds 查 nodes，内存匹配，避免 N+1；无 workflow/无 story-testing 的故事 `testStatus=null`。
- 输出：`DevelopmentTopicStoryDTO` 增 `buildVersion`、`testStatus`。
- 前端：只读「故事测试汇总」（通过/未通过/测试中/未开始/未记录 计数 + 总体状态），不改写专题手动字段。

- [ ] **Step 1: 写失败测试**（汇总计数、空故事、故事无 workflow、故事无 story-testing 边界）。
- [ ] **Step 2: 实现 DTO/批量查询/面板 + i18n。**
- [ ] **Step 3: 跑聚焦前后端测试通过。**
- [ ] **Step 4: Commit** `feat: aggregate story testing into topic results`。

### Task 5: 后端持久化集成测试（真实 MySQL）

**Files:**
- Create: `pms-backend/src/test/java/com/brad/pms/service/StoryNodeWorkbenchIntegrationTest.java`

**Interfaces:**
- 照 `TopicDevelopmentTestingIntegrationTest` 模式：模板含某 variant 的 story-node-workbench + story-testing → 保存节点 → 断言白名单生效、历史保留、字段/任务/其他节点与模板版本不动。

- [ ] **Step 1: 写集成测试。**
- [ ] **Step 2: 用 colima Docker 运行 `mvn test` 验证通过。**
- [ ] **Step 3: Commit** `test: cover story workbench persistence`。

### Task 6: 浏览器 E2E

**Files:**
- Create: `pms-front/tests/workflows/story-workbench.spec.mjs`
- Create: `pms-front/tests/workflows/story-workbench.config.mjs`

**Interfaces:**
- 照 `topic-testing-results.spec.mjs`：mock API，story 节点填写某 variant → 保存 → 刷新回填 → 服务端历史保留 → 不触发节点完成；无组件模板不渲染；只读态禁用。

- [ ] **Step 1: 写 spec + config，固定 `pms.locale=zh-CN`。**
- [ ] **Step 2: 本地跑通过（真实 Chrome）。**
- [ ] **Step 3: Commit** `test: cover story workbench e2e`。

### Task 7: 配置、固化为系统默认、清库不丢

**前提**：Task 1-6 完成，组件已在代码中可用。

**Files:**
- Create/Modify: `pms-backend/src/main/resources/workflow-defaults/story-management.json`
- Test: `pms-backend/src/test/java/com/brad/pms/config/StoryWorkflowDefaultResourceTest.java`（新增：直接读取真实 JSON 并校验）
- Test: `pms-backend/src/test/java/com/brad/pms/config/WorkflowTemplateSeedRunnerTest.java`

**Interfaces:**
- 配置来源：以固定定义（脚本/接口）写入 7 节点工作台，避免手工点击不可重复；定义须通过 `validateForPublish` 与 `validateSourceNodeKeys`。
- 固化：`POST /admin/workflow-config/project-types/{id}/default-template/system-default` 写出 JSON（环境需 local/dev + `PMS_WORKFLOW_DEFAULT_WRITE_ENABLED=true`）。

- [ ] **Step 1: 准备 7 节点工作台定义并发布/设为默认**（脚本或接口；7 节点的 contentOrder + componentConfigs 齐备）。
- [ ] **Step 2: 固化写出 `story-management.json` 并提交 git。**
- [ ] **Step 3: 新增真实 JSON 校验测试**：读取 `workflow-defaults/story-management.json` → `validateForPublish('story-management', def)` 通过 → 7 节点各含对应工作台配置。
- [ ] **Step 4: 新增/扩展种子往返测试**：清库上下文 → `WorkflowTemplateSeedRunner` 播种 → 断言 story 默认模板含 7 节点工作台；断言旧库不被覆盖。
- [ ] **Step 5: 确认发布/固化触发的 `synchronizeMountWorkbenchDrafts` 对专题草稿影响符合预期。**
- [ ] **Step 6: Commit** `feat: solidify story workflow system default`。

### Task 8: 整分支复核与验证

- [ ] **Step 1: 前端 `pnpm test`/`typecheck`/`build`；后端全量 `mvn test`；`git diff --check`。**
- [ ] **Step 2: 检查 diff**：无通用组件误放给 story、无硬编码需求 palette 残留、story 分支完整、无调试残留。
- [ ] **Step 3: 浏览器验证**：设计器 7 节点候选、故事详情页渲染、专题测试汇总、移动端无溢出。
- [ ] **Step 4: 记录偏差与遗留；无新鲜命令输出不宣称完成。**

## 已知技术债（不在本功能范围）

- `requirement-node-workbench` / `topic-research` / `topic-design-review` 后端合并为无白名单整块 `deepCopy`，另行记录改进。
- 工作台字段的"必填"当前不校验；如需要，在 `completeNode` 增加完成门禁（本计划暂不做）。

## 依赖与顺序

- Task 1、2 为前置（阻断项）；Task 3 依赖 2；Task 4 依赖 1；Task 5 依赖 1；Task 6 依赖 3；Task 7 依赖 1-3。
- Task 7 第 1 步为受控配置（脚本/接口），非手工点击；固化产物必须提交 git 并受 CI 校验。
