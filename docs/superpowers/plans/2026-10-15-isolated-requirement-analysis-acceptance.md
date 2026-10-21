# 隔离环境需求分析保存验收实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在独立的本地 PMS 数据库中完成“创建测试需求 → 读取上下文 → 预览 → 用户确认 → 执行保存 → 幂等重放 → 回读验证”的真实闭环，为后续聊天入口提供可信的业务执行基线。

**Architecture:** 继续让 PMS Backend 负责权限、流程、版本和写入规则；Connector 只负责场景编排。真实写入只连接一次性或可整体销毁的数据库，测试通过 `runId` 创建可追踪数据，结束后销毁数据库或恢复快照，不触碰当前共享开发数据。自然语言暂时采用固定分析草稿，先证明执行安全和回读正确，再评估聊天体验。

**Tech Stack:** Spring Boot 3.x, MySQL 8, Docker Compose, TypeScript, pnpm, Vitest, 现有 PMS CLI BrowserAuth 和 AI Connector HTTP API。

**Spec:** `docs/roadmaps/2026-10-14-ai-collaboration-roadmap.md` 与 `docs/superpowers/plans/2026-10-15-ai-orchestrator-mvp.md`

## Global Constraints

- 任何写入都只能发往独立数据库或一次性测试实例；不能使用现有需求 24、27 或其他共享业务数据。
- `PMS_E2E_WRITE=true`、`PMS_E2E_ISOLATED=true`、`PMS_E2E_BASE_URL` 与 `PMS_E2E_ALLOWED_BASE_URL` 完全匹配时才允许运行真实写入。
- BrowserAuth 返回的用户 ID 必须与 `PMS_E2E_ACCOUNT_ID` 一致，否则立即停止。
- 每次执行使用唯一 `runId` 和幂等键；执行成功后必须保存 operationId、context version、回读结果和清理结果。
- 不为 `requirement.analysis.save` 添加未注册的 Agent Contract；当前场景默认不携带 contract。
- 在真实保存闭环通过前，不开发项目计划自动推进、无人值守 Agent 或完整聊天工作台。

## Review Focus

- **写入目标隔离：** 目标 URL、数据库名称和运行账号不属于共享开发环境；由 Task 1 的启动前检查和失败测试覆盖。
- **测试数据生命周期：** 中途失败仍能定位并销毁本次 run 创建的数据；由 Task 2 的 fixture 记录和 Task 4 的清理验证覆盖。
- **版本冲突：** 使用旧 context version 的新幂等键必须返回 HTTP 409 客户端冲突且不产生第二次分析写入；由 Task 3 覆盖。
- **幂等重放：** 相同幂等键只能复用原 operation，不重复追加分析文本；由 Task 3 覆盖。
- **流程边界：** 保存分析成果不能自动完成当前节点，受保护字段必须保持不变；由 Task 3 的回读断言覆盖。

### Task 1: 建立一次性真实验收环境

**Files:**
- Create: `pms-backend/scripts/start-ai-e2e.sh`
- Create: `pms-backend/scripts/stop-ai-e2e.sh`
- Create: `pms-backend/docs/agent-contracts/pms/isolated-e2e-runbook.md`
- Modify: `pms-cli/tests/e2e/support/isolatedSession.ts`
- Test: `pms-cli/tests/unit/e2e-safety.test.ts`

**Interfaces:**
- Produces an API base URL and auth origin for a disposable PMS instance, plus a documented `PMS_E2E_*` environment block.
- `isolatedE2EEnabled(env)` remains the single gate; the new scripts must not bypass it.

- [x] **Step 1: Add failing safety assertions for the disposable target**

  Cover a mismatched database/API target, a shared development URL, an invalid `runId`, and a valid isolated target. Expected behavior: mismatches return disabled before BrowserAuth or any write request.

- [x] **Step 2: Implement the disposable MySQL/API scripts**

  Start a separate MySQL database and API port using the existing migration path; accept credentials only through process environment; print only the base URL, auth origin, database name and readiness status. Stop the stack and remove the disposable volume explicitly.

- [x] **Step 3: Document the exact environment and reset procedure**

  The runbook must show how to start, verify readiness, set `PMS_E2E_BASE_URL`, `PMS_E2E_ALLOWED_BASE_URL`, `PMS_E2E_AUTH_ORIGIN`, `PMS_E2E_ACCOUNT_ID`, `PMS_E2E_RUN_ID`, `PMS_E2E_WRITE`, and `PMS_E2E_ISOLATED`, then destroy the stack.

- [x] **Step 4: Run the safety tests**

  Run: `pnpm exec vitest run tests/unit/e2e-safety.test.ts`

  Expected: PASS; no browser authorization or HTTP write is attempted by the safety tests.

### Task 2: 创建可追踪的需求测试夹具

**Files:**
- Create: `pms-cli/tests/e2e/support/requirementFixture.ts`
- Modify: `pms-cli/tests/e2e/support/isolatedSession.ts`
- Modify: `pms-cli/tests/e2e/requirement-analysis.e2e.test.ts`
- Test: `pms-cli/tests/unit/requirement-fixture.test.ts`

**Interfaces:**
- `createRequirementFixture(client, runId): Promise<{ requirementId: number; nodeId: number; version: number; title: string }>`
- `fixtureTitle(runId): string` must be deterministic and include the run ID.
- The fixture is created through the registered `requirement.create` operation, then reads context to discover the actual current node and version; it must not assume a hard-coded node ID.

- [x] **Step 1: Write the failing fixture contract tests**

  Assert that the helper rejects a missing created requirement ID, rejects a context without `currentNode` or version, and returns the created ID/node/version when both reads are valid.

- [x] **Step 2: Implement fixture creation through preview and execute**

  Use the existing client operation APIs with `requirement.create`; use a run-specific title and description; assert the result is `SUCCEEDED`; record the operation ID and created ID in memory for the run report.

- [x] **Step 3: Make the real test select fixture or explicit target**

  Keep `PMS_E2E_REQUIREMENT_ID`/`PMS_E2E_NODE_ID` support for a pre-seeded isolated environment. Add `PMS_E2E_CREATE_FIXTURE=true` as an opt-in path that creates the requirement and uses the discovered node/version. Never fall back to IDs from the shared database.

- [x] **Step 4: Run unit and type checks**

  Run: `pnpm exec vitest run tests/unit/requirement-fixture.test.ts tests/unit/e2e-safety.test.ts && pnpm typecheck`

  Expected: PASS.

### Task 3: 完成真实保存、冲突和幂等验收

**Files:**
- Modify: `pms-cli/tests/e2e/requirement-analysis.e2e.test.ts`
- Modify: `pms-cli/tests/e2e/support/requirementFixture.ts`
- Modify: `pms-cli/docs/e2e-requirement-analysis-report.md`
- Create: `pms-cli/tests/e2e/requirement-analysis-conflict.e2e.test.ts`

**Interfaces:**
- Consumes the existing `planRequirementAnalysisScenario`, `executeRequirementAnalysisScenario`, and `runRequirementAnalysisScenario` APIs.
- Produces a report containing `runId`, viewer ID, requirement ID, node ID, before/after context versions, operation IDs, protected-field result, replay result, cleanup result and any skipped reason.

- [x] **Step 1: Add deterministic real-case assertions**

  Use a fixed `AnalysisDraft` with `facts`, `inferences`, `assumptions`, `unresolvedDecisions` and a patch containing a unique run marker. Assert preview succeeds before execute, the first execute succeeds, the current node remains unchanged, protected fields remain unchanged, and readback contains the marker.

- [x] **Step 2: Verify idempotent replay**

  Execute the same request with the same idempotency key; assert the operation ID and saved result match the first execution and the marker is not appended twice.

- [x] **Step 3: Verify stale-version rejection**

  Use a new idempotency key with the old context version after the first save; assert the client receives HTTP 409 conflict, no second business change, and a fresh context read shows the original successful version.

- [x] **Step 4: Run the isolated real E2E**

  Run: `PMS_E2E_WRITE=true PMS_E2E_ISOLATED=true PMS_E2E_CREATE_FIXTURE=true pnpm run verify:real-pms:analysis-save`

  Expected: the save/idempotency test and the stale-version conflict test both pass in the disposable target; if the safety gate is incomplete, the tests must skip before authentication and the report must say why.

### Task 4: 评估自然语言入口，再决定是否做聊天 UI

**Files:**
- Create: `pms-backend/docs/agent-contracts/pms/requirement-analysis-acceptance-report.md`
- Modify: `pms-backend/docs/roadmaps/2026-10-14-ai-collaboration-roadmap.md`
- Create: `pms-cli/tests/fixtures/requirement-analysis-shadow-cases.json`

**Interfaces:**
- Consumes the verified deterministic save flow from Task 3.
- Produces a decision: continue to a preview-first chat surface, repair the context contract, or stop expansion until the failure distribution is understood.

- [ ] **Step 1: Run 10–20 shadow cases without writing**

  Feed real user-style prompts into the external AI layer, but only record the generated `AnalysisDraft`; score facts, inferences, assumptions, unresolved decisions, citation coverage and clarification needs.

- [ ] **Step 2: Compare against manual baseline**

  For each case record active user time, total completion time, clarification count, revision count and reviewer quality score. Do not claim an efficiency gain from code-level tests alone.

- [x] **Step 3: Set the product decision gate**

  Proceed to a chat UI only if the deterministic write flow passes, no unauthorized/protected-field violations occur, and shadow cases show lower active effort without lower review quality. Otherwise repair the failing context or draft contract first.

- [x] **Step 4: Record evidence and run the full verification**

  Run: `pnpm test && pnpm typecheck && pnpm build:cli`, then attach the isolated E2E report and the shadow-case decision to the roadmap.

## Deferred Work

- 不在本阶段加入模型 SDK、通用 Agent 平台、异步 operation event 或多 Agent 调度。
- 不把现有共享需求 24、27 改造成测试数据。
- 不在真实保存和自然语言影子评估通过前开发完整聊天页面；下一阶段只需要一个“草稿 → 预览 → 确认”的最小入口。

## Self-Review

- **Spec coverage:** 既有 MVP 计划中的真实验收缺口由 Task 1–3 补齐；自然语言质量和是否进入聊天 UI 由 Task 4 单独决策，没有混入写入安全验证。
- **Step scan:** 每个任务按失败测试、最小实现、独立验证拆分；环境、夹具、真实写入和产品决策分开，便于在任一阶段停止。
- **Type consistency:** Task 2 的 fixture 返回 `requirementId/nodeId/version`，Task 3 直接消费；Task 3 的报告字段由 Task 4 使用，不依赖未实现的异步 operation API。
- **Review focus:** 写入隔离、生命周期、冲突、幂等、流程边界均有对应测试或验收步骤。
- **Proportion:** 先补真实可信度，再做聊天交互；没有把项目自动推进或前端重构提前带入本阶段。
