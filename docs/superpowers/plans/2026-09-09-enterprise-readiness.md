# Enterprise Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Each stage ends with review and verification before the next stage begins.

**Goal:** Strengthen the PMS from a project-level workflow tool into a reliable enterprise project-management platform without adding new workbench surfaces before the existing data and governance paths are trustworthy.

**Architecture:** Deliver the work in four gated stages. Stage 1 adds optimistic concurrency and explicit conflict handling to project/task/workbench saves. Stage 2 closes reminders and acceptance-defect workflows. Stage 3 adds cross-project management views only as a demo first; formal implementation requires explicit user approval. Stage 4 hardens visibility, knowledge reuse, and production operations.

**Tech Stack:** Spring Boot 3.5, MyBatis-Plus, Flyway, H2 migration tests, Vue 3, TypeScript, Ant Design Vue, Node test runner, Vite.

**Spec:** Approved enterprise-readiness plan from the 2026-09-09 conversation; current code baseline is the `main` branch after commits `5ad0a75` and `8a4a947`.

## Global Constraints

- Preserve the current project workflow, iteration-plan model, independent story/task model, and explicit node confirmation behavior.
- Do not reintroduce milestone-to-task or story-to-task associations.
- Every behavior change must have a failing test before production code is written.
- Complete a code review and full verification after each stage.
- Do not implement Stage 3 after the demo without explicit user approval.
- Keep frontend and backend commits separate and keep database changes forward-only through Flyway migrations.

---

### Stage 1: Concurrent editing and save reliability

**Outcome:** A stale editor receives a conflict instead of silently overwriting newer data.

**Files:**
- Modify backend `src/main/java/com/brad/pms/dto/request/ProjectUpdateCmd.java`.
- Modify backend `src/main/java/com/brad/pms/dto/request/TaskUpdateCmd.java`.
- Modify backend project/task services and controllers.
- Modify backend node workbench update commands and services that already persist a versioned baseline.
- Modify backend exception/response tests where conflict payloads are asserted.
- Modify frontend `src/types/domain.ts`, `src/api/project.ts`, `src/api/task.ts`, and all node workbench payload mappings.
- Modify frontend save error handling and conflict presentation in the affected project-detail components.

**Interfaces:**
- Update commands accept the client-observed `version`.
- Successful updates return the incremented version already exposed by the DTO.
- A stale update returns HTTP 409 with the existing business-error response shape and a message that identifies the record as changed by another user.

- [ ] Write backend tests proving project updates reject a stale version and accept the current version.
- [ ] Run the focused backend tests and verify the new tests fail for the missing version check.
- [ ] Write backend tests proving task updates reject a stale version and do not execute follow-up link, notification, or audit mutations.
- [ ] Implement conditional updates and affected-row checks using the existing MyBatis-Plus optimistic-lock support.
- [ ] Run focused backend tests, then the full `mvn test` suite.
- [ ] Write frontend tests proving a 409 save preserves the local editor state and exposes a recoverable conflict message.
- [ ] Add version fields to update payloads and synchronize returned versions after successful saves.
- [ ] Add conflict UI with explicit refresh/reload action; never replace unsaved local data automatically.
- [ ] Run `pnpm test`, `pnpm run typecheck`, and `pnpm run build`.
- [ ] Review backend and frontend diffs, migration/API compatibility, and all update call sites.
- [ ] Commit backend and frontend Stage 1 changes separately.

**Gate:** Do not start Stage 2 until the code review reports no critical or important issues and both repositories pass their full test/build checks.

---

### Stage 2: Reminders and acceptance-defect closure

**Outcome:** Due work is surfaced reliably and acceptance problems can be tracked to closure.

**Files:**
- Modify backend `TaskReminderService`, reminder properties/job, notification APIs, and related tests.
- Modify backend acceptance defect entities, migration, mapper, service, controller, and DTOs.
- Modify frontend `AcceptanceWorkbench.vue`, acceptance API/types, notification configuration surfaces, and localization strings.
- Update the user manual and operations runbook with reminder and defect rules.

**Interfaces:**
- Reminder scans support catch-up after downtime, dedupe, retry-safe insertion, and configurable overdue summaries.
- Defect lifecycle supports owner, severity, status, verification result, closure reason, and acceptance-item association.
- Acceptance completion blocks unresolved defects unless a documented waiver is recorded by an authorized user.

- [ ] Add failing tests for reminder catch-up, duplicate prevention, and long-overdue summary selection.
- [ ] Implement the smallest reliable scan/backfill path and configurable notification behavior.
- [ ] Add failing migration/service/controller tests for defect creation, assignment, status transitions, verification, and closure.
- [ ] Implement defect persistence and acceptance completion gating.
- [ ] Add frontend tests for the complete defect lifecycle and blocked completion feedback.
- [ ] Implement the acceptance UI and notification settings while preserving the current explicit-save behavior.
- [ ] Run backend and frontend full suites plus production build.
- [ ] Review business rules against acceptance and release node behavior.
- [ ] Commit backend, frontend, and documentation changes separately where appropriate.

**Gate:** Do not start Stage 3 implementation until Stage 2 review and verification are complete.

---

### Stage 3: Cross-project management demo and approval gate

**Outcome:** A working demo makes the proposed enterprise management views concrete before the production data model is changed.

**Demo scope:**
- Portfolio summary with project status, progress, overdue risk, and current-node health.
- Cross-project dependency list.
- Resource load by person and time window.
- Iteration commitment versus completed story points.
- Baseline versus actual schedule variance.

**Files:**
- Prefer new isolated demo files under the frontend public/demo surface and focused demo tests.
- Do not add production controllers, migrations, or persistent tables before approval.
- Add a short demo design note describing the displayed metrics and their source assumptions.

- [ ] Write demo tests for metric calculation and empty/loading/error states.
- [ ] Build the demo with representative local data and clear labels separating sample data from production data.
- [ ] Verify desktop and narrow-width layouts in the browser.
- [ ] Show the demo to the user and record approval or requested changes.
- [ ] Stop here until explicit approval is received.

**Gate:** User approval is required before any Stage 3 production API, schema, or dashboard implementation is started.

---

### Stage 3 production implementation: portfolio and capacity management

**Outcome:** Approved demo metrics become auditable, permission-aware production views.

**Files:**
- Add forward-only backend migrations, query DTOs, aggregate services, controllers, and tests.
- Add frontend APIs, dashboard components, filters, localization, and tests.
- Reuse project, node, task, story, and iteration-plan sources instead of duplicating writable state.

- [ ] Define approved metric formulas and permission scope from the demo feedback.
- [ ] Add failing backend aggregation tests for portfolio health, dependencies, capacity, commitment, and schedule variance.
- [ ] Implement read-only aggregate APIs with pagination and bounded date ranges.
- [ ] Add failing frontend tests for filters, sorting, empty states, and permission-limited results.
- [ ] Implement the approved dashboard and link each metric to its source project or iteration.
- [ ] Run full verification and review data consistency with project detail.

---

### Stage 4: Visibility, knowledge reuse, and production hardening

**Outcome:** The system has enterprise-appropriate access controls and a deployable operational baseline.

**Files:**
- Modify project permission policy, data-scope queries, and frontend visibility controls.
- Extend knowledge asset DTOs, APIs, UI, and migrations to support document links, versions, and search metadata.
- Modify rate limiting, import/file processing, observability, backup, and release documentation.
- Update `docs/operations/infrastructure-status.md` and `docs/operations/scaling-readiness.md` to match the V41 migration baseline.

- [ ] Add failing permission tests for company-wide, organization-scoped, member-only, and private project visibility.
- [ ] Implement optional project visibility without changing the current default behavior unexpectedly.
- [ ] Add failing knowledge-asset tests for document linkage, version display, and reuse/search metadata.
- [ ] Implement knowledge links and version-aware presentation without restoring removed archive/source sections.
- [ ] Add operational tests and runbook steps for shared rate limiting, async imports, backups, recovery drills, RPO/RTO, and capacity evidence.
- [ ] Run full verification and perform a release-readiness review.

**Final definition of done:** All four stages have passing automated verification, reviewed API/schema changes, updated documentation, and an explicit record of any deferred enterprise capability.
