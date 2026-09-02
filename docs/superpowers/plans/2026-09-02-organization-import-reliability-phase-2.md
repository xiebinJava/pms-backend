# Organization and Import Reliability Phase 2 Implementation Plan
> **For agentic workers:** REQUIRED SUB-SKILL: Follow this plan task-by-task, keep the user-visible contract backward compatible, and run the listed verification after each checkpoint.

**Goal:** Make organization assignments and CSV/Excel imports reliable for enterprise use while keeping organization ownership independent from employee primary affiliation.

**Architecture:** Keep `sys_org_unit.leader_user_id` as the organization-owner relation and `sys_user_position` as the employee assignment relation. Add append-only organization change history, explicit import-job idempotency metadata, and a server-generated error report endpoint. Preview remains read-only; commit remains one transaction guarded by a row lock.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, OceanBase (MySQL mode), Apache Commons CSV, Apache POI, Vue 3, Ant Design Vue, Vitest/Vue Test Utils.

**Spec:** `docs/business-specification.md` and `docs/superpowers/specs/2026-08-27-enterprise-organization-access-design.md`

## Global Constraints

- OceanBase is the only supported runtime database; do not add H2-specific behavior.
- Organization leader and employee primary/part-time/project affiliations are independent relations.
- Email remains the employee import idempotency key; names are optional display fields.
- Never commit credentials or local `.env` files.
- Preserve existing API response envelopes and Chinese UI terminology.
- Every phase checkpoint must include regression tests, an implementation review, and documentation updates.

## Tasks

1. **Baseline and failing regressions** ✅
   - Inspect current import, organization, position, audit, and UI contracts.
   - Add tests for organization history, leader/primary independence, duplicate import commit idempotency, error-report download, and rollback on a mid-batch failure.

2. **Schema and persistence** ✅
   - Add an OceanBase-compatible Flyway migration for append-only `sys_org_unit_history` and import-job idempotency/status metadata.
   - Add entities/mappers and indexes/foreign keys without mutating existing relation semantics.

3. **Organization reliability** ✅
   - Record create, update, move, deactivate, leader assignment, and primary-affiliation changes in history with before/after snapshots.
   - Expose a read endpoint for an organization’s change history; keep audit log writes intact.
   - Enforce active organization and single-primary invariants at the service boundary.

4. **Import reliability** ✅
   - Make commit idempotent by returning the already-successful result and rejecting conflicting reuse.
   - Ensure failed commits mark the job failed without persisting partial business rows.
   - Add a CSV error-report download API and keep frontend preview/download behavior aligned.
   - Fix duplicate validation so one bad field yields one deterministic error per row.

5. **Verification and delivery** ✅
   - Run unit/integration tests, Flyway validation and OceanBase migration verification, API smoke checks, and frontend typecheck/build/tests.
   - Self-review the diff and update business specification, roadmap, release checklist, and changelog with evidence.
   - Commit and push backend/frontend changes to their current `main` branches only after verification.

## Completion review (2026-09-02)

- Backend `mvn -q test`: 194 tests, 0 failures, 0 errors, 1 skipped.
- Frontend `pnpm test`: 112 passed; `pnpm typecheck` and `pnpm build` passed.
- OceanBase CE 4.3.5 `brad_pms`: V1–V11 applied with `pms_migrator`; a second run verified every checksum and reported no pending migrations.
- `bash scripts/verify-oceanbase.sh` passed using the locally available OceanBase image and `obclient`; no database or business table was dropped.
- Review found and fixed one stale V10 health-controller test expectation and one lock contention path in failed import status handling.
