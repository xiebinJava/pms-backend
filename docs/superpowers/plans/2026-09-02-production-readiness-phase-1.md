# Production Readiness Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Each task is independently testable and must be reviewed before the next task begins.

**Goal:** Complete the safe, repeatable part of the first production-readiness phase for the single-enterprise PMS deployment: validate production configuration without exposing secrets, rehearse OceanBase migration and recovery locally, run API/browser acceptance, and record the remaining enterprise-owned inputs explicitly.

**Architecture:** Keep application behavior unchanged. Add a fail-closed shell validator for production environment variables and a shell regression test for that validator. Reuse the existing versioned OceanBase migration, backup, restore, preflight, and verification scripts. Treat real enterprise credentials, domains, SMTP, object storage, monitoring, RPO/RTO, and on-call contacts as deployment-time inputs; they must never be committed or inferred.

**Tech Stack:** Bash 5, Spring Boot 3.5/Java 17, OceanBase MySQL mode, Maven, pnpm, Playwright, Docker Compose.

**Spec:** The validator must reject development mode, missing or weak JWT secrets, root database accounts, missing database passwords, wildcard/non-HTTPS CORS, non-HTTPS public URLs, token echo in production, and incomplete SMTP configuration when mail is enabled. It must not print secret values. Successful checks may use synthetic placeholders in tests only; a successful test is not production sign-off.

## Global Constraints

- Use the existing `main` branches in both repositories; do not create a worktree or change business behavior.
- Do not read, print, commit, or upload `.env.oceanbase.local` values or any real credentials.
- Do not overwrite `brad_pms` during restore drills. Restores target an explicitly named empty database only.
- Every task follows test → implementation → verification → review, and the result is recorded in `docs/operations/infrastructure-status.md`.

---

## Task 1: Add a fail-closed production configuration gate

**Files:** `scripts/validate-production-config.sh`, `scripts/validate-production-config.test.sh`, `.env.production.example`, `docs/operations/release-checklist.md`.

- [x] Add a shell regression test that runs in an empty environment and asserts failure for missing production settings, wildcard CORS, HTTP public URL, and incomplete SMTP; assert success for a fully populated synthetic configuration.
- [x] Run the new test before implementing the validator and record the expected failure.
- [x] Implement `validate-production-config.sh` with strict mode, safe diagnostics, required variable checks, length/format validation, root-account rejection, and optional `--require-object-storage`/`--require-smtp` switches.
- [x] Add a redacted production environment template documenting ownership and acceptable values without real secrets.
- [x] Run the shell test, `bash -n`, the existing backend configuration tests, privacy scan, and `git diff --check`.
- [x] Review that no diagnostic can echo a password, JWT, token, or full connection string; then update the release checklist with the command and result.

## Task 2: Execute the local OceanBase migration and recovery rehearsal

**Files:** `docs/operations/drill-records/2026-09-02-production-readiness.md`, `docs/operations/infrastructure-status.md`.

- [x] Confirm the local OceanBase container and `brad_pms` are reachable without printing credentials.
- [x] Run the versioned upgrade twice with the migrator account, then run `enterprise-preflight.sh` and `verify-enterprise-migration.sh`.
- [x] Create a compressed logical backup with checksum and exact row-count metadata in a temporary directory outside Git.
- [x] Verify the backup, restore into a newly created isolated empty database, compare key-table row counts, and remove only the isolated drill database.
- [x] Exercise the documented failure boundary (non-empty restore target or invalid checksum) and capture the safe refusal without touching `brad_pms`.
- [x] Review evidence for idempotency, backup integrity, restore isolation, and cleanup; record commands, timestamps, schema version, and pass/fail results without secrets.

## Task 3: Run application and browser acceptance against OceanBase

**Files:** `docs/operations/drill-records/2026-09-02-production-readiness.md`, `docs/operations/release-checklist.md`.

- [x] Build and start the backend with the local OceanBase profile using the existing start script; start the frontend with its existing local command if it is not already running.
- [x] Verify liveness/readiness and API error contracts without exposing tokens.
- [x] Run backend tests, frontend tests/typecheck/build, API smoke, and the existing desktop/mobile Playwright suites with the local test account.
- [x] Validate logout redirect, login redirect preservation, organization/role access, project list/detail consistency, and manual page navigation.
- [x] Stop only processes started for this rehearsal and capture ports/log locations (not log contents containing secrets).
- [x] Review failures against the previous CI baseline and document any enterprise-only checks that cannot be proven locally.

## Task 4: Close the phase with documentation and commit

- [x] Update `docs/operations/infrastructure-status.md` with the phase status, exact local evidence, current test counts, and explicit external blockers.
- [x] Update release/upgrade checklists so V6/V7/V10 naming and test counts are consistent with code and current `main`.
- [x] Run full proportionate verification: backend tests, frontend tests/typecheck/build, shell syntax, OpenAPI validation, privacy scan, and `git diff --check`.
- [x] Perform a final self-review for secrets, destructive commands, stale counts, and claims that would imply real enterprise production sign-off.
- [x] Commit the phase changes on `main`, push backend and frontend only if frontend changes are required, and report commit IDs plus unresolved enterprise inputs.

## Verification Commands

```bash
bash scripts/validate-production-config.test.sh
bash -n scripts/*.sh docker/*.sh
mvn -q test
./scripts/validate-openapi.sh
cd ../pms-front && pnpm test && pnpm typecheck && pnpm build
```
