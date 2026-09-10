# Notification Center and Task Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox - [ ] syntax.

**Goal:** 在现有站内通知链路上实现通知中心，以及按企业时区每天在临期窗口外沿和刚逾期当天各触发一次的任务提醒。

**Architecture:** 通过 V15 给 user_notification 增加内部幂等键，使用数据库侧候选查询和唯一约束保证提醒可重跑；提醒作业直接写入站内通知，不经过会发布 Webhook 的事件入口。保留顶部铃铛旧接口，新增 SQL 过滤后的分页接口和 /notifications 前端页面，通知中心、铃铛、项目深链共用现有 DTO 与路由规则。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、Flyway、Testcontainers MySQL 测试、Vue 3、TypeScript、Ant Design Vue、Node built-in test runner、Vite。

**Spec:** docs/superpowers/specs/2026-09-04-notification-center-task-reminders-design.md

## Global Constraints

- 临期状态窗口为 dueDate ∈ [today, today + N]，默认 N=7，共 8 个自然日；临期通知只在 dueDate=today+N 的当地 09:00 触发。
- 逾期通知只在 dueDate=today-1 的当地 09:00 触发；历史逾期不回填，停机错过触发日不补发。
- 提醒默认 pms.notification.task-reminder.enabled=false；权限收口和 SQL 可读性验收完成后才允许打开。
- 提醒候选、通知分页、顶部预览和未读数的状态/权限/删除过滤必须在 SQL 中完成后再分页或计数。
- 提醒使用独立的仅站内写入入口，不调用会触发 WebhookPublisher 的现有事件通知入口。
- 生产与测试均使用 MySQL 8；保留所有既有未提交改动，不使用 destructive git commands。
- 后端使用 TaskStatus.DONE、ProjectStatus.ACTIVE 等枚举，不散落字符串或裸状态数字。
- 前端正文只用文本插值渲染，不使用 v-html；页面在 390px 视口下不横向溢出。

---

### Task 1: Add V15 dedupe schema and reminder configuration

**Files:**
- Create: src/main/resources/db/migration/V15__task_notification_dedupe.sql
- Create: src/main/java/com/brad/pms/config/TaskReminderProperties.java
- Modify: src/main/java/com/brad/pms/entity/UserNotificationDO.java
- Modify: src/main/resources/application.yml
- Test: src/test/java/com/brad/pms/config/TaskReminderPropertiesTest.java
- Test: src/test/java/com/brad/pms/migration/NotificationDedupeMigrationTest.java

**Interfaces:**
- TaskReminderProperties binds pms.notification.task-reminder with enabled=false, cron="0 0 9 * * *", zone="Asia/Shanghai", dueSoonDays=7.
- UserNotificationDO gains dedupeKey; UserNotificationDTO does not expose it.
- V15 adds uk_user_notification_dedupe(user_id, type, dedupe_key); historical NULL keys remain valid.

- [ ] **Step 1: Write failing configuration and migration tests**

Assert the exact defaults, dueSoonDays range 1–30, valid zone/cron binding, V15 column/index existence, and insertion of an old notification with dedupe_key=NULL.

~~~java
assertThat(properties.isEnabled()).isFalse();
assertThat(properties.getCron()).isEqualTo("0 0 9 * * *");
assertThat(properties.getZone()).isEqualTo("Asia/Shanghai");
assertThat(properties.getDueSoonDays()).isEqualTo(7);
assertThatThrownBy(() -> properties.setDueSoonDays(0))
    .isInstanceOf(IllegalArgumentException.class);
assertThatThrownBy(() -> properties.setDueSoonDays(31))
    .isInstanceOf(IllegalArgumentException.class);
~~~

- [ ] **Step 2: Run the focused tests and verify they fail**

Run: mvn -q -Dtest=TaskReminderPropertiesTest,NotificationDedupeMigrationTest test

Expected: FAIL because the properties class, V15 migration, and entity field do not exist.

- [ ] **Step 3: Add migration, entity field, validated properties, and YAML defaults**

Use this additive migration; do not include deleted in the unique index:

~~~sql
ALTER TABLE user_notification ADD COLUMN dedupe_key VARCHAR(128) NULL;
CREATE UNIQUE INDEX uk_user_notification_dedupe
    ON user_notification (user_id, type, dedupe_key);
~~~

Annotate TaskReminderProperties with @ConfigurationProperties(prefix = "pms.notification.task-reminder"), @Validated, and @EnableConfigurationProperties(TaskReminderProperties.class). Reject dueSoonDays outside 1–30 and reject invalid zone/cron at startup. Add the property group to application.yml with enabled false and environment-backed values.

- [ ] **Step 4: Run the focused tests**

Run: mvn -q -Dtest=TaskReminderPropertiesTest,NotificationDedupeMigrationTest test

Expected: PASS, including migration against existing V14 notification data.

- [ ] **Step 5: Review the schema diff**

Run: git diff --check -- src/main/resources/db/migration/V15__task_notification_dedupe.sql src/main/java/com/brad/pms/config/TaskReminderProperties.java src/main/java/com/brad/pms/entity/UserNotificationDO.java src/main/resources/application.yml

Confirm only feature files changed.

---

### Task 2: Implement database-side reminder candidates and isolated scheduling

**Files:**
- Create: src/main/java/com/brad/pms/dto/TaskReminderCandidate.java
- Create: src/main/java/com/brad/pms/service/TaskReminderService.java
- Create: src/main/java/com/brad/pms/job/TaskReminderJob.java
- Modify: src/main/java/com/brad/pms/mapper/ProjectTaskMapper.java
- Modify: src/main/java/com/brad/pms/service/NotificationService.java
- Test: src/test/java/com/brad/pms/service/TaskReminderServiceTest.java
- Test: src/test/java/com/brad/pms/job/TaskReminderJobTest.java

**Interfaces:**
- ProjectTaskMapper.findReminderCandidates(LocalDate dueSoonDate, LocalDate overdueDate, int doneStatus, int activeStatus, long offset, long limit) returns SQL-filtered TaskReminderCandidate rows.
- NotificationService.emitInApp(Long userId, String type, String title, String content, Long projectId, Long taskId, Long nodeId, Long actorId, String dedupeKey) returns boolean and never publishes Webhook.
- TaskReminderService.run(LocalDate today) returns inserted reminder count.
- TaskReminderJob.run() uses LocalDate.now(clock.withZone(zoneId)); the scheduled bean exists only when enabled=true.

- [ ] **Step 1: Write failing reminder tests**

Use a fixed date and candidates due at today+7 and today-1. Verify exactly two isolated in-app writes with keys TASK_DUE_SOON:taskId:dueDate and TASK_OVERDUE:taskId:today. Verify no writes for due today, due today+3, due today+8, due today-2, DONE, deleted, unassigned, inactive-project, or inactive-user candidates. Verify duplicate false does not abort the batch, changed assignee only affects the new assignee, and WebhookPublisher is never called.

~~~java
when(taskMapper.findReminderCandidates(
        LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 3),
        TaskStatus.DONE.getCode(), ProjectStatus.ACTIVE.getCode(), 0, 500))
    .thenReturn(List.of(candidate(1L, 10L, 20L, LocalDate.of(2026, 9, 11)),
                      candidate(2L, 11L, 20L, LocalDate.of(2026, 9, 3))));
when(notificationService.emitInApp(any(), any(), any(), any(), any(), any(), isNull(), isNull(), anyString()))
    .thenReturn(true);

assertThat(service.run(LocalDate.of(2026, 9, 4))).isEqualTo(2);
verify(notificationService).emitInApp(eq(10L), eq("TASK_DUE_SOON"),
    anyString(), anyString(), eq(20L), eq(1L), isNull(), isNull(),
    eq("TASK_DUE_SOON:1:2026-09-11"));
~~~

- [ ] **Step 2: Run focused reminder tests and verify they fail**

Run: mvn -q -Dtest=TaskReminderServiceTest,TaskReminderJobTest test

Expected: FAIL because the candidate projection, isolated write method, service, and job do not exist.

- [ ] **Step 3: Add the SQL candidate projection**

Add TaskReminderCandidate fields taskId, projectId, assigneeId, taskTitle, projectName, dueDate. Add a MyBatis @Select script with all predicates before LIMIT/OFFSET:

~~~sql
SELECT t.id AS task_id, t.project_id, t.assignee_id,
       t.title AS task_title, p.name AS project_name, t.due_date
FROM project_task t
JOIN project p ON p.id = t.project_id
JOIN sys_user u ON u.id = t.assignee_id
WHERE t.deleted = FALSE
  AND t.status <> doneStatus
  AND t.due_date IN (dueSoonDate, overdueDate)
  AND p.deleted = FALSE
  AND p.status = activeStatus
  AND u.status = 'ACTIVE'
ORDER BY t.due_date, t.id
LIMIT limit OFFSET offset
~~~

Bind doneStatus from TaskStatus.DONE.getCode() and activeStatus from ProjectStatus.ACTIVE.getCode(). The candidate query must not load all tasks or project IDs.

- [ ] **Step 4: Add isolated in-app insertion and reminder service**

Keep existing event methods and their Webhook behavior unchanged. Add emitInApp that fills dedupeKey, inserts the row, returns true on insert, and catches only DuplicateKeyException to return false. TaskReminderService requests batches of 500, checks the two exact boundary dates, builds plain-text Chinese copy, and counts inserted rows. Non-duplicate database failures are logged with task/date context and propagated.

- [ ] **Step 5: Add the conditional scheduler**

Use @ConditionalOnProperty for pms.notification.task-reminder.enabled and @Scheduled with the configured cron and zone. Inject Clock and ZoneId so host JVM timezone cannot change the reminder date. Log local date, candidates, inserted, duplicate, and failures without notification secrets or content.

- [ ] **Step 6: Run focused reminder tests**

Run: mvn -q -Dtest=TaskReminderServiceTest,TaskReminderJobTest test

Expected: PASS, including fixed-zone behavior, no historical backfill, and no Webhook call.

---

### Task 3: Move notification visibility, unread count, and pagination into SQL

**Files:**
- Modify: src/main/java/com/brad/pms/mapper/UserNotificationMapper.java
- Modify: src/main/java/com/brad/pms/service/NotificationService.java
- Modify: src/main/java/com/brad/pms/controller/NotificationController.java
- Test: src/test/java/com/brad/pms/service/NotificationServiceTest.java
- Test: src/test/java/com/brad/pms/controller/NotificationPermissionAnnotationTest.java
- Test: src/test/java/com/brad/pms/service/NotificationQueryIntegrationTest.java

**Interfaces:**
- selectVisibleList(Long userId, boolean unreadFirst, int limit) returns SQL-filtered preview rows.
- countVisibleUnread(Long userId) returns the filtered unread count.
- selectVisiblePage(Page<UserNotificationDO> page, Long userId, String type, boolean unreadOnly) returns an IPage with the same SQL predicates.
- NotificationService.page(String type, boolean unreadOnly, long currPage, long pageSize) returns PageResult<UserNotificationDTO>.
- GET /notifications/page remains protected by project:read and invalid type returns 400.

- [ ] **Step 1: Write failing SQL/API tests**

Create fixtures for visible active, terminated, soft-deleted, another-user, and project-less notifications. Assert page total, page records, preview, and unread count exclude deleted/inaccessible project notifications before pagination/counting. Assert type filters, unreadOnly, created_at DESC then id DESC, page size max 50, invalid type 400, and mark-read ownership.

- [ ] **Step 2: Run notification tests and verify they fail**

Run: mvn -q -Dtest=NotificationServiceTest,NotificationPermissionAnnotationTest,NotificationQueryIntegrationTest test

Expected: FAIL because current code uses fixed-80-row in-memory keepReadable and has no page endpoint.

- [ ] **Step 3: Add shared mapper visibility SQL**

Use this join/predicate in page, preview, and unread-count queries:

~~~sql
FROM user_notification n
LEFT JOIN project p ON p.id = n.project_id
WHERE n.user_id = userId
  AND n.deleted = FALSE
  AND (n.project_id IS NULL
       OR (p.id IS NOT NULL AND p.deleted = FALSE AND p.status <> 4))
~~~

Apply type and read_at predicates in the same SQL. Use unread-first ordering only for the legacy preview and created_at DESC, id DESC for the center. Ensure the page count query uses the same join and predicates.

- [ ] **Step 4: Replace in-memory filtering**

Update list() and unreadCount() to call mapper SQL methods; remove keepReadable from these paths. Keep actor enrichment bounded to returned rows. Add page() with currPage minimum 1, pageSize clamped to 1–50, and exact validation for TASK_DUE_SOON/TASK_OVERDUE.

- [ ] **Step 5: Add the page controller method**

~~~java
@GetMapping("/page")
@RequirePermission(PermissionCode.PROJECT_READ)
public ResponseResult<PageResult<UserNotificationDTO>> page(
        @RequestParam(required = false) String type,
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "1") long currPage,
        @RequestParam(defaultValue = "20") long pageSize) {
    return ResponseResult.success(
        notificationService.page(type, unreadOnly, currPage, pageSize));
}
~~~

Keep all existing notification endpoints unchanged.

- [ ] **Step 6: Run notification tests**

Run: mvn -q -Dtest=NotificationServiceTest,NotificationPermissionAnnotationTest,NotificationQueryIntegrationTest test

Expected: PASS with correct totals and no先切页再过滤 behavior.

---

### Task 4: Add typed frontend API and filter helpers

**Files:**
- Modify: src/api/notification.ts
- Modify: src/types/domain.ts
- Create: src/views/notifications/notification-center.ts
- Test: src/views/notifications/notification-center.test.mjs

**Interfaces:**
- NotificationFilter is all | unread | due-soon | overdue.
- NotificationPageParams contains type TASK_DUE_SOON/TASK_OVERDUE, unreadOnly, currPage, pageSize.
- getNotificationPage(params) returns Promise<PageResult<UserNotification>>.
- notificationQuery(filter, page, pageSize) resets page to 1 on filter changes and preserves filter during pagination.

- [ ] **Step 1: Write failing helper tests**

~~~js
assert.deepEqual(notificationQuery('all', 1, 20), { currPage: 1, pageSize: 20 })
assert.deepEqual(notificationQuery('unread', 1, 20), { unreadOnly: true, currPage: 1, pageSize: 20 })
assert.deepEqual(notificationQuery('due-soon', 2, 20), { type: 'TASK_DUE_SOON', currPage: 2, pageSize: 20 })
assert.deepEqual(notificationQuery('overdue', 3, 20), { type: 'TASK_OVERDUE', currPage: 3, pageSize: 20 })
assert.equal(notificationTypeLabel('TASK_OVERDUE'), '逾期')
~~~

Also test unknown types get a neutral label and never become overdue.

- [ ] **Step 2: Run focused helper tests and verify they fail**

Run: node --test src/views/notifications/notification-center.test.mjs

Expected: FAIL because the helper module and page API function do not exist.

- [ ] **Step 3: Add API types and pure helpers**

Keep getNotifications(limit) unchanged for the topbar. Add getNotificationPage(params) using /notifications/page and the existing PageResult. Keep dedupeKey out of frontend types. Put filter mapping, labels, and read-state helpers in notification-center.ts.

- [ ] **Step 4: Run focused helper tests**

Run: node --test src/views/notifications/notification-center.test.mjs

Expected: PASS.

---

### Task 5: Build the notification center page and integrate the topbar

**Files:**
- Create: src/views/notifications/index.vue
- Modify: src/router/index.ts
- Modify: src/layout/Index.vue
- Modify: src/styles/fs-insight.css
- Modify: src/locales/zh-CN.ts
- Modify: src/locales/en-US.ts
- Test: src/views/notifications/index.test.mjs
- Modify: src/layout/index.test.mjs

**Interfaces:**
- Route /notifications, name notifications, titleKey route.notifications, permission project:read.
- Page owns filter, page, pageSize, total, records, loading, and error; filter changes set page=1 before fetching.
- Topbar View all goes to /notifications; existing 20-item preview and read/deep-link behavior remain compatible.

- [ ] **Step 1: Write failing page and shell tests**

Assert four filter labels, /notifications/page, markAllNotificationsRead, loading/empty/error states, notificationRoute, no v-html, route permission project:read, and a topbar View all link targeting /notifications.

- [ ] **Step 2: Run focused tests and verify they fail**

Run: node --test src/views/notifications/index.test.mjs src/layout/index.test.mjs

Expected: FAIL because route, page, and shell link do not exist.

- [ ] **Step 3: Add guarded page**

Use existing Ant Design Vue list/card/tabs/pagination primitives. Render text by interpolation; show TASK_DUE_SOON as 临期 and TASK_OVERDUE as 逾期; use notificationRoute(item) on click. Non-navigable notifications show a neutral message. Preserve the previous list until a replacement request succeeds, and show request errors.

Implement filter reset exactly:

~~~ts
function onFilterChange(next: NotificationFilter) {
  filter.value = next
  page.value = 1
  void load()
}
~~~

Use keyboard-focusable controls, aria labels, visible unread state, wrapped text, and a responsive 390px row layout.

- [ ] **Step 4: Add route and topbar entry**

Register the lazy route under the authenticated shell. Add a View all link to the bell dropdown and retain preview size 20. Reuse reminder labels, date formatting, and notificationRoute. Do not alter workbench scope.

- [ ] **Step 5: Add bilingual copy and styles**

Add route, filter, reminder, read-state, empty, failure, and View all keys to both locale files. Add focused FS Insight selectors for page header, tabs, unread marker, reminder colors, empty/error states, wrapped rows, and narrow-screen layout.

- [ ] **Step 6: Run focused tests, typecheck, and build**

Run:
~~~bash
node --test src/views/notifications/index.test.mjs src/layout/index.test.mjs
pnpm typecheck
pnpm build
~~~
Expected: PASS, no TypeScript errors, successful production bundle.

---

### Task 6: Update manuals, business rules, operations, and release checks

**Files:**
- Modify: docs/business-specification.md
- Modify: docs/operations/notification-and-upload.md
- Modify: docs/operations/release-checklist.md
- Modify: ../pms-front/docs/user-manual.md
- Modify: ../pms-front/docs/design-logic.md
- Modify: ../pms-front/src/locales/zh-CN.ts
- Modify: ../pms-front/src/locales/en-US.ts
- Test: ../pms-front/src/views/manual/manual.test.mjs

**Interfaces:**
- Backend docs state outer-edge trigger, no backfill, no missed-run replay, SQL filtering, V15 dedupe, default-disabled rollout, no Webhook, replacement-assignee, and host-timezone caveat.
- Frontend docs state notification-center navigation, mutually exclusive filters, page reset, read states, task deep link, plain-text rendering, and workbench separation.
- Release checklist states permission rollout, SQL visibility acceptance, then enabling the reminder property.

- [ ] **Step 1: Add documentation contract assertions**

Require these meanings in the docs: dueDate ∈ [today, today + N], today + N, today - 1, enabled=false, no historical overdue backfill, no Webhook, SQL filtering before pagination, deleted projects treated as nonexistent, and terminated-project historical notifications remain readable.

- [ ] **Step 2: Run documentation tests and verify they fail**

Run: node --test ../pms-front/src/views/manual/manual.test.mjs

Expected: FAIL on the new notification/reminder assertions.

- [ ] **Step 3: Update backend business and operations docs**

Use the approved spec as source of truth. Include the first-day formula:

~~~text
首日新增提醒数 = dueDate=today+N 的有效未完成负责人任务数
              + dueDate=today-1 的有效未完成负责人任务数
~~~

Document that the bell intentionally shows only 20 latest records and may displace older events; notification center remains the complete paginated view. Document default disabled configuration and explicit enablement only after permission acceptance.

- [ ] **Step 4: Update frontend manual and design logic**

Describe all four filters, mutually exclusive behavior, filter-change page reset, pagination retention, read actions, deep links, plain-text content, 390px behavior, and the known possible one-day workbench timezone difference.

- [ ] **Step 5: Run docs tests and diff check**

Run:
~~~bash
node --test ../pms-front/src/views/manual/manual.test.mjs
git diff --check -- docs/business-specification.md docs/operations/notification-and-upload.md docs/operations/release-checklist.md ../pms-front/docs/user-manual.md ../pms-front/docs/design-logic.md ../pms-front/src/locales/zh-CN.ts ../pms-front/src/locales/en-US.ts
~~~
Expected: PASS with no whitespace errors and matching Chinese/English feature semantics.

---

### Task 7: Full verification, runtime rollout check, and review package

**Files:**
- Verify: src/main/resources/openapi/pms-api.yaml
- Verify: scripts/check-openapi.sh
- Verify: scripts/check-release-consistency.sh
- Verify: scripts/check-privacy.sh
- Verify: scripts/audit-acceptance.sh
- Review: all files listed in Tasks 1–6

**Interfaces:**
- Produces tested V15 schema, disabled-by-default scheduler, SQL-safe /notifications/page API, and responsive /notifications UI.
- Does not change existing event Webhook behavior, workbench scope, feedback visibility, or permission matrix semantics.

- [ ] **Step 1: Run backend full tests**

Run: mvn -q test

Expected: all backend tests pass with zero failures/errors; record the exact count in the review note.

- [ ] **Step 2: Run frontend full tests and static checks**

From /Users/fs/Desktop/Project/pms-front run:
~~~bash
pnpm test
pnpm typecheck
pnpm build
~~~
Expected: all tests pass, typecheck passes, and production build completes.

- [ ] **Step 3: Run consistency and security checks**

Run the existing OpenAPI, release-consistency, privacy, and audit acceptance scripts. Add /notifications/page, TASK_DUE_SOON, and TASK_OVERDUE to OpenAPI if the repository check requires explicit entries. Verify notification contents, tokens, and secrets do not enter logs.

- [ ] **Step 4: Verify runtime with reminder disabled**

Restart the local backend using the existing run procedure, confirm V15 migrated and no reminder job is registered with enabled=false, then call existing notification endpoints and /api/notifications/page with an authenticated account. Verify deleted-project notifications are absent from page/unread results, terminated-project history is visible, and invalid type returns 400.

- [ ] **Step 5: Verify enabled behavior with deterministic dates**

In a controlled test database create tasks due today+7, today, today-1, today-2, and today+8; run the service twice. Verify exactly one due-soon reminder for today+7, one overdue reminder for today-1, none for the other dates, and no Webhook deliveries. Change the assignee and verify the old notification remains while only the new assignee receives a future eligible reminder.

- [ ] **Step 6: Perform final review before claiming completion**

Review the diff for additive nullable V15, default disabled scheduler, SQL-side filters, isolated in-app writes, enum status usage, mutually exclusive filters with page reset, no v-html, and matching manuals/permission rules. Run git diff --check across both workspaces and report pre-existing dirty files separately from feature changes. Do not claim completion until tests, build, runtime checks, and review all pass.
