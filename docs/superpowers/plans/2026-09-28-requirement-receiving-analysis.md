# Requirement Receiving Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为需求流程实现一个可由流程模板配置的“需求接收与分析”复合组件，并把过滤、价值分析、通过/待补充/驳回、重新开启和历史模板兼容规则闭环落到后端、前端和测试中。

**Architecture:** 需求接收组件作为一个运行时组件注册到模板系统，内部再拆成概览、过滤、分析、结论四个 UI 区块。组件状态保存到当前流程节点 `fieldValuesJson.__components.requirement-receiving-analysis`，需求名称、描述、负责人、业务线和最终优先级继续使用现有字段绑定。后端提供组件状态保存、驳回、重新开启接口，并由统一策略校验器复用模板配置、节点完成和终态操作规则；前端详情页只负责交互和展示，不能绕过后端完成状态转换。

**Tech Stack:** Spring Boot / MyBatis-Plus / Flyway / JUnit 5；Vue 3 / TypeScript / Ant Design Vue / Vitest；现有 Playwright E2E 体系。

**Spec:** `docs/superpowers/specs/2026-09-28-requirement-receiving-analysis-design.md`

## Global Constraints

- 沿用当前工作区和 `release` 分支，不创建额外 worktree。
- 不复制需求主字段；最终优先级必须继续写入需求主表的 `priority`。
- 不改变现有需求只能绑定一个项目、专题或故事的既有模型。
- 组件保存必须携带节点 id 和节点版本，并且只合并保留命名空间，不能覆盖普通自定义字段。
- 已发布且不含组件的历史模板继续兼容；新建或编辑后发布的需求模板必须恰好含一个组件。
- `NEEDS_INFO` 只允许保存，不允许完成；`REJECTED` 是终态，必须事务性更新需求、工作流和审计记录。
- 每个任务先补失败测试，再写最小实现，最后运行该任务对应的验证命令。

## Task 1: 固化模板 schema、组件注册和配置规则

**Files:**

- Modify `src/main/java/com/brad/pms/workflow/WorkflowNodeDefinition.java`
- Modify `src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionNormalizer.java`
- Modify `src/main/java/com/brad/pms/service/WorkflowComponentBindingService.java`
- Modify `src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java`
- Modify `src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java`
- Modify `src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionNormalizerTest.java`
- Modify `/Users/fs/Desktop/Project/pms-front/src/types/workflow.ts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/components/workflow/workflow-component-registry.ts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-model.mjs`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-schema.mjs`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-model.d.mts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-schema.test.mjs`

**Steps:**

- [ ] Add `componentConfigs` to the v2 node definition as a component-keyed JSON map while keeping compatibility constructors and preserving the map through normalizer/binding-service rebuilds.
- [ ] Add `requirement-receiving-analysis` to the shared component registry and allow it only for `requirement-management`.
- [ ] Add a typed config shape for visible filter/analysis/decision sections, score visibility/required flags, analysis-conclusion required flag, and reject permission.
- [ ] Add schema validation for unknown config keys, invalid booleans, hidden-and-required contradictions, decision section disabled, wrong process type, duplicate component, and missing component on newly published/edited requirement templates. Preserve old published snapshots without auto-migration.
- [ ] Make the front model preserve `componentConfigs` on clone, content reorder, add/remove, and serialization; when the component is newly added, seed the default config.
- [ ] Add failing backend and frontend tests for one valid config, one invalid config, duplicate/missing component, old snapshot compatibility, and config preservation.
- [ ] Run `mvn -q -Dtest=WorkflowTemplateDefinitionValidatorTest,WorkflowTemplateDefinitionNormalizerTest test` and the focused frontend schema tests.

## Task 2: Add workflow terminal state and domain/API types

**Files:**

- Add `src/main/resources/db/migration/V61__requirement_receiving_analysis.sql`
- Modify `src/main/java/com/brad/pms/entity/DevelopmentItemWorkflowDO.java`
- Modify workflow mapper XML or annotations if required by the current project mapping style
- Modify `src/main/java/com/brad/pms/dto/DevelopmentItemWorkflowDetailDTO.java`
- Modify `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify requirement list/detail DTOs and services that expose requirement status or active source requirements
- Modify `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/api/development-item.ts`

**Steps:**

- [ ] Add nullable `terminal_status` to `pms_development_item_workflow` and verify existing rows remain null and requirements remain `ACTIVE`.
- [ ] Expose `terminalStatus` in workflow detail and add `REJECTED` to the frontend workflow/status unions.
- [ ] Ensure detail status computation returns rejected when the workflow is terminal, before ordinary node-based status calculation.
- [ ] Add API/types for component state read/save, reject, and reopen with node/version fields where applicable.
- [ ] Make active-source requirement queries exclude `REJECTED` while preserving direct historical detail and existing target relations; reopening makes the requirement selectable again.
- [ ] Add migration and DTO/list regression tests before implementation.
- [ ] Run the migration-focused tests and compile the backend/frontend type surfaces.

## Task 3: Implement the pure receiving-analysis policy and component state persistence

**Files:**

- Add `src/main/java/com/brad/pms/workflow/RequirementReceivingAnalysisState.java`
- Add `src/main/java/com/brad/pms/workflow/RequirementReceivingAnalysisConfig.java`
- Add `src/main/java/com/brad/pms/workflow/RequirementReceivingAnalysisPolicy.java`
- Add `src/main/java/com/brad/pms/service/RequirementReceivingAnalysisService.java`
- Add request/response DTOs under the existing DTO package convention
- Modify `src/main/java/com/brad/pms/controller/DevelopmentItemWorkflowController.java`
- Modify requirement controller for reject/reopen endpoints
- Add `src/test/java/com/brad/pms/workflow/RequirementReceivingAnalysisPolicyTest.java`
- Add `src/test/java/com/brad/pms/service/RequirementReceivingAnalysisServiceTest.java`

**Steps:**

- [ ] Define strict enums and state serialization for validity, filter reasons, category, decision, score values, calculated average, value conclusion, analysis conclusion, supplement note, and decision reason.
- [ ] Implement pure validation: score range 1–5, `OTHER` requires filter note, incomplete scores produce `PENDING` evaluation, validity/decision combinations are consistent, `NEEDS_INFO` requires supplement note, `REJECT` requires reason, and hidden sections do not participate in required checks.
- [ ] Implement average calculation with one decimal and thresholds 4.0–5.0 high, 2.5–3.9 medium, 1.0–2.4 low.
- [ ] Implement component read/save against the reserved namespace, preserving unrelated `fieldValuesJson` keys and enforcing current-node editability, permission, and optimistic version update.
- [ ] Resolve component config from the workflow snapshot rather than the current template so existing work items do not change behavior after a later publish.
- [ ] Add tests for malformed JSON, unknown enum, score boundaries, null/incomplete score, namespace merge, version conflict, completed-node read-only, and old-node-without-component behavior.
- [ ] Run focused policy/service tests.

## Task 4: Integrate completion, reject, reopen, permissions, and audit behavior

**Files:**

- Modify `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify `src/main/java/com/brad/pms/service/DevelopmentItemPermissionService.java` only where the existing permission abstraction requires it
- Modify requirement status service/entity constants and audit/log services
- Add or modify backend integration tests around workflow completion and rollback
- Add tests for reject/reopen transaction behavior

**Steps:**

- [ ] Make ordinary node update, component save, task writes, complete, and rollback reject writes while a requirement workflow has `terminal_status=REJECTED`.
- [ ] Extend completion validation so node owner, schedule, template-required fields, and the receiving component policy are all checked server-side; only `PASS` can advance the node.
- [ ] Implement transactional reject with `requirement:manage`: set requirement status `REJECTED`, set workflow terminal status, persist the component snapshot/reason, and append the existing audit/operation record. Any failure rolls back all writes.
- [ ] Implement transactional reopen with `requirement:manage`: set requirement back to `ACTIVE`, clear terminal status, restore the receiving node to active when it can be identified from the workflow snapshot, preserve completed prior nodes and future locked nodes, and write a required reopen reason. For legacy snapshots without the component, restore status only and do not guess a node.
- [ ] Keep rejected requirement target relations/history but exclude rejected requirements from active source selectors.
- [ ] Add tests for all valid/invalid validity-decision combinations, needs-info blocking, reject atomicity, reopen placement, terminal write blocking, permission failures, and optimistic conflicts.
- [ ] Run the complete backend test suite before starting frontend integration.

## Task 5: Build the runtime composite component and connect it to the detail page

**Files:**

- Add `/Users/fs/Desktop/Project/pms-front/src/views/development/detail/RequirementReceivingAnalysisComponent.vue`
- Add `/Users/fs/Desktop/Project/pms-front/src/views/development/detail/requirement-receiving-analysis.ts` or colocated typed model/helpers
- Modify `/Users/fs/Desktop/Project/pms-front/src/components/workflow/workflow-component-registry.ts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Modify `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/api/development-item.ts`
- Add `/Users/fs/Desktop/Project/pms-front/src/views/development/requirement-receiving-analysis.visual.test.mjs`

**Steps:**

- [ ] Implement the four sections as one component: read-only requirement overview, filter form, analysis form with computed score/value conclusion, and decision controls.
- [ ] Respect template config for section visibility, score visibility/required flags, analysis conclusion requirement, and reject availability; preserve disabled-section data without validating it.
- [ ] Use existing bound-field controls for final priority and other requirement fields; do not create a second priority source.
- [ ] Load/save component state through the new API with node version; show version conflict and backend validation errors in the existing page feedback pattern.
- [ ] Expose “驳回需求” and, for manageable users on rejected requirements, “重新开启”; keep ordinary “完成节点” for PASS only and retain the existing right-aligned action placement.
- [ ] Make the component read-only when the node is completed, locked, or workflow terminal; allow editing when the node is active and permissions allow it.
- [ ] Add visual tests for empty state, configured state, required markers, pending score, needs-info block, rejected terminal state, reopen action, and old node without the component.
- [ ] Run focused frontend unit/visual tests and typecheck.

## Task 6: Add administrator configuration UI and localization

**Files:**

- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/index.vue`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-model.mjs`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-schema.mjs`
- Modify `/Users/fs/Desktop/Project/pms-front/src/locales/zh-CN.ts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/locales/en-US.ts`
- Modify `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-admin-visual.test.mjs`

**Steps:**

- [ ] Add the component to the requirement template palette and prevent adding it to non-requirement process types or adding it twice.
- [ ] Render a component configuration inspector when the component card is selected, with section visibility, required controls, score controls, and reject toggle.
- [ ] Enforce UI constraints immediately (hidden section cannot be required, decision section cannot be disabled) while keeping backend publish validation authoritative.
- [ ] Show a publish-time error when a new/edited requirement template lacks exactly one receiving component; do not mutate old published template snapshots.
- [ ] Preserve content order and component config when saving, previewing, publishing, cloning, archiving, or reopening a template version.
- [ ] Add Chinese and English labels, hints, validation messages, terminal status text, and action text.
- [ ] Add visual/model tests for add/remove/order/config/publish errors and legacy template compatibility.
- [ ] Run focused workflow admin tests and typecheck.

## Task 7: End-to-end closure and regression hardening

**Files:**

- Extend the existing Playwright/E2E suite in the project’s current test directory
- Add or update requirement list/detail selectors and status fixtures
- Update `docs/superpowers/specs/2026-09-28-requirement-receiving-analysis-design.md` only if implementation discovers a confirmed contract correction

**Steps:**

- [ ] Cover: requirement entry → receiving save → incomplete scoring remains pending → NEEDS_INFO blocks completion → fill supplement → PASS advances.
- [ ] Cover: REJECT from receiving → requirement list shows rejected → active source selectors hide it → REOPEN restores ACTIVE and receiving node editability.
- [ ] Cover: component config changes visibility/required behavior and a newly published template is used by a new requirement while an old requirement keeps its snapshot.
- [ ] Cover: completed receiving node is read-only and future nodes remain locked after rejection/reopen placement rules.
- [ ] Run backend tests, frontend tests, typecheck, build, and E2E in the documented order; capture any environment-only Docker limitation separately from code failures.
- [ ] Review the final diff for accidental changes to requirement priority bindings, existing workflow components, and legacy template JSON.

## Task 8: Final review and delivery

**Steps:**

- [ ] Run `git diff --check` in both repositories and inspect migration, API, permission, and terminal-state diffs manually.
- [ ] Run the verification-before-completion checks: report exact commands and results, with no claim of passing tests unless the command completed successfully.
- [ ] Summarize changed files, API behavior, compatibility behavior, known limitations, and test results.
- [ ] Do not push or commit until the user explicitly requests delivery; when requested, commit backend/frontend changes separately if that matches the repositories’ existing convention.

## Self-review of the Plan

- **Business closure:** The plan covers the full receiving loop: editable intake, structured analysis, pending supplementation, pass to the next node, reject terminal state, and reopen with history preserved.
- **Template closure:** The same component key/config is validated at publish time and resolved from the workflow snapshot at runtime, so future template versions cannot silently change existing requirements.
- **Data closure:** Main requirement fields remain the source of truth; component data is namespaced and merged without destroying ordinary node fields; terminal state is transactional.
- **Permission closure:** Ordinary writes use existing requirement write permission; reject/reopen use management permission; backend checks remain authoritative even if the UI is bypassed.
- **Compatibility closure:** Old published templates and old node snapshots remain readable; missing component data renders empty/default state and does not cause guessed node placement.
- **Test closure:** Unit tests cover policy boundaries, integration tests cover state transitions and transactions, visual tests cover editor/runtime behavior, and E2E covers the end-to-end business path.

## Execution Recommendation

Because the backend schema, template model, runtime component, and E2E contract are tightly coupled, execute this plan natively in the current workspace task-by-task. Use the task checkboxes as the progress ledger and pause at the end of each major task if a test exposes a contract mismatch.
