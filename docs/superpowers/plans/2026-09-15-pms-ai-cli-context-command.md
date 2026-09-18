# PMS AI CLI Context and Command Implementation Plan

> Implementation status (2026-09-15): Tasks 1–6 are implemented and verified. The optional `pms-cli` remains intentionally deferred until the authenticated UI flow is accepted; Task 8 still needs a valid-account browser E2E drill.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Codex-inspired, safe PMS command interface that lets Work Helper inspect the current page and preview or execute project-management operations.

**Architecture:** The PMS backend owns structured context, typed commands, permissions, transactions, durable operation previews and audit logs. Work Helper translates natural language into read/preview tool calls, while the frontend sends the current page identity and renders previews. After confirmation, the authenticated PMS frontend or a thin `pms-cli` client executes the operation through the backend; Work Helper never receives a write-capable PMS credential.

**Tech Stack:** Existing Spring Boot/Java PMS backend, existing Work Helper Python service, existing Vue frontend, JUnit/Spring tests, Python pytest, and a small Picocli-based terminal client only after the backend contract is stable.

**Spec:** `docs/superpowers/specs/2026-09-15-pms-ai-cli-context-command.md`

## Global Constraints

- The backend remains the source of truth for PMS data and business rules.
- No AI path may execute arbitrary shell commands or SQL.
- Write operations must support preview, permission checks, optimistic version checks and audit logging.
- Context payloads must be structured JSON and must exclude secrets and unrelated records.
- Existing `TaskService`, `ProjectService`, `NodeService`, permission services and audit services remain the execution primitives.
- Follow Codex's thin launcher, centralized subcommand registration and capability-module separation.
- Reuse the existing `UserContext`/`LoginUser` authentication model; do not introduce an undefined `AuthenticatedUser` abstraction in the first version.
- Use `mvn` for backend verification because this repository does not contain a Maven wrapper.

---

### Task 1: Define the command and context contracts

**Files:**
- Create: `src/main/java/com/brad/pms/ai/context/PageContextType.java`
- Create: `src/main/java/com/brad/pms/ai/context/PageContextRequest.java`
- Create: `src/main/java/com/brad/pms/ai/context/PageContextSnapshot.java`
- Create: `src/main/java/com/brad/pms/ai/command/CommandName.java`
- Create: `src/main/java/com/brad/pms/ai/command/CommandPreviewRequest.java`
- Create: `src/main/java/com/brad/pms/ai/command/OperationExecuteRequest.java`
- Create: `src/main/java/com/brad/pms/ai/command/CommandPreview.java`
- Create: `src/main/java/com/brad/pms/ai/command/CommandResult.java`
- Test: `src/test/java/com/brad/pms/ai/AiCommandContractTest.java`

**Interfaces:**

```java
public record PageContextRequest(
    PageContextType pageType,
    String route,
    Long projectId,
    Long nodeId,
    Map<String, Object> pageState
) {}

public record CommandPreviewRequest(
    CommandName name,
    Map<String, Object> arguments,
    String contextId,
    String contextVersion
) {}

public record OperationExecuteRequest(
    String operationId,
    String idempotencyKey
) {}
```

- [ ] Add tests proving page types and command names serialize to stable kebab-case values.
- [ ] Run `mvn -q -Dtest=AiCommandContractTest test` and verify the new contract test passes.
- [ ] Keep idempotency keys off preview requests and require them only for operation execution.
- [ ] Treat `pageState` as untrusted hints; the backend must reload authoritative data by ID.

### Task 2: Build page context assemblers

**Files:**
- Create: `src/main/java/com/brad/pms/ai/context/PageContextAssembler.java`
- Create: `src/main/java/com/brad/pms/ai/context/ProjectListContextAssembler.java`
- Create: `src/main/java/com/brad/pms/ai/context/ProjectDashboardContextAssembler.java`
- Create: `src/main/java/com/brad/pms/ai/context/ProjectDetailContextAssembler.java`
- Create: `src/main/java/com/brad/pms/ai/context/WorkflowTemplateContextAssembler.java`
- Create: `src/main/java/com/brad/pms/ai/context/PageContextService.java`
- Test: `src/test/java/com/brad/pms/ai/context/PageContextServiceTest.java`

**Interfaces:**

```java
public interface PageContextAssembler {
    PageContextType supports();
    PageContextSnapshot assemble(PageContextRequest request);
}
```

- [ ] Add one test per supported page type with representative project/node data.
- [ ] Verify project detail context includes current node, members, tasks, progress and entity versions.
- [ ] Verify list and dashboard contexts cap visible rows and chart items to a documented limit.
- [ ] Verify sensitive fields and unrelated projects are absent from the snapshot.
- [ ] Reuse the current `UserContext` and permission services while assembling data.
- [ ] Run `mvn -q -Dtest=PageContextServiceTest test` and verify all page assemblers pass.

### Task 3: Add the command registry and preview executor

**Files:**
- Create: `src/main/java/com/brad/pms/ai/command/PmsCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/PmsCommandRegistry.java`
- Create: `src/main/java/com/brad/pms/ai/command/CommandPreviewService.java`
- Create: `src/main/java/com/brad/pms/ai/command/CommandExecutionService.java`
- Create: `src/main/java/com/brad/pms/ai/command/AiOperationService.java`
- Create: `src/main/java/com/brad/pms/entity/AiOperationDO.java`
- Create: `src/main/java/com/brad/pms/mapper/AiOperationMapper.java`
- Create: `src/main/java/com/brad/pms/ai/command/task/CreateTaskCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/task/AssignTaskCommand.java`
- Create: `src/main/resources/db/migration/V43__ai_command_operations.sql`
- Test: `src/test/java/com/brad/pms/ai/command/PmsCommandRegistryTest.java`

**Interfaces:**

```java
public interface PmsCommand {
    CommandName name();
    CommandPreview preview(CommandPreviewRequest request);
    CommandResult execute(AiOperationDO operation);
}

public interface PmsCommandRegistry {
    PmsCommand require(CommandName name);
    Set<CommandName> list();
}
```

- [ ] Add registry tests for `context.inspect`, `task.create`, `task.assign` and `operation.execute`.
- [ ] Implement `task.create` preview by resolving project, node, assignee and due date without writing.
- [ ] Implement `task.assign` preview by validating task membership and assignment permissions.
- [ ] Keep `context.inspect` in `PageContextService`; keep `operation.execute` in `AiOperationService`, not in the command registry.
- [ ] Persist preview arguments, user ID, context version, expiry, status and idempotency key in `AiOperationDO`; do not use Work Helper memory or an in-memory backend map as the source of truth.
- [ ] Reuse existing `TaskService`, `ProjectService`, `NodeService` and permission services during execution. Map `task.create` to the existing `TaskCreateCmd` instead of duplicating task rules.
- [ ] Return refresh scopes such as `project-detail`, `task-board` and `project-dashboard` in every successful result.
- [ ] Run `mvn -q -Dtest=PmsCommandRegistryTest test` and verify preview performs no business write while persisting only the operation preview.

### Task 4: Expose the AI command HTTP API

**Files:**
- Create: `src/main/java/com/brad/pms/controller/AiContextController.java`
- Create: `src/main/java/com/brad/pms/controller/AiCommandController.java`
- Create: `src/main/java/com/brad/pms/controller/AiDelegationController.java`
- Create: `src/main/java/com/brad/pms/ai/api/AiCommandErrorHandler.java`
- Test: `src/test/java/com/brad/pms/controller/AiCommandControllerTest.java`

**Endpoints:**

```text
POST /api/ai/context/inspect
POST /api/ai/commands/preview
POST /api/ai/operations/{operationId}/execute
GET  /api/ai/commands
POST /api/ai/delegation
```

- [ ] Require the existing `UserContext` authenticated user for every endpoint.
- [ ] Map permission, stale-context, validation and idempotency failures to stable error codes.
- [ ] Store previews with a short expiration and bind them to the authenticated user.
- [ ] Issue a short-lived, scope-limited AI delegation token for Work Helper context/preview calls; never include the token in `ChatContextRequest.goal` or other model-visible fields.
- [ ] Let operation execution accept the normal PMS user authentication and the operation ID; do not expose write credentials to Work Helper.
- [ ] Add controller tests for success, permission denial, stale version and duplicate execution.
- [ ] Run `mvn -q -Dtest=AiCommandControllerTest test` and verify the HTTP contract.

### Task 5: Add Work Helper PMS tools

**Files:**
- Modify: `/Users/fs/Desktop/Project/work-helper/app/tools/registry.py`
- Create: `/Users/fs/Desktop/Project/work-helper/app/tools/pms.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/api/routes/tools.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/api/routes/chat.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/api/schemas/chat.py`
- Create: `/Users/fs/Desktop/Project/work-helper/tests/test_pms_tools.py`

**Interfaces:**

```python
def pms_context_inspect(context: dict) -> dict: ...
def pms_command_preview(command: str, arguments: dict, context_id: str) -> dict: ...
```

- [ ] Register only the two PMS tools; do not expose a generic shell tool or a write-capable PMS tool to the model.
- [ ] Add a backend base URL and timeout to Work Helper configuration.
- [ ] Accept the AI delegation token as request metadata and pass it only as an outbound PMS API header; keep it out of the model prompt, session message and audit arguments.
- [ ] Treat Work Helper's existing confirmation token as an outer HITL mechanism only; PMS `operationId` remains the backend source of truth for execution.
- [ ] Pass structured JSON to the model and return structured preview/result data.
- [ ] Add pytest coverage for backend success, timeout, 401 and stable business errors.
- [ ] Run `pytest tests/test_pms_tools.py -q` and verify all tool paths pass.

### Task 6: Connect frontend page context and confirmation UI

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-front/src/components/ai/page-context.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/layout/Index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/AiProjectAssistantDrawer.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/work-helper.ts`
- Test: `/Users/fs/Desktop/Project/pms-front/src/components/ai/page-context.test.mjs`
- Test: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.test.mjs`

- [ ] Define a `PageContextProvider` that returns the page type, route, IDs, filters and current version metadata.
- [ ] Register providers for project list, dashboard, project detail and workflow template pages.
- [ ] Request a scoped AI delegation token from PMS and send it to Work Helper as transport metadata, never as chat context.
- [ ] Send context snapshots when the drawer opens and refresh them when route, node or filter state changes.
- [ ] Render command previews with affected records, warnings and a primary “确认执行” button.
- [ ] Call PMS execution only after confirmation and refresh the scopes returned by the backend.
- [ ] Keep the drawer usable when context loading or command execution fails.
- [ ] Run `pnpm test`, `pnpm typecheck` and the focused workflow tests.

### Task 7: Add a thin Codex-style `pms-cli`

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-cli/pom.xml`
- Create: `/Users/fs/Desktop/Project/pms-cli/src/main/java/com/brad/pms/cli/PmsCliApplication.java`
- Create: `/Users/fs/Desktop/Project/pms-cli/src/main/java/com/brad/pms/cli/PmsCli.java`
- Create: `/Users/fs/Desktop/Project/pms-cli/src/main/java/com/brad/pms/cli/commands/ContextCommand.java`
- Create: `/Users/fs/Desktop/Project/pms-cli/src/main/java/com/brad/pms/cli/commands/TaskCommand.java`
- Create: `/Users/fs/Desktop/Project/pms-cli/src/main/java/com/brad/pms/cli/commands/OperationCommand.java`
- Test: `/Users/fs/Desktop/Project/pms-cli/src/test/java/com/brad/pms/cli/PmsCliTest.java`

- [ ] Use a top-level parser with subcommands `context`, `task` and `operation`, following Codex's centralized subcommand model.
- [ ] Implement `pms context inspect --project --node --json`.
- [ ] Implement `pms task create ... --dry-run` and `pms task assign ... --dry-run`.
- [ ] Implement `pms operation execute <operation-id>` with an explicit idempotency key.
- [ ] Make the client call backend HTTP APIs; do not duplicate project or task business logic.
- [ ] Support `--base-url` and a short-lived `--token` or configured token source; never put credentials in command arguments written to audit logs.
- [ ] Add `--json` output for Work Helper automation and human-readable output for terminal users.
- [ ] Run `mvn test` in `pms-cli` and verify help output lists every supported command.

### Task 8: End-to-end verification and rollout

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/AiCommandEndToEndTest.java`
- Create: `/Users/fs/Desktop/Project/work-helper/tests/test_pms_command_flow.py`
- Modify: `/Users/fs/Desktop/Project/pms-front/tests/e2e/project-ai-assistant.spec.ts`
- Modify: `/Users/fs/Desktop/Project/pms-backend/README.md`

- [ ] Test `project-detail` context inspection from the current project page.
- [ ] Test natural-language task creation through Work Helper and verify the preview contains the current project and node.
- [ ] Confirm execution creates exactly one task, assigns the requested person and returns refresh scopes.
- [ ] Reload the project detail, task board and dashboard and verify the new task is visible everywhere.
- [ ] Repeat the same idempotency key and verify no duplicate task is created.
- [ ] Change the project/node version before execution and verify the operation is rejected as stale.
- [ ] Run backend tests, Work Helper pytest, frontend tests/typecheck/build and the browser E2E test.
- [ ] Document the supported command names, preview/execute lifecycle and rollback procedure.

## Rollout order

1. Land Tasks 1–4 as a backend-only, read-only context plus durable task preview milestone.
2. Add the delegation-token path and Task 5 so Work Helper can call context/preview without a write credential.
3. Add Task 6 and verify the drawer flow with the current project detail page.
4. Add Task 7 only after the HTTP contract and operation lifecycle are stable.
5. Complete Task 8 before enabling operation execution for ordinary users.

## Self-review checklist

- Context awareness, command discovery, preview, confirmation, execution, permissions, version checks and audit are all covered.
- The plan keeps business logic in `pms-backend`, AI orchestration in `work-helper`, and presentation in `pms-front`.
- The CLI remains a thin Codex-style entry point and does not create a second business layer.
- No task depends on an undefined command name or interface.
