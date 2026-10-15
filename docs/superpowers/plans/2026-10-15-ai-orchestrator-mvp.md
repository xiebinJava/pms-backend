# AI Orchestrator MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不复制 PMS 业务规则的前提下，补齐“结构化分析草稿 → 读取真实上下文 → 生成计划 → 预览 → 授权执行 → 回读验证”的第一个可验收闭环，首个场景限定为“需求分析并保存到当前可写节点”。

**Architecture:** PMS Backend 继续作为业务事实、权限、工作流和写入规则的唯一来源；`pms-ai-connector` 增加轻量场景编排层，负责把外部 AI 工作台已经生成的结构化分析草稿串成读取、预览、执行和回读流程。第一期不在 Connector 内实现模型调用，不建设通用 Agent 平台，也不要求立即新增 PMS 内置聊天页面；先用现有 CLI/外部 AI 工作台验证闭环，再决定是否把编排入口移入前端。

**Tech Stack:** Spring Boot 3.x, MyBatis-Plus, Flyway, MySQL, JUnit/Testcontainers, TypeScript, pnpm, Zod, 现有 PMS AI Connector HTTP client。

**Spec:** `docs/roadmaps/2026-10-14-ai-collaboration-roadmap.md`

## Global Constraints

- PMS Backend 是业务事实、权限、流程、版本和写入规则的唯一来源；Connector 不直接访问数据库。
- 第一阶段只实现“需求分析并保存到当前可写节点”，不扩展到项目计划自动推进、无人值守 Agent 或多 Agent 调度。
- 所有写入必须继续经过 capability 校验、context/version 校验、preview、幂等键和执行后回读。
- 保留现有 `/integration/ai/v1`、CLI 命令和响应兼容性；新增字段只能向后兼容。
- 不在 PMS Backend 或 Connector 中引入具体模型供应商 SDK；模型在外部 AI 工作台生成 `AnalysisDraft`，Connector 只负责校验和执行。
- `readonlyWorkflowContext` 和 `focusedWorkflowContext` 未通过真实端到端验收前不改为默认开启。
- 每个任务都必须有单测或集成测试，并记录实际运行、跳过和环境阻塞的区别。

## Review Focus

- **上下文对象不完整：** 项目或需求存在绑定节点时，返回的对象、节点、模板版本必须可区分；由 Task 2 的上下文契约测试覆盖。
- **权限边界：** 无权引用不会出现在事实、引用或推断中；由 Task 2 的批量授权测试覆盖。
- **陈旧版本：** context 生成后对象版本变化时，写入必须返回 conflict 且不产生业务变更；由 Task 3 的版本冲突测试覆盖。
- **重复执行：** 相同用户、相同幂等键重复请求只能产生一个业务效果；由 Task 3 的幂等集成测试覆盖。
- **分析可信度：** facts、inferences、assumptions 和 unresolvedDecisions 必须保持分类，不能把外部模型猜测写成 PMS 事实；由 Task 1 和 Task 3 的契约测试覆盖。

### Task 1: 固化场景编排契约

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/scenario.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/index.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/requirement-analysis-contract.test.ts`

**Interfaces:**
- Consumes: 现有 `WorkflowContext`、`OperationPreview`、`OperationResult` 和 `ResourceDetail` 契约；草稿由外部 AI 工作台传入。
- Produces:
  - `AnalysisItem { text: string; sourceRefs: string[]; confidence?: "high" | "medium" | "low" }`
  - `AnalysisDraft { facts: AnalysisItem[]; inferences: AnalysisItem[]; assumptions: AnalysisItem[]; unresolvedDecisions: AnalysisItem[]; patch: Record<string, unknown> }`
  - `RequirementAnalysisInput { resourceType: "requirement"; resourceId: number; nodeId: number; contextVersion: number; draft: AnalysisDraft; save: boolean; idempotencyKey?: string }`
  - `RequirementAnalysisPlan { scenarioContractVersion: "requirement-analysis/v1"; resourceId: number; contextId: string; contextVersion: string; previewRequired: true; proposedWrite: Record<string, unknown> }`
  - `RequirementAnalysisResult { status: "DRAFT" | "PREVIEWED" | "SUCCEEDED" | "REJECTED" | "CONFLICT"; operationId?: string; draft: AnalysisDraft; verification?: { resourceReadback: boolean; contextReadback: boolean; protectedFieldsUnchanged: boolean } }`

- [ ] **Step 1: Write the failing Zod contract tests**

  覆盖合法分析结果、数字 ID、缺少 `resourceId`、未分类的外部模型输出、未知状态值和向后兼容的可选字段。

- [ ] **Step 2: Run the contract tests and verify they fail**

  Run: `pnpm exec vitest run tests/contract/requirement-analysis-contract.test.ts`

  Expected: FAIL because `scenario.ts` and its exports do not exist.

- [ ] **Step 3: Implement the scenario schemas and exports**

  使用 Zod 定义输入、计划、分析草稿、验证结果和最终结果；禁止用 `any` 绕过枚举和必填字段校验；固定 `scenarioContractVersion` 为 `requirement-analysis/v1`；`sourceRefs` 只允许引用当前 context 中已授权的资源。

- [ ] **Step 4: Run the contract tests and package typecheck**

  Run: `pnpm exec vitest run tests/contract/requirement-analysis-contract.test.ts && pnpm typecheck`

  Expected: PASS.

- [ ] **Step 5: Commit the contract change**

  Commit: `feat: add requirement analysis orchestration contract`

### Task 2: 将 AI 上下文改成稳定的快照读取边界

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiContextDataPort.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiContextReference.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/ReadAuthorizationSnapshot.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadService.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiProjectContextReader.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentContextReader.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiContextReadPolicy.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiContextReferenceProjector.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/connector/context/AiContextSnapshotContractTest.java`

**Interfaces:**
- Consumes: 当前用户身份、`resourceType`、`resourceId`、现有 `ContextReadOptions`、项目/需求/专题/故事的只读查询。
- Produces:
  - `AiContextDataPort.read(String resourceType, Long resourceId, ContextReadOptions options, Long userId): AiWorkflowContextDTO`
  - `AiContextReference(String resourceType, Long resourceId)`
  - `ReadAuthorizationSnapshot.forUser(Long userId, Collection<AiContextReference> references)`
  - `ReadAuthorizationSnapshot.canRead(AiContextReference reference): boolean`

- [ ] **Step 1: Add failing tests for snapshot completeness and redaction**

  验证：真实绑定节点被返回；模板定义与执行状态分开；无权引用被省略；未配置字段与空字段保持可区分；同一次读取返回同一个 context version。

- [ ] **Step 2: Run the focused backend test and verify it fails**

  Run: `mvn -q -Dtest=AiContextSnapshotContractTest test`

  Expected: FAIL until the snapshot port and authorization snapshot are implemented.

- [ ] **Step 3: Implement the read port and authorization snapshot**

  在一次只读事务中收集并去重引用；授权快照在本次请求内复用同一引用的判定结果；让 Reader 只负责组装快照，让 Projector 只负责脱敏和输出；不得在数组循环中重复调用同一引用的授权查询。当前 mapper 没有批量查询能力时先保证请求内去重，批量 SQL 单独作为性能任务，不为本期强行扩大数据访问层改造。

- [ ] **Step 4: Route existing context endpoints through the new port**

  保留现有 response envelope、resource type 和 legacy/focused 参数；`AiWorkflowContextReadService` 只负责事务、参数和 port 编排，不再依赖 UI 详情 DTO 作为 AI 合同。

- [ ] **Step 5: Run focused tests and existing AI context tests**

  Run: `mvn -q -Dtest=AiContextSnapshotContractTest,AiWorkflowContextReadOnlyIntegrationTest test`

  Expected: PASS; 未连接 Docker 时必须明确记录为环境阻塞，不把 skipped 当作通过。

- [ ] **Step 6: Commit the context boundary change**

  Commit: `refactor: add snapshot boundary for ai workflow context`

### Task 3: 收敛现有需求分析场景，避免再造一套流程

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/scenarios/AnalysisDraftValidator.ts`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/apps/pms-cli/src/commands/scenarios/requirementAnalysis.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/unit/requirement-analysis-orchestrator.test.ts`

**Interfaces:**
- Consumes: Task 1 的 `RequirementAnalysisInput`、现有 `planRequirementAnalysisScenario`、`executeRequirementAnalysisScenario`、`PmsClient.get/context/preview/execute`。
- Produces:
  - `AnalysisDraftValidator.validate(draft: AnalysisDraft, context: WorkflowContext): void`
  - `runRequirementAnalysisScenario(client: PmsClient, input: RequirementAnalysisInput, approve: () => Promise<boolean>): Promise<RequirementAnalysisResult>`
  - 固定步骤：`readContext → validateDraft → preview → approve → execute → readback`。

- [ ] **Step 1: Write failing orchestration tests**

  使用 fake `PmsClient` 和固定 `AnalysisDraft` 验证：只读模式不执行；保存模式必须先 preview；拒绝确认不执行；execute 返回 `CONFLICT` 时不重试；成功后必须调用 `get` 和 `context` 回读；重复请求沿用同一幂等键。

- [ ] **Step 2: Run the scenario tests and verify the missing facade/validator**

  Run: `pnpm exec vitest run tests/unit/requirement-analysis-orchestrator.test.ts`

  Expected: FAIL because the validator and single scenario facade do not yet exist.

- [ ] **Step 3: Implement draft validation**

  验证四类分析数组、`patch` 字段、引用资源是否存在于当前已授权 context；禁止外部草稿覆盖受保护字段；验证失败时不调用 preview。

- [ ] **Step 4: Refactor the existing scenario functions behind one facade**

  不新增第二套保存逻辑；复用已有节点、版本、operation 和回读逻辑；保持现有 CLI JSON 输出和命令名兼容；不提前增加未使用的 operation polling API。

- [ ] **Step 5: Add explicit save/approval semantics**

  保留现有 `scenario requirement-analysis plan|execute` 参数；将“分析”和“保存”明确分开；默认只生成草稿，只有显式执行命令且 `approve()` 返回 `true` 才执行写入。

- [ ] **Step 6: Run package tests and CLI checks**

  Run: `pnpm test && pnpm typecheck && pnpm build:cli`

  Expected: PASS.

- [ ] **Step 7: Commit the scenario consolidation**

  Commit: `refactor: consolidate requirement analysis orchestration`

### Task 4: 真实环境验收与是否扩展的决策门

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-backend/docs/agent-contracts/pms/requirement-analysis-orchestration-e2e.md`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/docs/e2e-requirement-analysis-report.md`
- Modify: `/Users/fs/Desktop/Project/pms-backend/docs/roadmaps/2026-10-14-ai-collaboration-roadmap.md`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/fixtures/requirement-analysis-cases.json`

**Interfaces:**
- Consumes: Task 2 的真实 context、Task 3 的场景编排和现有 operation result。
- Produces: 可复现的自然语言场景验收记录，以及继续扩展、修复或暂停的明确结论。

- [ ] **Step 1: Prepare 10–20 representative cases**

  必须包含：背景完整、信息不足、术语歧义、已有分析、多个候选节点、权限不足、过期版本、重复请求和只读分析。

- [ ] **Step 2: Execute isolated MySQL end-to-end cases**

  记录实际用户、项目/需求 ID、context version、operation ID、页面回读和重复请求结果；历史报告不能直接作为当前版本证据。

- [ ] **Step 3: Run quality and safety review**

  对 facts、inferences、assumptions、unresolved decisions 分别评分；越权写入、重复业务写入、未授权字段覆盖必须为零。

- [ ] **Step 4: Compare against manual baseline**

  记录主动投入时间、总完成时间、澄清次数、修订次数、成果质量和返工情况；目标是主动投入下降且质量不下降，不把目标当成既成事实。

- [ ] **Step 5: Record the decision**

  只有在场景可重复、回读正确、失败可追踪且有明确效率收益时，才进入下一阶段的项目计划/行动项场景；否则根据失败分布修复 context、交互或分析流程。

- [ ] **Step 6: Commit the evidence and roadmap update**

  Commit: `docs: record requirement analysis orchestration acceptance`

## Deferred Work

- 暂不拆微服务。
- 暂不把 LLM SDK 放进 PMS Backend。
- 暂不在 Connector 中实现模型调用；外部 AI 工作台负责自然语言理解和 `AnalysisDraft` 生成。
- 暂不重写整个 PMS 前端，也不要求所有点击操作改成聊天。
- 暂不建设通用 Agent marketplace、多 Agent 调度、无人值守自动推进和模型供应商管理平台。
- 长流程所需的 `RUNNING/FAILED/CANCELLED` 持久化状态、operation event/outbox 和轮询接口，在真实场景证明需要异步执行后单独立项。
- 前端聊天工作台放在本计划验收通过后，复用已验证的场景契约、operation 状态和回读结果。

## Self-Review

- Spec coverage: M0 的基线、M1 的真实上下文、M2 的需求分析保存回读和 M3 的效率验收分别由 Task 2–4 覆盖；没有把 M4/M5 的跨对象自动推进混入本期。
- Step scan: 每个任务都有独立接口、失败测试、实现、验证和提交边界；Connector、Backend、验收证据分开，避免一次改动跨越不可审查的范围。
- Type consistency: Task 1 的 `RequirementAnalysisInput`、`AnalysisDraft`、`RequirementAnalysisResult` 被 Task 3 直接消费；Task 3 复用现有 operation result，不假设尚未实现的事件 API。
- Review focus: 五类高风险输入分别绑定到 Task 1、Task 2 和 Task 3 的测试，Task 4 负责真实环境复核。
- Proportion: 第一阶段只交付一个业务场景、上下文稳定性和必要的契约验证；异步 operation 生命周期和前端聊天明确延后。
