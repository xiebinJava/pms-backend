# PMS Feedback Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an authenticated enterprise feedback center with a ticket workflow, triage/assignment, audit history, and a matching frontend page.

**Architecture:** Keep feedback tickets separate from project comments. The backend owns validation, scope checks, state transitions, optimistic locking, idempotency, and audit history; the frontend consumes the ticket API and renders a single responsive feedback center for submitters and feedback managers.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, Flyway, OceanBase MySQL mode (H2 only in tests), Vue 3, TypeScript, Ant Design Vue, Pinia, Node test runner.

**Spec:** `docs/product-specs/pms-feedback-center.md`

## Global Constraints

- V1 is for authenticated internal enterprise users only; no anonymous feedback.
- Project comments remain unchanged and are not reused as feedback tickets.
- All user-facing status/type/priority labels are Chinese; enum codes are lower-case in secondary text where the existing UI displays codes.
- OceanBase is the runtime database; add one versioned Flyway migration and update readiness/docs to the new version.
- Every write path must use RBAC, validate context ownership, record an operation log, and preserve history.
- No production implementation code is written before a corresponding failing test is observed.

### Task 1: Backend schema, enums, entities, and permission seed

**Files:**
- Create: `src/main/resources/db/migration/V12__feedback_center.sql`
- Create: `src/main/java/com/brad/pms/common/enums/FeedbackStatus.java`
- Create: `src/main/java/com/brad/pms/common/enums/FeedbackType.java`
- Create: `src/main/java/com/brad/pms/common/enums/FeedbackPriority.java`
- Create: `src/main/java/com/brad/pms/entity/FeedbackTicketDO.java`
- Create: `src/main/java/com/brad/pms/entity/FeedbackHistoryDO.java`
- Modify: `src/main/java/com/brad/pms/security/PermissionCode.java`
- Modify: `src/main/java/com/brad/pms/config/EnterpriseDataMigration.java`
- Modify: `src/main/java/com/brad/pms/controller/HealthController.java`
- Test: `src/test/java/com/brad/pms/common/enums/FeedbackStatusTest.java`
- Test: `src/test/java/com/brad/pms/config/EnterpriseSchemaMigrationTest.java`

**Interfaces:**
- `FeedbackStatus.canTransition(String from, String to): boolean`
- Permission constants `FEEDBACK_READ`, `FEEDBACK_WRITE`, `FEEDBACK_MANAGE`.

- [x] **Step 1: Write the failing transition and schema assertions.** Assert the approved transitions, rejection of invalid transitions, and presence of both tables, their `version`/`deleted` columns, and idempotency indexes.
- [x] **Step 2: Run `mvn -q -Dtest=FeedbackStatusTest,EnterpriseSchemaMigrationTest test` and verify failure because the types/migration do not exist.**
- [x] **Step 3: Add the three enums, two MyBatis entities, V12 schema, permission constants, and migration seed bindings.** Update the readiness expected migration from 11 to 12 and keep V12 OceanBase/H2 compatible.
- [x] **Step 4: Run the focused tests and verify they pass.**
- [x] **Step 5: Review the schema for foreign keys, soft delete, optimistic locking, unique ticket/client keys, and indexes before continuing.**

### Task 2: Backend API, service, idempotency, and authorization

**Files:**
- Create: `src/main/java/com/brad/pms/mapper/FeedbackTicketMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/FeedbackHistoryMapper.java`
- Create: `src/main/java/com/brad/pms/dto/request/FeedbackCreateCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/FeedbackUpdateCmd.java`
- Create: `src/main/java/com/brad/pms/dto/response/FeedbackTicketDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/FeedbackHistoryDTO.java`
- Create: `src/main/java/com/brad/pms/service/FeedbackService.java`
- Create: `src/main/java/com/brad/pms/controller/FeedbackController.java`
- Test: `src/test/java/com/brad/pms/controller/FeedbackPermissionAnnotationTest.java`
- Test: `src/test/java/com/brad/pms/service/FeedbackServiceTest.java`

**Interfaces:**
- `POST /feedback/tickets` accepts title/content/type/priority/context/clientRequestId.
- `GET /feedback/tickets` returns `PageResult<FeedbackTicketDTO>`.
- `GET /feedback/tickets/{id}` returns ticket plus history.
- `PATCH /feedback/tickets/{id}` accepts status/priority/assigneeId/resolutionNote/version.
- `POST /feedback/tickets/{id}/reopen` accepts version and note.

- [x] **Step 1: Write failing service/controller tests** for permission annotations, same-user idempotent create, invalid transition, non-manager access to another user's ticket, and version conflict.
- [x] **Step 2: Run the focused tests and verify the expected missing-class/method failures.**
- [x] **Step 3: Implement mappers, validated DTOs, service scope rules, status transition validation, context checks using existing project/task/node services, idempotent create, history append, and operation-log writes.**
- [x] **Step 4: Implement the controller with the exact endpoints and `@RequirePermission` values from the spec.**
- [x] **Step 5: Run focused backend tests plus `mvn -q test`; fix regressions without weakening assertions.**
- [x] **Step 6: Review API responses, error codes, SQL paging, and sensitive-field logging.**

### Task 3: Frontend API, route, navigation, and feedback center UI

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-front/src/api/feedback.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/feedback/index.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/feedback/feedback.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/router/index.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/layout/Index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/zh-CN.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/en-US.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/styles/fs-insight.css`

**Interfaces:**
- API functions `listFeedback`, `getFeedback`, `createFeedback`, `updateFeedback`, `reopenFeedback` mirror the backend paths.
- Route `/feedback` uses `feedback:read`; the page uses `feedback:manage` to expose manager controls.

- [x] **Step 1: Write failing source tests** for the route, navigation entry, API paths, status labels, and presence of submit/list/detail interactions.
- [x] **Step 2: Run `pnpm test -- src/views/feedback/feedback.test.mjs` and verify failure because the page/API/route do not exist.**
- [x] **Step 3: Add domain types, API functions, route metadata, a distinct feedback navigation icon, and Chinese/English translations.**
- [x] **Step 4: Implement the page with a responsive filter bar, ticket table, submit modal, detail drawer, history timeline, and manager-only triage controls.**
- [x] **Step 5: Add visual styles using existing PMS tokens; do not alter existing project/comment behavior.**
- [x] **Step 6: Run `pnpm test`, `pnpm typecheck`, and `pnpm build`; review desktop and narrow-screen source contracts.**

### Task 4: Documentation, integration checks, and release review

**Files:**
- Modify: `docs/business-specification.md`
- Modify: `docs/user-manual.md`
- Modify: `docs/operations/infrastructure-status.md`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/docs/user-manual.md` if present, otherwise add the feedback-center usage section to the existing frontend docs location.
- Test: existing backend/frontend suites and `git diff --check`.

- [x] **Step 1: Add the feedback-center business rules, permission matrix, state machine, and user instructions to the docs.**
- [x] **Step 2: Update migration references from V1–V11 to V1–V12 where they describe the current schema.**
- [x] **Step 3: Run backend and frontend full verification commands, including schema migration tests and existing manual/navigation tests.**
- [x] **Step 4: Self-review against every section of `docs/product-specs/pms-feedback-center.md`; record findings in `docs/superpowers/reviews/2026-09-02-feedback-center.md`.**
- [x] **Step 5: Run `git diff --check` and report any unresolved external-environment requirements separately (real OceanBase deployment, credentials, or Playwright browser execution).**

---

## Self-review checklist

- [x] Ticket creation is idempotent per reporter/client request ID.
- [x] Only feedback managers can view and mutate other users' tickets.
- [x] Context IDs are validated against their project and readable scope.
- [x] State transitions and optimistic locking are enforced server-side.
- [x] Every mutation appends feedback history and an operation audit entry.
- [x] V12 is reflected in readiness and operational documentation.
- [x] Frontend uses existing visual tokens, Chinese primary labels, and responsive layout.
- [x] Existing project comments and authentication flows still pass their tests.
