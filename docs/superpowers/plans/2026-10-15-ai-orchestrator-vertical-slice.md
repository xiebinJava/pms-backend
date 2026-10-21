# AI Orchestrator 垂直切片实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 PMS + AI Connector 之上交付一个只覆盖需求分析保存的 AI 编排器，使外部 Agent 的结构化草稿可以经过上下文校验、计划、预览、确认、执行、回读验证和有序事件记录形成完整闭环。

**Architecture:** 编排器作为 PMS Backend 内的 `com.brad.pms.ai.orchestrator` 模块存在，继续复用现有权限、工作流、版本、幂等和命令执行能力；它通过 `AiConnectorPort` 调用 Connector 边界，不直接访问业务 Mapper。外部 Claude/Codex/DeepSeek 或固定 Mock 只提交 `requirement-analysis/v1` 的 Agent Draft，第一期使用 HTTP 轮询事件，不建设模型运行时、SSE 或微服务。

**Tech Stack:** Spring Boot 3.x, MyBatis-Plus, MySQL/Flyway, Java 17+, Spring MockMvc/JUnit, TypeScript, pnpm, Vitest, 现有 PMS AI Connector HTTP Client。

**Spec:** `docs/agent-contracts/orchestrator/ai-orchestrator-v1.md`

## Global Constraints

- `pms_ai_operation` 是唯一的业务操作记录；不要为编排器再建一张执行表。
- 外部 Agent 不得直接写 PMS 数据库；后端不保存模型 API Key，也不在执行路径内调用模型。
- 所有读取、预览和执行使用认证上下文中的 `UserContext.userId()`；请求体中的用户 ID 必须被忽略或拒绝。
- `requirement.analysis.save` 是唯一允许的意图；项目自动推进、跨对象批量写入和无人值守执行不进入本计划。
- 旧版本执行返回 `CONFLICT/409`，不得自动刷新上下文或盲目重试。
- 计划的上下文快照、Agent Draft、预览和验证结果必须可审计；未知引用、越权资源和受保护字段必须在执行前拒绝。
- 每个计划的事件以 `(plan_id, sequence_no)` 唯一约束保证顺序；事件读接口使用 `afterSequence`，第一期不依赖 SSE。
- 本轮只改动后端、Connector Client 和契约测试；前端聊天工作台另建计划，避免把交互层和执行闭环同时变更。

## Review Focus

- **状态跳跃：** 不能从 `DRAFT_VALIDATED` 直接执行，必须先有 `PREVIEWED` 和 `WAITING_CONFIRMATION`；由 `AiOrchestratorStateTest` 覆盖。
- **引用边界：** Draft 引用不在本次上下文快照中的资源时，在保存 Plan 前返回可定位的验证错误；由 `AiAgentDraftValidatorTest` 覆盖。
- **用户隔离：** 用户 A 不能读取或确认用户 B 的 conversation/plan；由 Controller 集成测试覆盖。
- **版本冲突：** 预览后的上下文版本变化必须进入 `CONFLICT`，不能产生第二次业务写入；由编排服务测试和隔离 E2E 覆盖。
- **结果未知：** PMS 执行成功但回读失败必须保存 `WRITE_RESULT_UNKNOWN`，不能显示 `SUCCEEDED`；由验证器/服务测试覆盖。

---

### Task 1: 固化协议、状态机和跨仓库契约样例

**Files:**
- Modify: `pms-backend/docs/agent-contracts/orchestrator/ai-orchestrator-v1.md`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/domain/AiOrchestratorState.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/domain/AiOrchestratorEventType.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/domain/AiOrchestratorIntent.java`
- Create: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/AiOrchestratorStateTest.java`
- Create: `pms-backend/src/test/resources/ai/orchestrator/requirement-analysis-v1-valid.json`
- Create: `pms-backend/src/test/resources/ai/orchestrator/requirement-analysis-v1-invalid.json`

**Interfaces:**
- Produces enums `AiOrchestratorState`, `AiOrchestratorEventType`, `AiOrchestratorIntent.REQUIREMENT_ANALYSIS_SAVE` for Tasks 2–5.
- Produces `transition(from, event)` or an equivalent total transition function that rejects every unspecified transition with a domain exception.
- The JSON fixture is the canonical outer request for both Java and Connector contract tests; it contains `contractVersion: requirement-analysis/v1` and the existing Connector `AnalysisDraft` under `draft`.

- [x] **Step 1: Write the failing state-transition tests**

  Assert the only successful path is `RECEIVED → CONTEXT_READY → DRAFT_VALIDATED → PLAN_READY → PREVIEWED → WAITING_CONFIRMATION → EXECUTING → VERIFYING → SUCCEEDED`; assert stale execution maps to `CONFLICT`, cancellation maps to `CANCELLED`, and direct execution from `PLAN_READY` is rejected.

- [x] **Step 2: Implement the enums and transition function**

  Keep the transition table explicit and side-effect free. Error messages must include both current state and attempted event so the API can expose a stable error code with a useful message.

- [x] **Step 3: Add valid and invalid contract fixtures**

  The valid fixture must contain `facts`, `inferences`, `assumptions`, `unresolvedDecisions`, `patch`, and readable `sourceRefs`; the invalid fixture must include one missing required array, one unknown source reference and one protected patch key for negative tests.

- [x] **Step 4: Run the protocol tests**

  Run: `mvn -q -Dtest=AiOrchestratorStateTest test`

  Expected: PASS; all illegal transitions fail before any persistence or Connector call.

- [x] **Step 5: Commit the protocol contract**

  Suggested message: `feat: define ai orchestrator protocol and state machine`

### Task 2: 建立会话、计划和事件持久化

**Files:**
- Create: `pms-backend/src/main/resources/db/migration/V70__ai_orchestrator.sql`
- Modify: `pms-backend/src/main/java/com/brad/pms/controller/HealthController.java:28`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiConversationDO.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiConversationTurnDO.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiPlanDO.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiOperationEventDO.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiConversationMapper.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiConversationTurnMapper.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiPlanMapper.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/persistence/AiOperationEventMapper.java`
- Create: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/AiOrchestratorPersistenceTest.java`

**Interfaces:**
- `AiConversationMapper` exposes `insert`, `selectByIdAndUserId`, and `updateStatus`.
- `AiConversationTurnMapper` exposes `insert` and `selectByIdAndConversationId`.
- `AiPlanMapper` exposes `insert`, `selectByIdAndConversationUser`, `selectForUpdate`, and `updateState`.
- `AiOperationEventMapper` exposes `appendNext(planId, ...)` and `selectAfterSequence(planId, afterSequence, limit)`; `appendNext` must allocate the next sequence under row lock/transaction.
- `pms_ai_plan` stores `context_snapshot_json`, `draft_json`, `preview_operation_id`, `idempotency_key`, `verification_json`, `error_code` and `error_message`; the snapshot is immutable after `PLAN_READY`.

- [x] **Step 1: Write the persistence tests**

  Assert conversation/plan rows are isolated by user, a plan can store the exact context snapshot and draft JSON, event sequences are `1,2,3` under one transaction, and duplicate `(plan_id, sequence_no)` is rejected.

- [x] **Step 2: Add Flyway migration V70**

  Create the four tables and indexes from the spec. Use `BIGINT` IDs matching existing PMS tables, `JSON`/`LONGTEXT` according to the current schema conventions, UTC-compatible timestamps, and foreign keys only where existing migration policy permits them.

- [x] **Step 3: Implement DOs and MyBatis mappers**

  Follow existing `AiOperationDO`/mapper conventions. `selectForUpdate` must be used before state or idempotency mutation; every read query must include authenticated `user_id` through the service call.

- [x] **Step 4: Run the migration and persistence tests**

  Run: `mvn -q -Dtest=AiOrchestratorPersistenceTest test`

  Expected: PASS against the project test database; migration is repeatable in a fresh schema and no existing AI operation rows are changed.

- [x] **Step 5: Commit the persistence slice**

  Suggested message: `feat: persist ai orchestrator conversations plans and events`

### Task 3: 接入 Connector 边界并实现 Draft 校验

**Files:**
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/connector/AiConnectorPort.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/connector/DefaultAiConnectorPort.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/contract/AgentDraft.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/contract/AgentDraftItem.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/contract/AiAgentDraftValidator.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/contract/DraftValidationResult.java`
- Create: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/AiAgentDraftValidatorTest.java`
- Create: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/DefaultAiConnectorPortTest.java`

**Interfaces:**
- `AiConnectorPort.readContext(String resourceType, Long resourceId, ContextReadOptions options): AiWorkflowContextDTO`.
- `AiConnectorPort.preview(CommandPreviewRequest request): AiOperationPreviewDTO`.
- `AiConnectorPort.execute(OperationExecuteRequest request): AiOperationResultDTO`.
- `AiConnectorPort.readResource(String resourceType, Long resourceId): AiResourceDetailDTO`.
- `AiAgentDraftValidator.validate(AgentDraft draft, AiWorkflowContextDTO context): DraftValidationResult`.
- `DraftValidationResult` returns stable error codes `MISSING_FIELD`, `UNKNOWN_SOURCE_REF`, `PROTECTED_FIELD`, `EMPTY_PATCH`, `CONTRACT_VERSION_UNSUPPORTED` and the offending JSON path.

- [x] **Step 1: Write failing validator and port tests**

  Assert a valid fixture passes; missing arrays, unknown source refs, protected fields, empty patch and unsupported contract versions are rejected before `preview` is invoked. Assert the adapter delegates only to existing context/resource/command services and never to a business Mapper.

- [x] **Step 2: Implement the Java Draft DTOs and validator**

  Preserve unknown-field rejection, normalize blank text as invalid, and compare every `sourceRef` with the readable references in the supplied `AiWorkflowContextDTO`. Treat an absent value, an unreadable value and an empty value as distinct validation outcomes where the context metadata provides that distinction.

- [x] **Step 3: Implement the Connector adapter**

  Wrap `AiWorkflowContextReadService`, `CommandPreviewService`, `CommandExecutionService` and existing resource read services. Map transport errors to domain errors without changing PMS operation status or version semantics.

- [x] **Step 4: Run contract tests**

  Run: `mvn -q -Dtest=AiAgentDraftValidatorTest,DefaultAiConnectorPortTest test`

  Expected: PASS; all invalid drafts stop before any preview/execute call.

- [x] **Step 5: Commit the Connector boundary**

  Suggested message: `feat: add orchestrator connector port and draft validation`

### Task 4: 实现 Orchestrator 应用服务和 HTTP API

**Files:**
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/api/AiOrchestratorController.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/api/AiOrchestratorRequest.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/api/AiOrchestratorResponse.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/service/AiOrchestratorService.java`
- Create: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/service/AiOrchestratorVerificationService.java`
- Create: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/AiOrchestratorServiceTest.java`
- Create: `pms-backend/src/test/java/com/brad/pms/controller/AiOrchestratorControllerTest.java`

**Interfaces:**
- `createConversation(Long userId, CreateConversationCommand): ConversationView`.
- `appendTurn(Long userId, Long conversationId, AppendTurnCommand): PlanView`.
- `preview(Long userId, Long planId): PlanPreviewView`.
- `confirm(Long userId, Long planId, String idempotencyKey): PlanExecutionView`.
- `getConversation(Long userId, Long conversationId): ConversationView`.
- `listEvents(Long userId, Long conversationId, long afterSequence): List<OperationEventView>`.
- Controller routes are `/integration/ai/v1/orchestrator/conversations`, `/turns`, `/plans/{planId}/preview`, `/plans/{planId}/confirm`, and `/events` exactly as specified.

- [x] **Step 1: Write service tests for the happy path and failure paths**

  Mock `AiConnectorPort` and assert: append turn reads and stores a context snapshot; invalid Draft never creates a preview; preview creates an existing `pms_ai_operation` in preview state and transitions to `WAITING_CONFIRMATION`; confirm records execute/verify events; readback failure returns `WRITE_RESULT_UNKNOWN`; old version returns `CONFLICT`.

- [x] **Step 2: Implement conversation and turn creation**

  Resolve the authenticated user from `UserContext`, create the turn, read the requested resource context, validate the resource/node/version, persist the immutable snapshot, validate the Draft, and create a `PLAN_READY` plan. Emit `CONTEXT_READ` and `DRAFT_VALIDATED` in the same transaction as the state change.

- [x] **Step 3: Implement preview-first execution**

  `preview` may only run from `PLAN_READY`; it calls the existing command preview service with the snapshot version and patch, stores `preview_operation_id`, emits `PREVIEW_CREATED` and `WAITING_CONFIRMATION`, and does not change business data.

- [x] **Step 4: Implement confirmation, verification and idempotent replay**

  `confirm` must lock the plan, require `WAITING_CONFIRMATION`, persist the idempotency key, execute through the existing command service, read resource/context again, compare the requested patch and protected fields, and finish as `SUCCEEDED`, `FAILED`, or `CONFLICT`. If the same key already completed, return the stored operation and verification without executing again.

- [x] **Step 5: Add the controller and user-scoped event reads**

  Return stable error envelopes for `400` validation, `403` scope, `404` ownership/resource, `409` conflict/idempotency and `500` result-unknown. `GET events` must never expose another user's conversation or raw unreadable context.

- [x] **Step 6: Run backend tests**

  Run: `mvn -q -Dtest=AiOrchestratorServiceTest,AiOrchestratorControllerTest,AiConnectorControllerTest test`

  Expected: PASS; existing Connector controller and operation tests remain green.

- [x] **Step 7: Commit the orchestration API**

  Suggested message: `feat: add preview-first ai orchestrator lifecycle`

  No commit created under the working-tree ruling recorded in the SDD ledger.

### Task 5: 让 Connector Client 和外部 Agent 可调用编排器

**Files:**
- Modify: `pms-cli/packages/pms-client/src/PmsClient.ts`
- Create: `pms-cli/packages/pms-client/src/OrchestratorClientTypes.ts`
- Create: `pms-cli/packages/pms-client/src/orchestrator.ts`
- Modify: `pms-cli/packages/pms-client/src/index.ts`
- Modify: `pms-cli/packages/pms-contracts/src/index.ts`
- Create: `pms-cli/tests/contract/orchestrator-client.test.ts`
- Create: `pms-cli/tests/unit/orchestrator-client.test.ts`
- Modify: `pms-cli/apps/pms-cli/src/commands/scenarios/requirementAnalysis.ts`
- Modify: `pms-cli/apps/pms-cli/src/cli/parseArguments.ts`

**Interfaces:**
- Extend `PmsClient` with `createConversation`, `appendTurn`, `previewPlan`, `confirmPlan`, `getConversation`, `listConversationEvents`.
- `appendTurn` accepts `{ conversationId, message, contractVersion, draft, nodeId?, contextVersion?, requestId? }` and returns `{ turnId, planId, status, contextSnapshot, validationErrors }`.
- `confirmPlan` accepts `{ planId, idempotencyKey }` and returns `{ planId, operationId, status, verification }`.
- CLI exposes a thin `scenario requirement-analysis orchestrate` path that prints JSON contract data and does not call a model; the existing `draft/plan/execute` commands remain backward compatible.

- [x] **Step 1: Write failing TypeScript contract tests**

  Assert request paths, body field names, `409` mapping to the existing `PmsClientError` category, event ordering, and no retry for confirm/execute. Use `tests/fixtures/requirement-analysis-v1-valid.json` from the backend contract copied into the connector fixture set.

- [x] **Step 2: Add Zod schemas and client methods**

  Implement the six methods on `PmsHttpClient`, reuse current auth/request envelope/error handling, and mark only read endpoints retry-safe. Preserve `clientId` and request ID headers on every call.

- [x] **Step 3: Add the thin CLI command**

  Parse conversation/resource/plan IDs, draft JSON and idempotency key; print each response as machine-readable JSON. Do not put `PMS_MODEL_*` or `PMS_SHADOW_MODEL_*` configuration into this path.

- [x] **Step 4: Run Connector tests and build**

  Run: `pnpm exec vitest run tests/contract/orchestrator-client.test.ts tests/unit/orchestrator-client.test.ts --no-file-parallelism --cache=false && pnpm typecheck && pnpm build:cli`

  Expected: PASS; existing requirement-analysis, authentication and operation client tests remain green.

- [x] **Step 5: Commit the client contract**

  Suggested message: `feat: expose ai orchestrator through pms client`

  No commit created under the working-tree ruling in the SDD ledger. The focused suite, typecheck, and escalated CLI build passed; the full suite still has sandbox-only auth listener/install write failures.

### Task 6: 做隔离环境端到端验收并形成下一阶段门槛

**Files:**
- Create: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/AiOrchestratorIsolatedAcceptanceTest.java`
- Create: `pms-backend/docs/agent-contracts/orchestrator/ai-orchestrator-v1-acceptance-report.md`
- Modify: `pms-backend/docs/roadmaps/2026-10-14-ai-collaboration-roadmap.md`
- Modify: `pms-cli/tests/e2e/requirement-analysis.e2e.test.ts`
- Create: `pms-cli/tests/e2e/orchestrator-requirement-analysis.e2e.test.ts`

**Interfaces:**
- Consumes the HTTP endpoints and `AgentDraft` fixture from Tasks 1–5.
- Produces a report containing user ID, conversation/turn/plan/operation IDs, context version before/after, ordered events, verification result, idempotent replay result, conflict result, cleanup result and skipped reason.

- [x] **Step 1: Add the isolated happy-path E2E**

  Create or select a disposable requirement, create a conversation, append the valid Draft, preview, confirm once, poll events, and assert readback includes the unique run marker while node/status/owner/date fields remain unchanged.

- [x] **Step 2: Add idempotency and conflict cases**

  Repeat confirmation with the same key and assert the same operation ID; then use the old context version with a new key and assert `409/CONFLICT` and no second business change.

- [x] **Step 3: Verify safety and clean-up**

  Require `PMS_E2E_WRITE=true`, `PMS_E2E_ISOLATED=true`, matching allowed base URL and a run ID before any write. Destroy the disposable database even when a test fails; never use shared requirements.

- [ ] **Step 4: Run the complete verification set**

  Run backend targeted tests, Connector targeted tests, `pnpm typecheck`, `pnpm build:cli`, then the isolated E2E command from the existing runbook. Record `PASS`, `SKIPPED` and `FAILED` separately; skipped is not passed.

- [x] **Step 5: Update the roadmap with the decision gate**

  Mark the orchestrator slice complete only when the isolated write, replay, conflict, event ordering and readback checks pass. If natural-language shadow cases have not yet been scored, explicitly keep the chat UI deferred.

- [x] **Step 6: Commit documentation and verification evidence**

  Suggested message: `docs: record ai orchestrator vertical slice acceptance`

  No commit created under the working-tree ruling. The report records the real E2E gate as SKIPPED until a disposable PMS environment is available.

## Deferred Work

- 前端 `AiChatWorkspace`、项目侧边栏、SSE/实时进度和结果修订卡片：在本计划通过后另开前端计划。
- 10—20 条真实自然语言影子用例、人工时间/质量基线和模型波动评估：与本代码闭环并行但不互相冒充验收证据。
- 批量权限快照、领域读模型、旧 `AiConnectorQueryService` 清理、命令 descriptor 重构：作为 P1/P2 维护计划，不阻塞第一条闭环。
- 跨对象项目推进、会议纪要行动项、无人值守 Agent、外部事件触发：等需求分析保存场景验证出真实收益后再做。

## Self-Review

- **Spec coverage:** 会话/轮次、Draft 契约、上下文快照、计划状态、预览确认、既有 operation、事件、回读验证、认证和冲突语义分别由 Tasks 1–6 覆盖；前端和模型运行时按非目标延后。
- **Step scan:** 每个任务先有失败测试，再有最小实现和独立命令；接口名称、路径、状态和错误码已在前置任务定义，后续任务直接消费。
- **Type consistency:** Java 端统一使用 `AiWorkflowContextDTO`、`AiOperationPreviewDTO`、`AiOperationResultDTO`；Connector 端通过 `PmsClient` 扩展六个同名方法；数据库中的 conversation/plan/event/operation ID 使用现有 `Long` 主键约定，HTTP/JSON 层沿用 Connector 现有的字符串化 operation ID 约定。
- **Review focus:** 非法状态、非法引用、用户越权、版本冲突和执行后回读未知均有对应测试位置；共享环境误写由 Task 6 的安全门禁覆盖。
- **Proportion:** 第一阶段只实现一个可验证业务场景和一个编排边界；不提前建设通用 Agent 平台、聊天 UI 或微服务。

## Execution Handoff

本计划已按 Native 方式执行到 Task 6 的验收门槛；Task 1→5 的代码和测试已落地，Task 6 的真实隔离写入仍需在一次性 PMS 环境中运行。Connector 和 Backend 接口紧密耦合，保持同一实施上下文同步契约。
