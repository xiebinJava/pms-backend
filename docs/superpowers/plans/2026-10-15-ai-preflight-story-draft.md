# AI Workflow Preflight and Story Draft Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make PMS node completion previews explain their blocking prerequisites and make story-node draft fields writable through an explicit AI context contract.

**Architecture:** Keep PMS workflow validation as the source of truth. Add an additive structured `preconditions` field to the existing preview envelope, populated by the development-item completion command from the current node snapshot. Add a component-level `saveContract` to AI context for JSON-backed story workbenches; it describes the existing field-update command without performing a write.

**Tech Stack:** Spring Boot, Java records, Jackson, JUnit 5/AssertJ, TypeScript/Zod contracts, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-15-ai-preflight-story-draft.md`

## Global Constraints

- Preserve the existing `warnings`, `changes`, `refreshScopes`, and preview/execute lifecycle.
- Do not bypass workflow validation or write to PMS during context reads.
- Use the existing allow-listed `development-item.node.field.update` command for story workbench writes.
- Keep current repository changes intact; modify only files needed for this feature.

## Review Focus

- A missing node owner must be reported before execution and point to the owner-update command.
- A missing or inverted node schedule must be reported with actionable schedule details.
- Unfinished node tasks must be visible in preview without exposing unrelated task data.
- Story workbench save contracts must carry the current node version to prevent stale writes.
- Older clients and commands must continue to parse previews without `preconditions`.

### Task 1: Add structured completion preconditions

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/command/CommandPreview.java`
- Modify: `src/main/java/com/brad/pms/integration/ai/api/AiOperationPreviewDTO.java`
- Modify: `src/main/java/com/brad/pms/ai/command/AiOperationService.java`
- Modify: `src/main/java/com/brad/pms/ai/command/development/CompleteDevelopmentItemNodeCommand.java`
- Test: `src/test/java/com/brad/pms/ai/command/development/CompleteDevelopmentItemNodeCommandTest.java`
- Test: `src/test/java/com/brad/pms/ai/command/AiOperationContractAuditTest.java`

**Interfaces:**
- Produces `CommandPreview.Precondition(code, message, blocking, suggestedAction, details)` and additive `preconditions` serialization.
- `CompleteDevelopmentItemNodeCommand.preview` returns preconditions for owner, schedule, and unfinished tasks while retaining its current warning.

- [x] Write failing tests for missing owner, missing schedule, unfinished task count, and backward-compatible preview construction.
- [x] Run the focused command tests and observe the expected failures.
- [x] Add the precondition record, additive DTO field, and persistence round-trip.
- [x] Populate preconditions from `DevelopmentItemWorkflowNodeDTO` without changing execute-time validation.
- [x] Run focused tests, then the backend AI command test slice.

### Task 2: Publish story workbench save contracts

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReader.java`
- Modify: `src/main/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadService.java` (only if contract data needs projection wiring)
- Modify: `packages/pms-contracts/src/operation.ts` in `/Users/fs/Desktop/Project/pms-ai-connector`
- Modify: `packages/pms-client/src/PmsClient.ts` in `/Users/fs/Desktop/Project/pms-ai-connector` (only if type mapping requires it)
- Test: `src/test/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReaderTest.java`
- Test: `tests/contract/schema.test.ts` in `/Users/fs/Desktop/Project/pms-ai-connector`

**Interfaces:**
- Produces a component `saveContract` object with `command`, `itemType`, `itemId`, `nodeId`, `version`, `componentKey`, `variant`, and `writableFields`.
- The contract uses `development-item.node.field.update` and the existing `fieldValues.__components.<componentKey>` payload shape.

- [x] Add a failing reader test asserting the story writing component exposes the current node version and writable fields.
- [x] Run the focused reader test and observe the expected failure.
- [x] Implement a small contract builder for JSON-backed story workbench components; leave unsupported components without a contract.
- [x] Extend connector schemas only where the additive field is explicitly typed; keep unknown fields tolerant for older PMS versions.
- [x] Run backend context tests and connector contract tests.

### Task 3: Review and integration verification

**Files:**
- Modify: none unless verification exposes a contract mismatch.

- [x] Run the full backend targeted AI/orchestrator test command.
- [x] Run the connector unit and contract test suites.
- [x] Build the backend and connector CLI.
- [x] Use the authenticated `pms-cli` to preview a real node completion and verify `preconditions` is present, without executing a write.
- [x] Review the diff for scope, compatibility, and accidental changes before reporting completion.
