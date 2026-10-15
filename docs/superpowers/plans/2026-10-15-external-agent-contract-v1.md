# External Agent Contract v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Claude、Codex 等外部 Agent 通过 `pms-cli` 使用统一协议读取 PMS 上下文、委派只读子 Agent、生成节点必填字段草稿，并在用户确认后安全推进项目、需求、专题和故事。

**Architecture:** 保留 PMS 前端作为数据浏览和人工维护入口，不新增 PMS 内置 AI 工作台。PMS 后端继续作为事实、权限、流程和写入的唯一权威；Connector 负责共享契约和场景适配；`pms-cli` 负责向外部 Agent 提供机器可读的 context → draft → preview → execute → verify 链路。已有的 `PmsAgentContract`、工作流上下文 `saveContract`、`requirement-analysis/v1` 和 `story-workbench/v1` 作为现有资产扩展，不再创建第二套平行业务协议。

**Tech Stack:** PMS Spring Boot 3.5 / Java 17 / Jackson / MyBatis；`pms-ai-connector` TypeScript / Zod / Vitest / pnpm；Claude、Codex 等外部 Agent；不修改前端业务入口。

**Spec:** `docs/agent-contracts/pms/external-agent-contract-v1.md`

## Execution order

The task numbers identify stable work packages; the implementation order is intentionally:

```text
Task 1 → Task 5 → Task 2 → Task 3 → Task 4 → Task 6
```

Task 2 maps the Connector output from the PMS backend, so Task 5 must publish and test the real capability, node, `saveContract`, permission-scope and context-version fields first. This avoids building the mapper against a guessed response shape and then reworking it after the backend contract is corrected.

## Global Constraints

- PMS 后端是业务事实、权限、工作流、版本和最终写入的唯一权威。
- 外部 Agent 负责自然语言理解、任务拆解、子 Agent 协作和结果汇总。
- `pms-cli` 主路径只提供 `read`、`propose`、`apply` 三类能力；模型配置不是必需依赖。
- 所有写入继续遵循 preview → explicit confirmation → execute → readback verification。
- 子 Agent 当前只接收快照和任务，不接收 PMS 登录凭据；主 Agent 是唯一调用 `pms-cli` 读取和写入 PMS 的 Agent。
- 现有 `requirement-analysis/v1` 和 `story-workbench/v1` 必须保持向后兼容。
- 读取、草稿、预览不能修改 PMS；冲突和未知结果不得自动重试或覆盖数据。
- 不新增 PMS 前端 AI 工作台，不拆分微服务，不引入新的模型供应商依赖。

## Review Focus

- 现有 `PmsAgentContract` YAML 与字段级 `saveContract` 是否被合并成一份可消费的节点契约，而不是出现两份规则漂移。
- `requirement-analysis/v1` 与 `story-workbench/v1` 映射到统一外层结构时，是否保留原始场景字段和验证语义。
- 子 Agent 是否能读到完整的 `ContextSnapshot`，但没有 PMS 登录凭据和写入能力。
- 上下文版本变化、幂等重放、预览冲突和执行结果未知时，是否都能阻止重复写入。
- 项目、需求、专题和故事中任一资源缺少可写节点或字段时，是否返回可操作的缺失信息，而不是猜测或静默降级。

### Task 1: 建立 Connector 共享 Agent Contract 类型

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/agent.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/index.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/agent-contract.test.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/agent-work-session.test.ts`

**Interfaces:**
- Produces `agentContractVersion = "agent-contract/v1"`.
- Reuses the existing `resourceRefSchema` from `operation.ts` and produces `agentResourceRefSchema` (required positive resource ID), `contextSnapshotSchema`, `nodeContractSchema`, `agentTaskSchema`, `agentDraftSchema`, `previewEnvelopeSchema`, `operationEnvelopeSchema`, `verificationEnvelopeSchema` and `agentWorkSessionSchema`.
- `agentDraftSchema` must contain `facts`, `inferences`, `assumptions`, `unresolvedDecisions`, `proposedFieldValues`, `missingFields`, `questions`, `evidenceRefs` and `confidence`.
- `nodeContractSchema` must contain `nodeId`, `key`, `version`, `requiredFields`, `writableFields`, `protectedFields`, `allowedActions`, `allowedTransitions` and `missingInputRules`.
- `agentTaskSchema` must contain `taskId`, optional `parentTaskId`, `role`, `objective`, `resource`, `contextSnapshotId` and `permissionMode` (`read-only`, `draft`, `write`).

- [x] **Step 1: Write failing contract tests**

  Assert that a valid project, requirement, topic and story work session parses; a write-capable node with an empty writable field list, missing snapshot version, invalid permission mode, and an unverified write session are rejected. A read-only node may have no writable fields.

- [x] **Step 2: Run the focused tests and verify they fail**

  Run: `pnpm vitest run tests/contract/agent-contract.test.ts tests/contract/agent-work-session.test.ts`

  Expected: FAIL because the shared schemas do not exist.

- [x] **Step 3: Implement the Zod schemas and exports**

  Reuse the existing `AnalysisItem`, `WorkflowContext`, `OperationPreview`, `OperationResult` and scenario versions where possible. Keep existing scenario schemas unchanged; the new envelope must wrap them through an optional `scenarioDraft` field.

- [x] **Step 4: Run the focused tests and typecheck**

  Run: `pnpm vitest run tests/contract/agent-contract.test.ts tests/contract/agent-work-session.test.ts && pnpm typecheck`

  Expected: PASS.

- [x] **Step 5: Commit**

  Commit message: `feat: add external agent contract v1`

### Task 2: 将现有 PMS 上下文映射为 Node Contract

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/core/agentContext.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/core/context.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/cli/runCli.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/unit/agent-context.test.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/agent-context-cli.test.ts`

**Interfaces:**
- Produces `buildAgentContext(context: WorkflowContext): AgentWorkSession` for read/draft mode.
- `buildAgentContext` may return `draft: null` during a pure read; it must not fabricate an empty AI draft.
- Produces `buildNodeContract(context: WorkflowContext, nodeId?: number, component?: string): NodeContract`.
- Adds a machine-readable command: `pms context <resourceType> <id> --format agent [--node-id <id>] [--component <key>]`.
- With `--format agent`, the existing CLI success envelope remains unchanged and `data` contains the validated `AgentWorkSession`; `--format json` keeps returning the existing raw `WorkflowContext`.
- The mapper must derive field-level writable scope from the selected component `saveContract`; it must not infer writable fields from labels or page DTOs.

- [x] **Step 1: Add failing mapping tests**

  Cover a focused story node with a valid `saveContract`, a project node without a component save contract, an unsupported component, and a resource with missing workflow configuration. Assert that source references, protected fields, current version and warnings are preserved.

- [x] **Step 2: Run the focused tests and verify they fail**

  Run: `pnpm vitest run tests/unit/agent-context.test.ts tests/contract/agent-context-cli.test.ts`

  Expected: FAIL because the agent mapper and format are not implemented.

- [x] **Step 3: Implement the mapper and CLI output**

  Keep the existing default context output backward compatible. `--format agent` is additive and returns only the stable envelope; raw UI-oriented details remain under `contextSnapshot.raw` only when explicitly requested by tests/internal callers.

- [x] **Step 4: Run tests and build the CLI**

  Run: `pnpm vitest run tests/unit/agent-context.test.ts tests/contract/agent-context-cli.test.ts && pnpm typecheck && pnpm run build:cli`

  Expected: PASS.

- [x] **Step 5: Commit**

  Commit message: `feat: expose agent context and node contracts`

### Task 3: 统一现有场景到 AgentWorkSession

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/scenarios/agentWorkSession.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/scenarios/requirementAnalysis.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/scenarios/storyWorkbench.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/cli/runCli.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/unit/agent-work-session-adapter.test.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/agent-work-session-scenarios.test.ts`

**Interfaces:**
- Produces `toRequirementAnalysisWorkSession(input: { sessionId: string; taskId: string; context: WorkflowContext; draft: AnalysisDraft | null; plan?: RequirementAnalysisPlan; result?: RequirementAnalysisResult }): AgentWorkSession`.
- Produces `toStoryWorkbenchWorkSession(input: { sessionId: string; taskId: string; context: WorkflowContext; input: StoryWorkbenchInput; plan?: StoryWorkbenchScenarioPlan; result?: StoryWorkbenchResult }): AgentWorkSession`.
- Adds a common result discriminator while retaining existing `status`, `operationId`, `preview`, `verification` and `WRITE_RESULT_UNKNOWN` values.
- Does not change the existing scenario command arguments or API request payloads.

- [x] **Step 1: Write failing adapter tests**

  Assert that requirement analysis and story workbench preserve their scenario contract version, draft/patch, preview, operation and verification data inside the common envelope.

- [x] **Step 2: Run focused tests and verify they fail**

  Run: `pnpm vitest run tests/unit/agent-work-session-adapter.test.ts tests/contract/agent-work-session-scenarios.test.ts`

  Expected: FAIL because adapters do not exist.

- [x] **Step 3: Implement adapters without changing business execution code**

  Keep preview, idempotency, version conflict, readback and protected-field checks in the existing scenarios. The adapter only normalizes their results for external Agents.

- [x] **Step 4: Run the existing and new scenario tests**

  Run: `pnpm vitest run tests/contract/requirement-analysis-contract.test.ts tests/contract/story-workbench-contract.test.ts tests/unit/story-workbench-scenario.test.ts tests/unit/requirement-analysis.test.ts tests/unit/agent-work-session-adapter.test.ts tests/contract/agent-work-session-scenarios.test.ts`

  Expected: PASS.

- [x] **Step 5: Commit**

  Commit message: `feat: normalize scenarios into agent work sessions`

### Task 4: 固化主 Agent/子 Agent 的凭据边界

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/agent.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/scenarios/AgentDraftContract.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/unit/agent-task-boundary.test.ts`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/unit/agent-draft-contract.test.ts`

**Interfaces:**
- `AgentTask.permissionMode` remains metadata for parent/child coordination, but is not treated as an authorization mechanism.
- Child tasks receive a `ContextSnapshot` and task payload only; no PMS token, auth-store path or executable write callback is included.
- The parent Agent remains the only caller of `pms-cli`; existing PMS scopes, preview and confirmation remain the actual write boundary.
- Produces `buildChildAgentTask(input: { parentTaskId: string; taskId: string; role: string; objective: string; snapshot: ContextSnapshot }): AgentTask` and `validateChildDraft(draft: AgentDraft, snapshot: ContextSnapshot): void`.

- [x] **Step 1: Write failing permission tests**

  Assert that a child task payload contains no credentials or write callback, that draft evidence cannot cite unauthorized resources, and that the parent write path preserves the current conflict and unknown-result semantics.

- [x] **Step 2: Run focused tests and verify they fail**

  Run: `pnpm vitest run tests/unit/agent-task-boundary.test.ts tests/unit/agent-draft-contract.test.ts`

  Expected: FAIL because the child-task payload and credential boundary are not enforced.

- [x] **Step 3: Implement the parent/child payload boundary**

  Build child-task payloads from the snapshot only; reject credential-shaped keys and executable callbacks. Validate draft evidence and proposed changes against the parent snapshot. Do not add a second authorization system; keep PMS scopes and the existing preview/execute chain as the only write enforcement.

- [x] **Step 4: Run focused safety tests**

  Run: `pnpm vitest run tests/unit/agent-task-boundary.test.ts tests/unit/e2e-safety.test.ts tests/unit/agent-draft-contract.test.ts`

  Expected: PASS.

- [x] **Step 5: Commit**

  Commit message: `feat: enforce read-only child agent boundaries`

### Task 5: 对照真实能力目录补齐 PMS 后端契约输出

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/AiConnectorCapabilityService.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/AiConnectorController.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/ai/api/AiCapabilityDTO.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/ai/api/AiWorkflowContextDTO.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadService.java`
- Test: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/connector/AiConnectorCapabilityServiceTest.java`
- Test: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/controller/AiConnectorControllerTest.java`

**Interfaces:**
- Capabilities must publish the supported external-agent contract version and the actual availability of `readonlyWorkflowContext` and `focusedWorkflowContext`.
- The feature flags may be enabled only after the focused context tests pass; otherwise the catalog must report them as unavailable and the CLI must fail with a structured capability error instead of assuming support.
- Context responses must preserve `saveContract`, selected node/component metadata, warnings, permissions scope and context version.
- If an operation is not present in `PmsCommandRegistry` or unavailable for the delegated scope, it must not appear as executable.

- [x] **Step 1: Add failing backend contract tests**

  Assert that project, requirement, topic and story workflow capabilities expose real node fields and actions; delegated read-only scope hides write commands; focused context includes the selected node component contract.

- [x] **Step 2: Run the targeted Maven tests and verify they fail**

  Run: `./mvnw -q -Dtest=AiConnectorCapabilityServiceTest,AiConnectorControllerTest test`

  Expected: FAIL for missing external-agent metadata or stale feature flags.

- [x] **Step 3: Implement capability/context publication**

  Reuse `PmsAgentContractRegistry` for agent-level rules and the workflow definition/`saveContract` data for field-level rules. Do not add DSH-specific endpoints or model-provider configuration.

- [x] **Step 4: Run targeted backend tests and compile**

  Run: `./mvnw -q -Dtest=AiConnectorCapabilityServiceTest,AiConnectorControllerTest test && ./mvnw -q -DskipTests compile`

  Expected: PASS.

- [x] **Step 5: Commit**

  Commit message: `feat: publish external agent workflow contracts`

### Task 6: 更新 Claude/Codex 的 PMS Skill 使用协议

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/skills/pms-project-management/SKILL.md`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/skill-analysis-contract.test.ts`

**Interfaces:**
- The installed Skill must teach external Agents to call `pms capabilities --format json` before `pms context --format agent`.
- The Skill must separate read, draft, preview and execute; execute requires an explicit user confirmation in the current conversation.
- The Skill must state that child Agents receive a snapshot and task only, while the parent Agent owns the PMS CLI session and write path.
- The Skill must retain the existing rules for source evidence, dynamic workflow fields, idempotency, conflicts and no guessed URLs/commands.

- [x] **Step 1: Add failing Skill contract assertions**

  Assert that the Skill mentions `--format agent`, `preview`, explicit confirmation, parent/child snapshot handoff, no child credentials, and `WRITE_RESULT_UNKNOWN` handling.

- [x] **Step 2: Run the Skill contract test and verify it fails**

  Run: `pnpm vitest run tests/contract/skill-analysis-contract.test.ts`

  Expected: FAIL because the installed Skill still describes automatic write execution and does not define the external Agent handoff.

- [x] **Step 3: Update the Skill text**

  Keep the Skill concise and operational. Do not add model-provider setup, PMS frontend instructions or a second command naming scheme.

- [x] **Step 4: Run the Skill test and build the CLI**

  Run: `pnpm vitest run tests/contract/skill-analysis-contract.test.ts && pnpm typecheck && pnpm run build:cli`

  Expected: PASS.

- [x] **Step 5: Commit**

  Commit message: `docs: define external agent pms workflow`

### Task 7: 建立外部 Agent 真实验收集

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/fixtures/external-agent-cases.json`
- Create: `/Users/fs/Desktop/Project/pms-backend/docs/agent-contracts/pms/external-agent-e2e-runbook.md`
- Create: `/Users/fs/Desktop/Project/pms-backend/docs/agent-contracts/pms/external-agent-e2e-record.md`
- Test: `/Users/fs/Desktop/Project/pms-ai-connector/tests/e2e/external-agent-contract.e2e.test.ts`

**Interfaces:**
- Fixtures cover at least one read-only analysis, one missing-input clarification, one draft-only story update, one confirmed write, one version conflict and one unknown readback result.
- Every case records resource type, node, contract version, context version, operation ID, permission mode and verification result.

- [x] **Step 1: Write the six scenario fixtures and expected safety outcomes**

  Expected outcomes must state whether PMS data may change. Read-only, clarification and draft cases must explicitly assert no write operation.

- [x] **Step 2: Run the fixture tests against mocked clients**

  Run: `pnpm vitest run tests/e2e/external-agent-contract.e2e.test.ts`

  Expected: FAIL until the scenario adapter and permission boundary are wired.

- [x] **Step 3: Implement the test harness**

  Use the existing isolated session helpers and the real `pms-cli` JSON output. Do not inject model credentials into PMS; model natural-language generation remains an external Agent concern.

- [ ] **Step 4: Run the real isolated acceptance**

  Run: `pnpm vitest run tests/e2e/external-agent-contract.e2e.test.ts` with the existing local PMS services available. Record targeted test status separately from full-suite environment failures.

  Deferred until the user supplies or enables a disposable PMS target and isolated account; this step must not write to the shared local environment.

- [x] **Step 5: Commit**

  Commit message: `test: add external agent contract acceptance cases`

## Review gates

After each task, stop and review the diff, focused tests, contract compatibility and security boundary before starting the next task. Do not begin the next task when a review finds schema drift, an unverified write path, or a feature flag that claims a capability the backend does not actually serve.

## Deferred work

- Do not add a PMS frontend chat page.
- Do not add a built-in model provider to the CLI main path.
- Do not implement asynchronous multi-hour Agent delegation until the six acceptance cases pass.
- Do not expand to every command or every workflow component before measuring the first external-Agent scenarios.
