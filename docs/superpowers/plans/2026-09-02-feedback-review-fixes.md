# Feedback Review Fixes Implementation Plan

> **For agentic workers:** This plan is executed inline with regression gates after each task.

**Goal:** Make Feedback Center V1 consistent with enterprise RBAC/data-scope rules and safe lifecycle/audit behavior without changing its public workflow.

**Architecture:** Keep the existing FeedbackService/FeedbackController boundary. Centralize feedback permission implications in AuthorizationService, derive manager visibility from the existing DataScopeResolver and project/member tables, and emit allowlisted audit snapshots. Keep the frontend aligned with those capabilities and load project context lazily.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, MySQL-compatible SQL, Vue 3, TypeScript, Ant Design Vue, Vitest.

**Spec:** `docs/product-specs/pms-feedback-center.md` and `docs/business-specification.md`.

## Global Constraints

- Do not use an in-process database at runtime; tests use Testcontainers MySQL.
- Preserve `feedback:read`, `feedback:write`, and `feedback:manage` API names.
- Keep reporter access to their own tickets; manager access must obey organization/project data scope.
- Do not put feedback title, content, source URL, client request ID, or resolution text in operation-log snapshots.
- Existing user/project/task/node relationships remain the source of truth.

### Task 1: Regression tests first

**Files:**
- Modify: `src/test/java/com/brad/pms/service/FeedbackServiceTest.java`
- Modify: `src/test/java/com/brad/pms/security/AuthorizationServiceTest.java` (create if absent)
- Modify: `src/test/java/com/brad/pms/config/EnterpriseSchemaMigrationTest.java`
- Modify: `src/test/.../feedback.test.mjs`

- [x] Add tests for manager scope, manage→read/write permission implications, strict task/node consistency, closedAt preservation, reopen note clearing, and allowlisted audit snapshots.
- [x] Run focused tests and confirm each new assertion fails for the current implementation; the failing assertions were then repaired one at a time.

### Task 2: Backend authorization and lifecycle

**Files:**
- Modify: `src/main/java/com/brad/pms/security/AuthorizationService.java`
- Modify: `src/main/java/com/brad/pms/service/FeedbackService.java`
- Modify: `src/main/java/com/brad/pms/controller/FeedbackController.java`

- [x] Implement feedback-only permission implications.
- [x] Apply scoped manager visibility to list/detail/update/reopen.
- [x] Enforce strict task/node relationship and lifecycle timestamp/note rules.
- [x] Replace full entity audit payloads with an allowlist.

### Task 3: Database and frontend consistency

**Files:**
- Modify: `src/main/resources/db/migration/V12__feedback_center.sql`
- Modify: `../pms-front/src/store/user.ts`
- Modify: `../pms-front/src/router/admin-guard.ts`
- Modify: `../pms-front/src/views/feedback/index.vue`
- Modify: `../pms-front/src/api/feedback.ts`

- [x] Add reporter query index.
- [x] Align route/button visibility with effective feedback permissions.
- [x] Remove ambiguous assignee clearing and add project→node/task context loading.

### Task 4: Documentation and verification

- [x] Update product/business specs and review record with the final scope policy.
- [x] Run Maven tests, frontend tests/typecheck/build, and `git diff --check` (full verification gate).

Verification completed on 2026-09-03:

- Backend: `mvn -q test` — passed; Flyway applied all 12 migrations in the Testcontainers MySQL test profile.
- Frontend: `pnpm test` (121 passed), `pnpm typecheck`, and `pnpm build` — passed.
- Backend and frontend: `git diff --check` — passed.
