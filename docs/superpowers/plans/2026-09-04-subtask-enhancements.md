# Subtask Management Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add polished subtask status, optional due dates, deletion, and transaction-safe parent-completion cascading.

**Architecture:** Keep the existing task endpoints and DTOs. Extend `TaskService.update` with an explicit due-date-clear flag, a Shanghai-business-date completion helper, and a guard preventing children of completed parents from reopening. Render the new controls in `TaskWorkPanel.vue`, using the task-level capability DTO for every edit/delete affordance.

**Tech Stack:** Spring Boot, MyBatis-Plus, JUnit 5, Vue 3 `<script setup>`, Ant Design Vue, TypeScript, Node test runner, pnpm.

**Spec:** `docs/superpowers/specs/2026-09-04-subtask-enhancements-design.md`

## Global Constraints

- Subtask due date is optional; an empty due date does not create a reminder.
- Parent completion cascades to direct children in the same transaction.
- Missing child due dates are filled with the parent completion date in `Asia/Shanghai`; existing child dates are preserved.
- A child of a completed parent cannot be changed back to TODO or DOING.
- Frontend controls are hints only; backend permission and lifecycle checks remain authoritative.
- Do not add dependencies or a new HTTP endpoint.

### Task 1: Backend lifecycle and due-date contract

**Files:**
- Modify: `src/test/java/com/brad/pms/service/TaskServiceTest.java`
- Modify: `src/main/java/com/brad/pms/dto/request/TaskUpdateCmd.java`
- Modify: `src/main/java/com/brad/pms/service/TaskService.java`

**Interfaces:**
- Consumes: existing `PUT /tasks/{id}` request mapping and `ProjectTaskMapper` CRUD methods.
- Produces: `TaskUpdateCmd.clearDueDate`, `TaskService.update` cascade behavior, and Shanghai-date completion semantics for frontend and future callers.

- [x] **Step 1: Write failing backend tests**

Add tests to `TaskServiceTest` that arrange a parent task and two direct children, then assert:

```java
@Test
void completingParentCompletesChildrenAndBackfillsOnlyMissingDueDates() {
    ProjectTaskDO parent = task(1L, null);
    ProjectTaskDO missingDateChild = task(2L, 1L);
    ProjectTaskDO datedChild = task(3L, 1L);
    datedChild.setDueDate(LocalDate.of(2026, 9, 8));
    when(taskMapper.selectById(1L)).thenReturn(parent);
    when(permissionService.requireProject(9L)).thenReturn(openProject());
    when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
    when(taskMapper.selectList(any())).thenReturn(List.of(missingDateChild, datedChild));

    TaskUpdateCmd cmd = new TaskUpdateCmd();
    cmd.setStatus(TaskStatus.DONE.getCode());

    taskService.update(1L, cmd);

    assertThat(missingDateChild.getStatus()).isEqualTo(TaskStatus.DONE.getCode());
    assertThat(missingDateChild.getDueDate()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")));
    assertThat(datedChild.getStatus()).isEqualTo(TaskStatus.DONE.getCode());
    assertThat(datedChild.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 8));
    verify(taskMapper, times(2)).updateById(any(ProjectTaskDO.class));
}

@Test
void completedParentRejectsReopeningChild() {
    ProjectTaskDO child = task(2L, 1L);
    ProjectTaskDO parent = task(1L, null);
    parent.setStatus(TaskStatus.DONE.getCode());
    when(taskMapper.selectById(2L)).thenReturn(child);
    when(taskMapper.selectById(1L)).thenReturn(parent);
    when(permissionService.requireProject(9L)).thenReturn(openProject());
    when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());

    TaskUpdateCmd cmd = new TaskUpdateCmd();
    cmd.setStatus(TaskStatus.DOING.getCode());

    assertThatThrownBy(() -> taskService.update(2L, cmd))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("父任务已完成");
}

@Test
void updateCanExplicitlyClearDueDate() {
    ProjectTaskDO task = task(2L, 1L);
    task.setDueDate(LocalDate.of(2026, 9, 8));
    when(taskMapper.selectById(2L)).thenReturn(task);
    when(permissionService.requireProject(9L)).thenReturn(openProject());
    when(permissionService.requireNode(9L, 3L)).thenReturn(openNode());
    TaskUpdateCmd cmd = new TaskUpdateCmd();
    cmd.setClearDueDate(true);

    taskService.update(2L, cmd);

    assertThat(task.getDueDate()).isNull();
}
```

Import `TaskStatus`, `LocalDate`, `ZoneId`, and Mockito `times` as needed. Run:

```bash
./mvnw -q -Dtest=TaskServiceTest test
```

Expected: the new tests fail because `clearDueDate` and the parent-completion guard/cascade do not exist yet.

- [x] **Step 2: Add explicit due-date clearing**

Add `private Boolean clearDueDate = false;` to `TaskUpdateCmd`. In `TaskService.update`, after the existing permission checks, apply the date mutation in this order:

```java
if (Boolean.TRUE.equals(cmd.getClearDueDate())) {
    task.setDueDate(null);
} else if (cmd.getDueDate() != null) {
    task.setDueDate(cmd.getDueDate());
}
```

Keep the existing permission restriction so a task assignee can still modify only content, status, and due date.

- [x] **Step 3: Add the completed-parent guard and cascade helper**

Before mutating a child status, load its parent when `task.getParentId() != null`; if the parent is already `TaskStatus.DONE` and the requested status is not DONE, throw `BusinessException.forbidden("父任务已完成，子任务不能回退")`.

After `taskMapper.updateById(task)` and only when the task changes into DONE, call a private method with this signature:

```java
private void completeDirectSubtasks(ProjectTaskDO parent, LocalDate completionDate)
```

The method queries non-deleted direct children by `parentId`, sets every child to `TaskStatus.DONE`, sets `dueDate` only when null, and calls `taskMapper.updateById(child)` for each child. Use `LocalDate.now(ZoneId.of("Asia/Shanghai"))` at the parent update boundary. Preserve existing audit behavior and record a status change for each child whose status changed; when a missing due date is filled, include the before/after task snapshots in the same operation log.

- [x] **Step 4: Run backend tests and refactor only after green**

Run:

```bash
./mvnw -q -Dtest=TaskServiceTest test
```

Expected: all `TaskServiceTest` tests pass, including the new cascade and clear-date assertions. Keep the cascade inside the existing `@Transactional` method so any failed child update rolls back the parent update.

### Task 2: Frontend subtask workspace

**Files:**
- Modify: `../pms-front/src/api/task.ts`
- Modify: `../pms-front/src/views/project/detail/components/TaskWorkPanel.vue`
- Modify: `../pms-front/src/views/project/detail/workflow.test.mjs`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`

**Interfaces:**
- Consumes: `Task.dueDate`, `Task.permissions.canEdit`, `Task.permissions.canDelete`, `updateTask`, and `deleteTask`.
- Produces: optional due-date creation/editing, status selection, deletion confirmation, responsive subtask layout, and localized labels.

- [x] **Step 1: Write failing frontend structure tests**

Extend `workflow.test.mjs` with assertions that `TaskWorkPanel.vue` contains an optional date picker bound to the subtask create date, a status select, a capability-gated delete action, and calls `deleteTask(item.id)` and `updateTask(item.id, { dueDate })`/`clearDueDate`.

Run:

```bash
cd ../pms-front
node --test src/views/project/detail/workflow.test.mjs
```

Expected: the new test fails because the date picker and delete action are not present.

- [x] **Step 2: Add API typing for explicit clearing**

Change the frontend update signature to accept the explicit clear flag without weakening task typing:

```ts
export type TaskUpdatePayload = Partial<Task> & { clearDueDate?: boolean }

export function updateTask(id: number, data: TaskUpdatePayload): Promise<Task> {
  return http.put(`/tasks/${id}`, data)
}
```

- [x] **Step 3: Implement the create and row controls**

In `TaskWorkPanel.vue`:

```ts
const subtaskDueDate = ref<string | null>(null)

async function onSubtaskDueDateChange(item: Task, value: string | null) {
  updatingSubtaskId.value = item.id
  try {
    await updateTask(item.id, value ? { dueDate: value } : { clearDueDate: true })
    emit('changed')
  } finally {
    updatingSubtaskId.value = null
  }
}
```

Pass `dueDate: subtaskDueDate.value` to `buildTaskPayload` on creation, reset it after success, and render each existing subtask as a compact card with:

```vue
<a-date-picker
  :value="item.dueDate"
  value-format="YYYY-MM-DD"
  allow-clear
  :disabled="!item.permissions?.canEdit || item.permissions?.readOnly"
  @change="onSubtaskDueDateChange(item, $event)"
/>
<a-select
  :value="item.status"
  :disabled="!item.permissions?.canEdit || item.permissions?.readOnly"
  @change="onSubtaskStatusChange(item, $event)"
/>
<a-button
  v-if="item.permissions?.canDelete"
  type="text"
  danger
  :aria-label="$t('task.deleteSubtaskAria', { title: item.title })"
  @click="onDeleteSubtask(item)"
>
  <DeleteOutlined />
</a-button>
```

`onDeleteSubtask` must call `Modal.confirm`, then `deleteTask(item.id)`, show the existing localized success message, and emit `changed`. Use CSS grid/flex with a minimum title width, consistent control heights, a subtle surface, and a mobile media query that lets controls wrap without clipping.

- [x] **Step 4: Add localized copy and run the targeted frontend tests**

Add Chinese and English keys for the optional due-date placeholder, clear-date label if needed, subtask delete title/content/aria label, and any empty-date display copy. Run:

```bash
cd ../pms-front
node --test src/views/project/detail/workflow.test.mjs
```

Expected: targeted tests pass and the subtask structure assertions prove the controls remain capability-backed.

### Task 3: Documentation and full verification

**Files:**
- Modify: `docs/superpowers/specs/2026-09-04-subtask-enhancements-design.md`
- Modify: `docs/business-specification.md`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`

**Interfaces:**
- Consumes: the implemented backend and frontend behavior from Tasks 1–2.
- Produces: accurate business documentation and in-app manual copy that states optional child deadlines, parent-completion cascading, date backfill, and the completed-parent rollback rule without claiming extra reminder behavior.

- [x] **Step 1: Run all backend tests**

Run:

```bash
cd .
./mvnw -q test
```

Expected: exit code 0.

- [x] **Step 2: Run all frontend checks**

Run:

```bash
cd ../pms-front
pnpm test
pnpm typecheck
pnpm build
git diff --check
```

Expected: all tests pass, typecheck succeeds, Vite produces a production build, and diff check is clean.

- [x] **Step 3: Update business rules and perform rendered UI smoke validation**

Append the subtask rules to the task/project-lifecycle section of `docs/business-specification.md` and keep the in-app manual locale section aligned: the deadline is optional, missing child deadlines are backfilled only when a parent becomes DONE, existing deadlines are preserved, and a completed parent must be rolled back before a child can be reopened. Do not state that every subtask receives a reminder; reminder behavior remains governed by the existing task reminder boundary rules.

The flow under test is: `/projects/2` → open a parent task → add a subtask with and without a due date → change status/date → delete a permitted subtask.

Use the available Browser plugin if present. If it is unavailable, use the existing local frontend and Playwright fallback, record whether authentication redirected to `/login`, and do not claim authenticated interaction proof when no authenticated session is available. Check that the subtask controls do not clip on desktop or a narrow viewport.

- [x] **Step 4: Review the final diff**

Inspect only the files changed for this feature, verify no new reminder behavior or unrelated project-image behavior was introduced, and confirm the parent completion rule is enforced in `TaskService`, not merely in Vue. Summarize tests, remaining browser-auth limitation, and the optional-deadline product rule.
