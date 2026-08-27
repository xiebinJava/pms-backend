# 项目状态与节点回滚一致性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一项目生命周期状态，并修复节点回滚后任务卡片权限未刷新导致无法拖拽的问题。

**Architecture:** 项目状态由后端 `ProjectStatus` 作为唯一业务契约，前端只负责展示、筛选和统计映射。节点状态变化后，任务看板按节点状态重新加载任务权限；后端仍是任务移动权限的最终裁决者。删除项目改为保留记录的逻辑删除，使“已删除”可以在项目列表中被展示和筛选。

**Tech Stack:** Vue 3、TypeScript、Ant Design Vue、Spring Boot、MyBatis-Plus、JUnit 5、Node test runner。

**Spec:** 项目生命周期与权限管理产品规范（本次需求上下文）

## Global Constraints

- 项目状态统一为：`1 进行中`、`2 已完成`、`3 已终止`、`4 已删除`；历史 `0` 兼容为进行中。
- 项目创建后自动为进行中，第一个节点自动为进行中；项目已完成、已终止或已删除后不可写入。
- 节点回滚后目标节点进入进行中，任务权限必须按最新节点状态重新计算。
- 任务拖拽保持本地乐观更新，不刷新整个页面；后端接口继续做最终权限校验。
- 状态颜色统一：节点未开始灰色、进行中橙色、已完成绿色、已终止红色、已删除红色；项目状态不使用未开始。

### Task 1: Lock the shared status contract with failing tests

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/security/ProjectPermissionPolicyTest.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.test.mjs`

**Interfaces:**
- Backend `ProjectStatus` must expose the four project lifecycle values and normalize legacy `0` to `进行中`.
- Frontend status helpers must map project status `0` and `4` to stable display tones.
- Frontend test coverage must describe reloading task data when the same node changes status.

- [x] **Step 1: Write failing tests**

  Add backend assertions for the four project statuses, `labelOf(0)`, `labelOf(4)`, and `normalize(0) == 1`. Add frontend assertions for project status metadata and a helper that decides whether the task list must refresh when the node status changes.

- [x] **Step 2: Run tests to verify they fail**

  Run `mvn -q -Dtest=ProjectPermissionPolicyTest test` in the backend and `node --test src/views/project/detail/workflow.test.mjs` in the frontend. Expected: failures because the status values and refresh helper are not yet implemented.

### Task 2: Fix rollback-to-task permission refresh

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/TaskKanban.vue`

**Interfaces:**
- Add `shouldReloadNodeTasks(previous?: NodeTaskScope, next?: NodeTaskScope): boolean` to the workflow helpers; the scope includes node ID, node status, and effective read-only state.
- `TaskKanban` watches the selected node status/read-only state in addition to `nodeId`, and calls `loadAll()` when the same node transitions from read-only to writable or vice versa.

- [x] **Step 1: Implement the minimal status-change reload**

  Watch `[props.nodeId, props.node.status, props.node.permissions?.readOnly]`; close the task modal and reload only when the node identity or effective editability changes. Keep the existing optimistic `onDrop` update and API rollback behavior.

- [x] **Step 2: Run frontend unit tests**

  Run `node --test src/views/project/detail/workflow.test.mjs` and then `npm run typecheck`.

### Task 3: Unify backend project lifecycle states and deletion semantics

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/common/enums/ProjectStatus.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/security/ProjectPermissionPolicy.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/service/ProjectService.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/resources/schema.sql`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/security/ProjectPermissionPolicyTest.java`

**Interfaces:**
- `ProjectStatus.normalize(0)` returns `1`; null values normalize to the active status for legacy compatibility.
- `ProjectPermissionPolicy.isProjectOpen` returns true only for `ACTIVE`; completed, terminated, and deleted projects are read-only.
- `ProjectService.delete` changes status to `DELETED` and retains project collaboration data for read-only viewing.
- Project statistics return `active`, `completed`, `terminated`, and `deleted` counts.

- [x] **Step 1: Update enum/policy tests first**

  Add assertions that a legacy zero-status project behaves as active, while completed/terminated/deleted projects cannot be managed. Add a service-level test or focused assertion for the status transition contract if the existing test harness supports it.

- [x] **Step 2: Implement enum, policy, service, and schema comments**

  Keep the four project status values, update legacy normalization/read-only rules, replace physical project deletion with a status update, and count normalized states in statistics. Update the project table status comment to document all four values.

- [x] **Step 3: Run backend tests**

  Run `mvn -q test` in the backend and inspect failures before continuing.

### Task 4: Unify frontend project status display, filter, and statistics

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/enums/index.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/project.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/list/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.ts`

**Interfaces:**
- `ProjectStatus.options()` exposes all four project states.
- `statusTagColor` and detail header tones use one shared mapping.
- `ProjectStats` includes `deleted`.

- [x] **Step 1: Add frontend status contract tests**

  Assert that the four project states are available, labels are correct, and the status color/tone mapping is stable. Keep node-level “未开始” coverage separate.

- [x] **Step 2: Implement shared mapping and list UI**

  Add the project status enum, stats interface fields, filter options, and list tags. Keep the statistics interface ready for the later enterprise overview, while the current project list does not render the temporary overview block. Keep action availability driven by backend permissions so deleted rows do not expose edit/delete controls.

- [x] **Step 3: Implement detail header mapping**

  Make the detail header use the shared status tone for legacy zero-status and deleted records, while retaining the node-level “未开始” color convention.

- [x] **Step 4: Run frontend tests and typecheck**

  Run `node --test src/views/project/detail/workflow.test.mjs`, `npm run typecheck`, and `npm run build`.

### Task 5: Verify the rendered rollback and project list behavior

**Files:**
- No source changes unless verification exposes a regression.

- [ ] **Step 1: Refresh the running frontend/backend using the project’s existing dev commands**

  Keep the existing local data intact and verify the app uses the current source.

- [ ] **Step 2: Open project 1 and inspect a task card after rollback**

  Confirm the active target node’s task cards have `draggable="true"` when the logged-in user has task-management permission, and that moving a card updates the board without a full-page refresh.

- [x] **Step 3: Open the project list**

  Confirm status filters and tags show `进行中、已完成、已终止、已删除`, and the current list does not render the overview statistics block. Later nodes may still display node-level `未开始`.

- [x] **Step 4: Run final verification**

  Capture test/typecheck/build results and report any unrelated pre-existing console warnings separately.
