# Configurable Project Workflows — Implementation Plan

> **Execution:** Follow `superpowers:executing-plans` and `superpowers:test-driven-development`. Complete each backend/frontend slice with focused red-green tests before moving on. Review the cross-repository contract at each gate.

**Goal:** Deliver versioned, type-aware configurable sequential workflows with a draggable visual editor while preserving the nine-stage production flow and every existing workbench.

**Architecture:** Backend owns project types, template drafts/publications, immutable version snapshots, node/module/field definitions, project bindings, generic values and validation. Frontend uses those APIs to render a visual template canvas and node inspector; project creation selects a published template; project detail renders each node's component attachments and generic fields. Specialized module services remain owners of their data and validation.

**Spec:** `docs/superpowers/specs/2026-09-12-configurable-project-workflows.md`.

**Repos:** Backend `C:/Users/Brad/Desktop/MyWorker/project/.worktrees/pms-backend`; frontend `C:/Users/Brad/Desktop/MyWorker/project/.worktrees/pms-front`. The original checkouts remain untouched; create a feature branch in each worktree only after final verification.

## Global constraints

- TDD: write a focused failing test, run it to observe the expected failure, implement the smallest behavior, then run it green before broad verification.
- Preserve the primary checkout and the `latest` working copies; they contain pre-existing user changes.
- Sequential-only v1. Published versions immutable. Existing projects keep the version bound during migration.
- Stable compatibility node keys and all existing specialized workbench APIs/data/validators must survive.
- Template/project-type configuration is permission-protected and audited; file fields follow existing access control.
- Do not delete remote branches. Initial scan found no local branch other than `main`.
- Full backend Maven suite currently has a known infrastructure limitation: Testcontainers cannot reach Docker when Maven runs in the Maven container. Run focused non-container tests and compile checks, plus any integration test feasible with Docker socket access; report the full-suite caveat accurately.

## Implementation status

- Implemented the V42 schema, compatibility seed/backfill, versioned type/template APIs, project binding, component-identity completion checks, custom field persistence and attachment ACLs.
- Implemented the visual configuration editor, keyboard and pointer ordering, per-type default, all published-version project choices, fixed-block preview, and configurable project/node detail fields.
- Review follow-ups addressed: custom-key specialist components, repeated component placement, older published-version selection, backfill of soft-deleted projects, optimistic locking for publish races, draft saves with incomplete required fields, optional-field clearing, and faithful node-detail preview.
- Final verification: frontend `corepack pnpm test` — 290/290 passing; `corepack pnpm build` — passing. Backend targeted Maven regression — 119 tests across 17 suites, 0 failures/errors. Visual QA covered desktop/mobile layouts, node preview, keyboard reorder, and pointer drag-and-drop; no browser console errors. `git diff --check` is clean in both worktrees.
- Remaining verification limitation: the full backend suite previously reported 359 tests, with 3 failures, 29 errors, and 3 skipped due to Testcontainers being unable to reach Docker from inside the Maven container. Migration execution against a real MySQL database was not completed. Do not treat these as passing checks.

## Stage 1 — Backend template model, migration, and APIs

**Expected surface:** `V42` migration; entities/mappers/DTOs/services/controllers for project types, templates, versions, node definitions, module attachments and custom fields; permission/audit integration; project create command/model/service; tests.

- [ ] Add service/API tests for listing project types and available published templates, default resolution, unauthorized draft/publish, invalid node order, immutable published versions, and project creation default/override.
- [ ] Run targeted tests and confirm they fail because the template APIs/model do not exist.
- [ ] Add `V42__configurable_workflow_templates.sql` with tables and constraints for types, templates, versions, ordered nodes, module attachments, field definitions/options, generic values, and project binding columns. Backfill compatibility type/version without deleting or recreating project nodes.
- [ ] Test migration against existing project/node/workbench fixtures; assert IDs, statuses, owners, dates and row counts remain unchanged and all projects receive a binding.
- [ ] Seed the nine-node compatibility template with current keys and module identities. Add type/default seed and safe re-run behavior where applicable.
- [ ] Implement project-type/default and workflow template read/draft-save/publish/preview APIs. Validate unique order/keys, field schema/options, allowed module IDs, and published-template immutability transactionally.
- [ ] Add workflow admin permission code, role seed/backfill and audit events for create/edit/publish/default changes.
- [ ] Extend project creation to persist `projectTypeId` and bound `workflowTemplateVersionId`; default when omitted, allow valid published override, and initialize nodes from that bound version.
- [ ] Run focused backend unit tests and compile. Keep Testcontainers-dependent migration/API tests identified separately if Docker is unavailable inside Maven.

**Gate:** Review schema keys/FKs, migration backfill and permission checks before beginning frontend integration.

## Stage 2 — Backend configurable node runtime and custom field persistence

**Expected surface:** `NodeService`, project-node initialization/DTOs, completion routing, custom field value service/API, attachment metadata/storage integration, focused tests.

- [ ] Add tests proving nodes initialize from the project's immutable template version, preserve order/module metadata, and do not use later draft changes.
- [ ] Add tests proving specialized module validation follows declared module identity after node reorder/rename, and generic required fields block completion while optional fields do not.
- [ ] Run those tests red before changing runtime behavior.
- [ ] Replace default-only node initialization with template-version materialization while keeping seeded stable node keys and existing project rows untouched.
- [ ] Resolve completion validators by module identity (preserve current requirement/design/plan/development/acceptance/release/review/knowledge rules). Reject unknown module IDs safely. Keep generic required-field validation in addition to fixed owner/schedule/task behavior.
- [ ] Add generic value read/write with field-type, select-option, person, project/node access and requiredness validation. Keep canonical Project Basic Info fields mapped to the project model.
- [ ] Add attachment-field upload/reference lifecycle using existing file storage, size/type validation and node/project ACL; test unauthorized access and orphan/duplicate handling.
- [ ] Verify node list/detail DTOs expose immutable template node metadata/components/custom field definitions and frontend-required fixed block metadata.

**Gate:** Existing `NodeServiceCompleteTest`, owner defaults, specialized workbench tests, and focused new runtime tests pass.

## Stage 3 — Frontend API/type and project creation integration

**Expected surface:** frontend `src/types/domain.ts`, workflow/project API modules, project list create modal, localization and source-level/API tests.

- [ ] Add failing tests for template defaults by project type, switching among published choices, missing-default error state, canonical project type vs project level separation, and submitted template binding.
- [ ] Run the focused tests red.
- [ ] Add typed DTOs and APIs for project types, published templates and versions, draft editing/publishing/preview, and node custom values.
- [ ] Add project type and template selectors to project creation; preselect the type's default, permit authorized switch, submit both IDs, and retain current project-level behavior.
- [ ] Add loading/error/empty states and localization; ensure backend validation messages remain actionable.
- [ ] Run frontend focused tests, typecheck, and build.

## Stage 4 — Visual workflow-template editor

**Expected surface:** new Configuration Management workflow routes/views/components, navigation/permission filtering, editor styles, visual interaction tests.

- [ ] Add failing tests for visual node ordering, click-to-edit inspector, built-in component palette, custom-field configuration, preview, unsaved-draft protection, keyboard reordering, and publish/default flows.
- [ ] Run the tests red before implementation.
- [ ] Implement template/type list, sequential connected canvas, draggable node cards, inspector for node name/description/components/fields, field palette (text, textarea, number, date, single/multi-select, person, attachment), and Project Basic Info configuration.
- [ ] Pin and visibly distinguish owner, schedule, and task-board fixed blocks; do not expose remove controls for them.
- [ ] Add preview that renders the same node-detail structure and current specialized modules used by project detail. Saving a draft must preserve the current published version; publish creates an immutable version.
- [ ] Add default-template-per-type management and safe confirmation for deleting/reordering nodes in drafts. Do not silently mutate already published/project-bound versions.
- [ ] Add pointer drag plus keyboard-accessible move controls and responsive canvas/inspector layout. Use the repo's established org-canvas drag patterns where suitable.
- [ ] Run editor interaction tests, frontend full tests, typecheck and production build; manually verify the route in the running app if a browser test harness is available.

## Stage 5 — Project detail rendering, compatibility, and end-to-end verification

**Expected surface:** `src/views/project/detail/index.vue`, `workflow.ts`, node workbench placement, generic-field renderer, APIs, regression tests, docs.

- [ ] Add failing tests asserting all nine compatibility modules render from node metadata (not fixed node-key checks), and custom fields render/save/validate from definitions while fixed owner/schedule/task blocks remain present.
- [ ] Run tests red.
- [ ] Replace hardcoded component placement branches with module-identity rendering. Keep existing node-specific APIs and validation semantics intact.
- [ ] Render Project Basic Info as canonical project fields per configured visibility/order/requiredness; render generic custom fields and values for other nodes.
- [ ] Verify template binding labels/version in project details; ensure no client-side update can rebind a project.
- [ ] Add regression tests for current nine-node project visual, node completion gates, existing projects with pre-migration-like DTOs, attachment ACL, and unknown module fallback.
- [ ] Run frontend full tests/build and backend focused tests/compile. Attempt Docker-backed Testcontainers suite if Maven can be given the Docker socket; otherwise record the precise infrastructure error and do not claim it passed.
- [ ] Review both worktree diffs, migration safety, API/permission behavior, lint/type/build output and browser rendering. Summarize changed paths and verification status.

## Initial verification baseline

- Frontend: `corepack pnpm test` — 260/260 passing; `corepack pnpm build` — passing before feature work.
- Backend: source and test compilation succeeded under Maven 3.9.9 / Java 17 container. Full tests yielded 359 run, 3 failures, 29 errors, 3 skipped because Testcontainers could not find a Docker environment from inside that container; most failures were Spring context cascades. Docker works on the host.
