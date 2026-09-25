# 项目推进中心 v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入任务依赖和新阻塞状态的前提下，把项目配置问题、节点问题和任务时间问题统一转换为可查看、可定位、可处理的行动中心。

**Architecture:** 后端新增实时计算的 `ProjectAttentionService`，统一生成项目行动项、项目推进摘要和工作台行动项；不新增行动项数据表。项目详情使用完整检查清单，工作台使用聚合行动列表，项目列表和企业看板只使用轻量摘要；所有入口复用相同的服务和时间/权限规则。

**Tech Stack:** Spring Boot 3 / Java 17 / MyBatis-Plus / JUnit 5；Vue 3 / TypeScript / Ant Design Vue / Vue I18n / Node test runner。

**Spec:** `.worktrees/project-action-center-design/docs/superpowers/specs/2026-09-17-project-action-center-v1-design.md`

## Global Constraints

- 保持 `项目 → 节点 → 任务` 关系，不增加任务之间的依赖关系。
- 不增加独立的“阻塞”任务状态；逾期是由状态和截止日期实时推导出的关注状态。
- 普通项目成员可以查看其参与项目的全部问题；查看权限和修改权限必须分离。
- 当前节点问题是重点行动项；未来节点缺少负责人、日期或任务只作为普通提醒，不阻塞当前项目。
- 所有日期判断使用 `TaskScheduleCalculator.today()` 的 `Asia/Shanghai` 业务日期。
- 已完成任务不进入逾期、今日到期或即将到期行动项。
- 后端统一计算行动项，前端不得复制一套独立业务规则。
- 每个阶段完成后必须运行该阶段测试，并完成代码、权限、空状态和边界条件自我 review，review 通过后才进入下一阶段。

---

## Stage 0: 基线确认与计划落地

**Files:**

- Create: `docs/superpowers/plans/2026-09-17-project-action-center-v1.md`
- Read: `docs/superpowers/specs/2026-09-17-project-action-center-v1-design.md`
- Read: `src/main/java/com/brad/pms/service/WorkbenchService.java`
- Read: `src/main/java/com/brad/pms/service/ProjectService.java`
- Read: `../pms-front/.worktrees/task-overdue-reschedule/src/views/project/detail/index.vue`
- Read: `../pms-front/.worktrees/task-overdue-reschedule/src/views/workbench/index.vue`

**Interfaces:**

- 后续阶段继承当前 `codex/task-overdue-reschedule` 工作区中已经存在的服务器派生任务排期能力。
- 不修改主工作区，不清理或覆盖当前工作区已有的未提交用户修改。

- [x] **Step 1: 写入并自查实施计划**

  对照设计说明确认计划覆盖：统一行动项、项目详情检查清单、工作台行动中心、列表/看板直达、权限、时区、空状态和验证。

- [x] **Step 2: 确认测试入口**

  Backend command: `mvn -q -DskipTests compile` and focused JUnit commands described in later stages.

  Frontend command: `pnpm test -- <test-file>` and `pnpm typecheck`.

## Stage 0 review checkpoint

- [x] 确认本阶段只新增计划文档，没有业务代码变更。
- [x] 确认后端和前端继续使用独立 worktree，主工作区未被修改。
- [x] 确认后续所有新逻辑都从失败测试开始。

---

## Stage 1: 后端统一行动项模型与项目详情/工作台接口

**Files:**

- Create: `src/main/java/com/brad/pms/common/enums/ProjectAttentionType.java`
- Create: `src/main/java/com/brad/pms/common/enums/ProjectAttentionSeverity.java`
- Create: `src/main/java/com/brad/pms/dto/response/ProjectActionItemDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/ProjectReadinessDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/ProjectAttentionSummaryDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/WorkbenchActionCenterDTO.java`
- Create: `src/main/java/com/brad/pms/service/ProjectAttentionService.java`
- Create: `src/test/java/com/brad/pms/service/ProjectAttentionServiceTest.java`
- Modify: `src/main/java/com/brad/pms/dto/response/ProjectDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/WorkbenchDTO.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectService.java`
- Modify: `src/main/java/com/brad/pms/service/WorkbenchService.java`
- Modify: `src/main/java/com/brad/pms/mapper/ProjectTaskMapper.java` only if a batch query is required by the existing mapper patterns
- Modify: `src/main/java/com/brad/pms/service/ProjectBoardService.java` only if the board projection needs the new lightweight summary

**Interfaces:**

- `ProjectAttentionService.buildForProject(ProjectDTO project, List<ProjectNodeDO> nodes, List<ProjectTaskDO> tasks, List<ProjectNodeRiskDO> risks, LocalDate today)` returns `ProjectReadinessDTO`.
- `ProjectAttentionService.buildSummary(ProjectDTO project, List<ProjectNodeDO> nodes, List<ProjectTaskDO> tasks, List<ProjectNodeRiskDO> risks, LocalDate today)` returns `ProjectAttentionSummaryDTO`.
- `ProjectAttentionService.buildForProjects(List<ProjectDTO> projects, Map<Long, List<ProjectNodeDO>> nodesByProject, Map<Long, List<ProjectTaskDO>> tasksByProject, Map<Long, List<ProjectNodeRiskDO>> risksByProject, LocalDate today)` returns `WorkbenchActionCenterDTO`.
- `ProjectDTO.readiness` is populated only for project detail responses; `ProjectDTO.attentionSummary` is safe for list, board, and workbench projections.
- `WorkbenchDTO.actionCenter` contains a count summary and at most 20 sorted `ProjectActionItemDTO` values.

### Task 1.1: Define the failing domain tests

- [ ] **Step 1: Add tests for current-node rules**

  Add tests proving that an active project with a missing project manager, current-node owner, current-node schedule, or current-node task produces the corresponding `CRITICAL` action item.

- [ ] **Step 2: Add tests for future-node rules**

  Add tests proving that a not-started future node with missing owner, schedule, or task produces `WARNING` items and never increments the critical count.

- [ ] **Step 3: Add tests for task schedule rules**

  Add tests for overdue, today, due-soon, no-due-date, and completed tasks. Completed tasks must produce no schedule action.

- [ ] **Step 4: Add tests for ordering**

  Assert overdue tasks sort before current-node configuration issues, current-node issues sort before future-node warnings, and overdue days sort descending within overdue tasks.

- [ ] **Step 5: Run the focused test and verify RED**

  Run: `mvn -q -Dtest=ProjectAttentionServiceTest test`

  Expected: FAIL because the new enums, DTOs, and service do not exist yet.

### Task 1.2: Implement the minimal action-item model and calculator

- [ ] **Step 1: Add enums and DTOs**

  Use string-compatible enum names from the design: `PROJECT_MANAGER_MISSING`, `CURRENT_NODE_OWNER_MISSING`, `CURRENT_NODE_SCHEDULE_MISSING`, `CURRENT_NODE_TASK_MISSING`, `FUTURE_NODE_OWNER_MISSING`, `FUTURE_NODE_SCHEDULE_MISSING`, `FUTURE_NODE_TASK_MISSING`, `TASK_OVERDUE`, `TASK_DUE_TODAY`, `TASK_DUE_SOON`, and `HIGH_RISK_OPEN`; use `CRITICAL`, `WARNING`, and `INFO` severities.

- [ ] **Step 2: Implement project and node derivation**

  Treat status `IN_PROGRESS` as the current node, status `NOT_STARTED` as a future node, and completed/terminated nodes as non-actionable. A schedule is complete only when both `startDate` and `endDate` are present. A node is missing a task when no task exists for that node, regardless of whether a future node is expected to have one.

- [ ] **Step 3: Implement task and risk derivation**

  Reuse `TaskScheduleCalculator.calculate`. Only open projects and incomplete tasks produce time actions. A high-level open risk produces one `HIGH_RISK_OPEN` action, targeted to its node.

- [ ] **Step 4: Implement stable sorting and readiness counts**

  Implement the exact priority from the spec, then sort by overdue days, due date, project priority, node sort, and display name. Calculate `completedCount`, `totalCount`, `percent`, `criticalCount`, and `warningCount` from the generated items.

- [ ] **Step 5: Run the focused test and verify GREEN**

  Run: `mvn -q -Dtest=ProjectAttentionServiceTest test`

  Expected: PASS with all rule, sorting, and boundary tests.

### Task 1.3: Attach action data to existing read APIs

- [ ] **Step 1: Add failing controller/service integration tests**

  Extend the existing project and workbench service/controller tests to assert that project detail returns `readiness`, the workbench returns `actionCenter`, and a readable ordinary member receives problems for all readable participating projects.

- [ ] **Step 2: Load related data in batches**

  In `ProjectService`, load nodes, tasks, risks, and project DTOs in batches for list/detail projections. Avoid one query per action item. Preserve existing project scope filtering before action calculation.

- [ ] **Step 3: Populate project detail readiness**

  In `ProjectService.detail`, build the full readiness object for the requested project after existing permission checks. Keep list responses lightweight by omitting the full item list.

- [ ] **Step 4: Populate workbench action center**

  In `WorkbenchService.load`, use the existing readable project set as the visible project set. Do not filter action items to only the current user's assigned tasks; ordinary members must see all problems in their readable participating projects. Preserve the existing task/project/activity payloads.

- [ ] **Step 5: Run focused backend tests**

  Run: `mvn -q -Dtest=ProjectAttentionServiceTest,ProjectServiceTest,WorkbenchServiceTest test`

  Expected: PASS. If a named existing test class is absent, run the closest existing service/controller test class and record the command in the review notes.

## Stage 1 self-review checkpoint

- [ ] Compare every generated action type with the approved design and remove any accidental task-dependency or blocked-status logic.
- [ ] Verify ordinary member visibility uses existing readable-project scope and is not restricted to manager/admin.
- [ ] Verify `canAct` is false for read-only users while action visibility remains true.
- [ ] Verify completed projects/tasks, deleted projects, completed/terminated nodes, and no-due-date tasks do not create false actions.
- [ ] Verify one project with many future nodes is capped and sorted correctly in the workbench.
- [ ] Run focused tests and inspect the changed diff before starting frontend work.

---

## Stage 2: 项目详情推进状态卡、检查清单和定位

**Files:**

- Create: `pms-front/.worktrees/task-overdue-reschedule/src/views/project/detail/components/ProjectReadinessCard.vue`
- Create: `pms-front/.worktrees/task-overdue-reschedule/src/views/project/detail/project-attention.ts`
- Create: `pms-front/.worktrees/task-overdue-reschedule/src/views/project/detail/project-attention.test.mjs`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/api/project.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/types/domain.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project/detail/index.vue`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/locales/zh-CN.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/locales/en-US.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/styles/pms-theme.css` only for shared action-item tokens if scoped styles are insufficient

**Interfaces:**

- `ProjectReadiness` mirrors backend `ProjectReadinessDTO`.
- `ProjectActionItem` mirrors backend action item fields and preserves `projectId`, `nodeId`, `taskId`, `actionTarget`, and `canAct`.
- `buildAttentionRoute(item)` returns `/projects/:projectId` plus `node` and/or `task` query parameters.
- The detail page consumes `project.readiness` and retains existing `?node=` and `?task=` focus behavior.

### Task 2.1: Add front-end mapping tests first

- [ ] **Step 1: Test route mapping**

  Prove that task actions generate `{ path: '/projects/:id', query: { task, node } }`, node actions generate a node query, and project actions generate a project-only route.

- [ ] **Step 2: Test severity grouping and display copy selection**

  Prove that critical actions are grouped before warnings and that a future-node owner warning is not mapped to the critical visual tone.

- [ ] **Step 3: Run the focused test and verify RED**

  Run: `pnpm test -- src/views/project/detail/project-attention.test.mjs`

  Expected: FAIL because the helper module does not exist yet.

### Task 2.2: Implement the readiness card

- [ ] **Step 1: Add the shared domain types and API typing**

  Add optional `readiness` and `attentionSummary` fields to `Project`, plus `ProjectActionItem` and `ProjectReadiness` interfaces.

- [ ] **Step 2: Implement route helper and display helpers**

  Keep route generation in `project-attention.ts`; do not embed query construction in the template.

- [ ] **Step 3: Build the card and checklist**

  Render completion progress, current node, critical/warning counts, one primary next action, and a collapsible “查看全部问题” list. Ordinary warnings use neutral/warning tones; critical actions use the existing danger treatment without introducing a blocked badge.

- [ ] **Step 4: Mount near the project header**

  Insert `ProjectReadinessCard` below the project title metadata and above the node flow/detail content. On action click, navigate to the target route. On return to the page, existing `focusNodeId`/`focusTaskId` logic must activate the target node/task.

- [ ] **Step 5: Add Chinese and English copy**

  Add labels for readiness, action types, severity, empty state, read-only state, and next-action buttons in both locale files.

- [ ] **Step 6: Run tests and typecheck**

  Run: `pnpm test -- src/views/project/detail/project-attention.test.mjs src/views/project/detail/workflow.test.mjs` and `pnpm typecheck`.

  Expected: PASS.

## Stage 2 self-review checkpoint

- [ ] Open a project with no issues, current-node issues, future-node warnings, and overdue tasks; verify each card state.
- [ ] Verify clicking a task action activates the right node and existing task focus behavior remains intact.
- [ ] Verify ordinary project members see the card but users without edit permission do not see edit affordances.
- [ ] Verify no new “blocked” status or task dependency UI appears.
- [ ] Verify the card does not break the existing project flow, schedule tabs, task board, or AI-removal changes already present in the worktree.
- [ ] Run typecheck and focused tests before starting workbench changes.

---

## Stage 3: 工作台行动中心

**Files:**

- Create: `pms-front/.worktrees/task-overdue-reschedule/src/views/workbench/action-center.ts`
- Create: `pms-front/.worktrees/task-overdue-reschedule/src/views/workbench/action-center.test.mjs`
- Create: `pms-front/.worktrees/task-overdue-reschedule/src/views/workbench/WorkbenchActionCenter.vue`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/api/workbench.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/workbench/index.vue`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/workbench/workbench.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/locales/zh-CN.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/locales/en-US.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/styles/pms-theme.css` or a new scoped style block in the component

**Interfaces:**

- `WorkbenchPayload.actionCenter` is optional for backward compatibility during rollout.
- `ActionCenterFilter = 'ALL' | 'OVERDUE' | 'TODAY' | 'PROJECT' | 'SOON'`.
- `filterActionItems(items, filter)` returns a stable sorted list without changing backend counts.
- `getActionItemRoute(item)` reuses the detail-page route helper behavior.

### Task 3.1: Add filter/sort tests first

- [ ] **Step 1: Test filters**

  Cover all, overdue, today, project, and due-soon filters, including a project configuration warning and an overdue task from the same project.

- [ ] **Step 2: Test empty-state reason selection**

  Return different keys for no assigned tasks, no project configuration problems, and no time-sensitive actions.

- [ ] **Step 3: Run the focused test and verify RED**

  Run: `pnpm test -- src/views/workbench/action-center.test.mjs`

  Expected: FAIL because the helper module does not exist yet.

### Task 3.2: Implement the action center UI

- [ ] **Step 1: Add API/domain types**

  Extend `WorkbenchPayload` with `actionCenter`, define action item and count types, and keep existing task/project/activity fields unchanged.

- [ ] **Step 2: Implement filter helpers**

  Use backend action type/severity fields; do not recompute overdue dates in the front-end action center.

- [ ] **Step 3: Build grouped action center**

  Add the section after overview cards and before the existing task/project/activity grid. Show overdue, today, project issues, and next-seven-days filters, with at most the returned 20 items.

- [ ] **Step 4: Implement action navigation and read-only treatment**

  Make every visible issue clickable. If `canAct` is false, use “查看详情” instead of “去处理”; never hide the issue from an ordinary member solely because the member cannot edit.

- [ ] **Step 5: Add explicit empty states and i18n**

  Use separate copy for no assigned tasks, no project issues, and no time-sensitive actions.

- [ ] **Step 6: Run tests and typecheck**

  Run: `pnpm test -- src/views/workbench/action-center.test.mjs src/views/workbench/workbench-visual.test.mjs` and `pnpm typecheck`.

  Expected: PASS.

## Stage 3 self-review checkpoint

- [ ] Verify a normal member sees project issues from all readable participating projects, not only their assigned tasks.
- [ ] Verify the action center remains useful when `tasks` is empty but project configuration issues exist.
- [ ] Verify all actions require at most one click to reach project/node/task detail.
- [ ] Verify the existing overdue task card and counters do not duplicate or contradict the new action center.
- [ ] Verify loading, API failure, and empty states preserve the existing workbench layout.
- [ ] Run focused tests and typecheck before list/board work.

---

## Stage 4: 项目列表、企业看板和跨页面直达

**Files:**

- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project/list/index.vue`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project-dashboard/index.vue`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project-dashboard/enterprise-board.mjs`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project-dashboard/enterprise-board.d.mts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project/list/index.test.mjs`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project-dashboard/enterprise-board.test.mjs`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/views/project/detail/index.vue` only if action query handling needs to be extended
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/locales/zh-CN.ts`
- Modify: `pms-front/.worktrees/task-overdue-reschedule/src/locales/en-US.ts`

**Interfaces:**

- `Project.attentionSummary` is the only data used for list/board lightweight health signals.
- List/board actions navigate to `/projects/:id` with `node` or `task` query parameters; they do not duplicate full action-item calculation.

### Task 4.1: Add direct-link tests first

- [ ] **Step 1: Test list summary rendering**

  Assert that a row with overdue/current-node issue counts renders a clickable health summary and a clean project renders no alert badge.

- [ ] **Step 2: Test enterprise board navigation**

  Assert that clicking a health signal opens the project with the correct target query and does not create a separate dependency workflow.

- [ ] **Step 3: Run focused tests and verify RED**

  Run: `pnpm test -- src/views/project/list/index.test.mjs src/views/project-dashboard/enterprise-board.test.mjs`

  Expected: FAIL because the new summary rendering and handlers do not exist yet.

### Task 4.2: Implement lightweight direct paths

- [ ] **Step 1: Render list health summary**

  Add compact overdue/current-node issue counts near the existing current-node/date summary without adding a large table column.

- [ ] **Step 2: Render enterprise board health summary**

  Add the same compact counts to project cards/rows and route clicks to the project detail target.

- [ ] **Step 3: Preserve permissions and neutral states**

  A readable member can click into an issue even when `canAct` is false. A project with no issue should remain neutral and must not display a red/amber warning.

- [ ] **Step 4: Run focused tests and typecheck**

  Run: `pnpm test -- src/views/project/list/index.test.mjs src/views/project-dashboard/enterprise-board.test.mjs` and `pnpm typecheck`.

  Expected: PASS.

## Stage 4 self-review checkpoint

- [ ] Verify list, board, detail, and workbench all point to the same project/node/task target.
- [ ] Verify a member can view an issue from list/board but cannot edit when permissions deny it.
- [ ] Verify no extra network request is made per row/card for full action data.
- [ ] Verify project list pagination and existing filters remain unchanged.
- [ ] Verify the board remains readable with zero, one, and many issue counts.

---

## Stage 5: 全链路验证与最终 review

**Files:**

- Modify only if verification exposes a defect in files changed by Stages 1–4.
- Add focused tests next to the failing behavior; do not weaken existing tests.

### Task 5.1: Backend verification

- [ ] Run: `mvn -q -Dtest=ProjectAttentionServiceTest,ProjectServiceTest,WorkbenchServiceTest test`.
- [ ] Run: `mvn -q -DskipTests compile`.
- [ ] Verify permission cases: administrator, ordinary project member, readable-only member, and non-member.
- [ ] Verify empty/new project, current-node configuration missing, future-node warnings, overdue task, completed task, completed project, and terminated project.
- [ ] Verify API payloads do not include a full readiness list in list rows unless explicitly requested.

### Task 5.2: Frontend verification

- [ ] Run all new focused tests and the existing project/workbench visual tests.
- [ ] Run: `pnpm typecheck`.
- [ ] Run: `pnpm build`.
- [ ] Manually verify `/projects`, `/projects/:id`, `/dashboard`, and `/projects/dashboard` at desktop and narrow widths.
- [ ] Verify reload, route back/forward, task focus, node focus, API error, and empty states.
- [ ] Verify Chinese and English copy, especially “普通提醒”, “逾期”, “今日到期”, and “无编辑权限”.

### Task 5.3: Final self-review

- [ ] Review backend diff for N+1 queries, permission leaks, duplicate date logic, and accidental persistence.
- [ ] Review frontend diff for duplicated routing logic, oversized components, inaccessible clickable rows, and duplicated overdue cards.
- [ ] Confirm no task dependency UI, no new blocked status, no manual dismiss state, and no AI assistant UI is reintroduced.
- [ ] Confirm all four stage review checkpoints are recorded in the final task summary.

## Final acceptance

The feature is complete only when all of the following are true:

- Project detail shows a complete readiness card and actionable checklist.
- Workbench shows a server-derived action center with filters and explicit empty states.
- Project list and enterprise board provide lightweight issue summaries and direct navigation.
- Ordinary members can see all issues in readable participating projects.
- Editing remains permission-controlled.
- Future-node missing configuration is warning-only.
- Overdue and due-soon semantics are consistent across all surfaces.
- Focused tests, typecheck, build, and the final self-review pass.

## Execution status

- [x] Stage 1 implemented and reviewed: server-derived action items, readiness, lightweight summaries, workbench aggregation, and permission-aware `canAct`.
- [x] Stage 2 implemented and reviewed: project-detail readiness card, route targeting, critical/warning display, and backward-compatible empty rendering.
- [x] Stage 3 implemented and reviewed: workbench action center filters, explicit empty states, read-only actions, and navigation.
- [x] Stage 4 implemented and reviewed: project-list and enterprise-board lightweight attention summaries without per-row requests.
- [x] Frontend full test suite: 397 passed; production build passed.
- [x] Backend focused action-center/workbench/project tests and compile passed.
- [ ] Backend full integration suite remains environment-blocked until Docker/Testcontainers is available; the failure was database-container startup, not a feature assertion.
