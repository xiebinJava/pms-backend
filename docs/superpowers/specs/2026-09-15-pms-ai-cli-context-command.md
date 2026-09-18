# PMS AI CLI Context and Command Specification

**Goal:** Give Work Helper a safe, structured command interface for understanding the current PMS page and previewing or executing project-management operations.

## Codex patterns to borrow

- Keep the executable entry point thin and centralize top-level subcommand registration, as Codex does in its Rust CLI entry point: <https://github.com/xiebinJava/codex/blob/main/codex-rs/cli/src/main.rs>.
- Keep packaging or launcher concerns separate from command implementation, as shown by the `codex-cli` package: <https://github.com/xiebinJava/codex/blob/main/codex-cli/package.json>.
- Organize implementation by capability modules rather than putting every operation in one flat command directory.
- Support both interactive use and machine-readable, non-interactive execution so Work Helper can call the same command contract.

## Scope

### Context capabilities

The first version supports these page contexts:

- `project-list`: active filters, summary counters, visible project rows, pagination.
- `project-dashboard`: filters, dashboard counters, chart source data, risk and milestone summaries.
- `project-detail`: project profile, current node, node fields, members, followers, tasks, progress and version metadata.
- `workflow-template`: selected template/version, nodes, field definitions and layout settings.

Every context snapshot includes `pageType`, `route`, `projectId`, `nodeId`, `capturedAt`, `version` and a redacted `data` object.

### Commands

The first command capabilities are:

- `context.inspect`: read the current structured page context.
- `task.create`: preview and create a task under a project/node.
- `task.assign`: preview and assign a task to a project member.
- `operation.execute`: execute a previously approved preview by operation ID. This is an execution lifecycle endpoint, not an AI tool exposed to the model.

### Safety rules

- Commands are selected from a backend allowlist; no arbitrary shell execution is exposed.
- Every write command supports preview mode and requires explicit confirmation before execution.
- Permission checks run during both preview and execution.
- Execution revalidates context version and entity version to prevent stale writes.
- Each execution receives an idempotency key and writes an audit record.
- Context output excludes secrets, tokens, passwords and unrelated records.
- Frontend-to-Work Helper authentication uses a short-lived, scoped PMS AI delegation token; the token is transported out-of-band and never included in model-visible context.
- Work Helper may inspect context and request previews, but only the authenticated PMS frontend or `pms-cli` may execute a confirmed operation.

## Non-goals

- Letting the model call the database directly.
- Letting the model execute operating-system commands.
- Implementing every PMS operation in the first release.
- Replacing the existing `TaskService`, `ProjectService` or `NodeService` business rules.
