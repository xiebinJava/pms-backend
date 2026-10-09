# 需求节点专属工作台 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将需求流程 Demo 固化为可绑定、可运行的节点专属工作台，并恢复需求上线的发布版本字段，最后移除 Demo。

**Architecture:** 复用现有 v2 `contentOrder` 与 `componentConfigs`。新增一个受需求流程限制的节点工作台组件，由节点蓝图生成配置；模板编辑器按当前节点只展示适用候选，需求详情页按运行时组件渲染。保留现有需求接收分析和需求执行目标组件作为两个真实业务工作台。

**Tech Stack:** Vue 3 + TypeScript/JavaScript tests + Ant Design Vue；Spring Boot + Java records；Vitest/Node test runner；Maven。

**Spec:** `docs/superpowers/specs/2026-09-29-requirement-node-workbenches.md`

## Global Constraints

- 节点字段区仍是字段唯一数据源，工作台不得复制第二套字段编辑模型。
- 第一个“需求录入”节点不显示节点专属工作台候选。
- 需求接收继续使用 `requirement-receiving-analysis`，需求开发继续使用 `requirement-execution`。
- 需求上线只恢复“发布版本”必填单行文本，不恢复上线检查、回滚方案、上线结论。
- Demo 入口、本地草稿和 Demo 组件在最终结果中必须移除。
- 已有模板节点、字段、组件配置和发布版本语义必须保持兼容。

## Review Focus

- 节点名称变化或自定义节点：没有匹配蓝图时应安全回退为通用节点工作台，不能报错或串用其他节点配置。→ Task 1/2。
- 需求录入节点：不应出现节点专属工作台候选。→ Task 2。
- 需求接收与需求开发：只能出现各自既有真实业务组件，不能再出现通用节点工作台。→ Task 2/3。
- 已绑定节点组件：保存、加载、排序、删除时必须保留 `componentConfigs`，不能丢失工作台配置。→ Task 2。
- 旧模板/已发布版本：没有新组件时不自动污染节点；需求上线缺失字段时只补发布版本。→ Task 1/4。

### Task 1: 固化需求节点蓝图并恢复发布版本

**Files:**
- Create: `pms-front/src/components/workflow/requirement-node-workbench.mjs`
- Test: `pms-front/src/components/workflow/requirement-node-workbench.test.mjs`
- Modify: `pms-front/src/views/admin/workflows/workflow-template-model.mjs`
- Test: `pms-front/src/views/admin/workflows/workflow-template-model.test.mjs`
- Modify: `pms-front/src/views/admin/workflows/workflow-template-schema.mjs`
- Test: `pms-front/src/views/admin/workflows/workflow-template-schema.test.mjs`

**Interfaces:**
- Produces `REQUIREMENT_NODE_WORKBENCH_COMPONENT`, `getRequirementNodeWorkbenchBlueprint(node)`, `createRequirementNodeWorkbenchConfig(node)`, and `ensureRequirementReleaseVersionField(definition)`.
- The workbench config shape is `{ nodeKey, nodeName, purpose, activities, display? }`.

- [ ] **Step 1: Write the failing tests** for all final node blueprints, safe fallback, release field restoration, and schedule/integration labels.
- [ ] **Step 2: Run the focused tests and verify they fail** because the new blueprint helpers and release normalizer do not exist.
- [ ] **Step 3: Implement the blueprint module and normalization helpers.** Encode the confirmed activities and only the requested release field; preserve existing nodes and fields while adding `legacy-custom-fields` when needed.
- [ ] **Step 4: Run the focused frontend tests and verify they pass.**
- [ ] **Step 5: Commit** `feat: add requirement node workbench blueprints`.

### Task 2: Add template binding and remove Demo

**Files:**
- Modify: `pms-front/src/components/workflow/workflow-component-registry.ts`
- Modify: `pms-front/src/views/admin/workflows/index.vue`
- Modify: `pms-front/src/views/admin/workflows/workflow-template-model.mjs`
- Modify: `pms-front/src/views/admin/workflows/workflow-admin-visual.test.mjs`
- Delete: `pms-front/src/views/admin/workflows/RequirementWorkbenchDemo.vue`
- Delete: `pms-front/src/views/admin/workflows/requirement-workbench-demo.mjs`
- Delete: `pms-front/src/views/admin/workflows/requirement-workbench-demo.d.mts`
- Delete: `pms-front/src/views/admin/workflows/requirement-workbench-demo.test.mjs`
- Delete: `pms-front/src/views/admin/workflows/requirement-workbench-demo.visual.test.mjs`

**Interfaces:**
- Consumes Task 1 blueprint helpers.
- Produces node-specific candidate filtering and binding through `addWorkflowComponent(node, componentKey, config?)`.

- [ ] **Step 1: Write failing model/visual tests** proving per-node palette filtering, config persistence through add/remove/reorder, no candidate on entry, and no Demo import/button.
- [ ] **Step 2: Run focused tests and verify the expected failures.**
- [ ] **Step 3: Implement registry metadata and editor binding.** Keep generic project/topic/story workbench filters unchanged; requirement nodes expose only their own workbench candidate.
- [ ] **Step 4: Remove the Demo entry, component, localStorage migration and translations.** Keep shared blueprint logic in the new module.
- [ ] **Step 5: Run frontend tests and typecheck.**
- [ ] **Step 6: Commit** `feat: bind requirement workbenches to workflow nodes`.

### Task 3: Render the bound workbench in requirement details

**Files:**
- Create: `pms-front/src/views/development/detail/RequirementNodeWorkbenchComponent.vue`
- Test: `pms-front/src/views/development/detail/requirement-node-workbench.visual.test.mjs`
- Modify: `pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Modify: `pms-front/src/components/workflow/WorkflowRuntimeComponentHost.vue` only if needed for the slot contract.

**Interfaces:**
- Consumes `selectedNode.name`, `selectedNode.description`, `selectedNode.fields`, `selectedNode.componentConfigs`, and the Task 1 blueprint helper.
- Renders a concise workbench card; field editing remains in `DevelopmentItemWorkflowFields`, and activity completion remains in the existing task board.

- [ ] **Step 1: Write the failing visual/contract test** for the runtime component key, node title, activity list, and development target display hint.
- [ ] **Step 2: Run the focused test and verify it fails.**
- [ ] **Step 3: Implement the component and route `requirement-node-workbench` in the requirement detail page.**
- [ ] **Step 4: Run focused tests and the full frontend suite.**
- [ ] **Step 5: Commit** `feat: render bound requirement node workbenches`.

### Task 4: Validate and normalize the backend definition

**Files:**
- Modify: `pms-backend/src/main/java/com/brad/pms/workflow/WorkflowComponentKey.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionNormalizer.java`
- Test: `pms-backend/src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionNormalizerTest.java`

**Interfaces:**
- Consumes the frontend component key `requirement-node-workbench` and the existing v2 `componentConfigs` map.
- Produces backend acceptance for requirement-only node workbenches and runtime restoration of `release-version`.

- [ ] **Step 1: Write failing backend tests** for supported component, process-type restriction, config object validation, and release-version normalization.
- [ ] **Step 2: Run focused Maven tests and verify they fail.**
- [ ] **Step 3: Implement the component key, validator rule, and requirement release-field normalizer.** Unknown workbench config keys remain opaque only where existing validation permits them.
- [ ] **Step 4: Run focused backend tests and the complete backend test suite.**
- [ ] **Step 5: Commit** `feat: validate requirement node workbenches`.

### Task 5: Whole-branch review and verification

**Files:**
- Review all files changed by Tasks 1–4.

- [ ] **Step 1: Run frontend typecheck, frontend tests, backend tests, and `git diff --check`.**
- [ ] **Step 2: Inspect the final diff for Demo references, release-field behavior, and node-specific binding.**
- [ ] **Step 3: Build/restart the local services only if required by the existing workspace workflow, then verify the template editor and requirement detail page.**
- [ ] **Step 4: Record any deviations or remaining limitations; do not claim completion without fresh command output.**
