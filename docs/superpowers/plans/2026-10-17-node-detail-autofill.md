# 节点详情自动补全实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让外部 Agent 根据对话和 PMS 上下文生成节点详情草稿，并通过 `pms-cli` 完成字段校验、计划、原子写入和回读验证。

**Architecture:** 外部 Agent 保留自然语言、聊天上下文和模型配置；PMS 提供真实字段契约、权限、版本和工作流规则。第一期统一需求、专题、故事的研发事项节点，复用 `development-item.node.field.update` 和 `development-item.node.complete-with-data`；项目节点放到第二期，先补齐其独立 workbench 的原子完成命令。

**Tech Stack:** Spring Boot/Java、MyBatis、Jackson、TypeScript、Zod、Vitest、Maven。

**Spec:** `docs/superpowers/specs/2026-10-17-node-detail-autofill-design.md`

## Global Constraints

- PMS 是字段和流程规则的唯一事实来源；禁止 CLI 直写数据库或猜测命令名。
- 模型由 Claude/Codex/DeepSeek 等外部 Agent 提供；`pms-cli` 和 PMS 不保存模型 API Key。
- `draft` 只能读 context 和验证草稿；`plan` 只能创建预览；`execute` 必须使用 previewOperationId、幂等键和版本校验。旧客户端可以继续解析草稿，但新的节点详情自动补全写入必须提供字段来源。
- 低/中风险的明确用户意图可自动执行；高风险、推断值和未明确的负责人/排期必须停在 `NEEDS_INPUT` 或 `WAITING_CONFIRMATION`。
- 未提供的字段保留原值；状态、标题、负责人、排期、版本等保护字段不能通过详情 patch 修改。
- 每个写入字段必须有来源和置信度；凭据形态字段必须被拒绝。
- 每个任务完成后先运行该任务的测试和 diff review，再进入下一个任务。

## Review Focus

- **字段契约漂移：** PMS 返回的 writableFields、required、options 和实际命令不一致；测试 context 到 plan 的完整映射。
- **只补详情却误推进状态：** `UPDATE_NODE_FIELDS` 不得调用完成命令；`COMPLETE_NODE` 必须使用原子 complete-with-data。
- **模型臆测业务结论：** inference 来源的测试结果、验收结论、负责人、排期不能自动写入。
- **并发修改：** context/version 或字段版本过期时返回 CONFLICT，并要求重新生成预览。
- **部分成功和敏感数据：** 回读失败返回 WRITE_RESULT_UNKNOWN；草稿和日志不得输出凭据。

## 文件与模块边界

### 后端 PMS

- `src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReader.java`：生成研发事项组件的字段契约和保存契约。
- `src/main/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadService.java`：将当前节点、组件、版本和能力投影到 Agent context。
- `src/main/java/com/brad/pms/ai/command/development/CompleteDevelopmentItemNodeWithDataCommand.java`：作为需求/专题/故事原子保存详情并完成节点的唯一执行入口。
- `src/main/java/com/brad/pms/ai/command/development/UpdateDevelopmentItemNodeFieldCommand.java`：仅更新详情、不推进节点的入口。
- `src/main/java/com/brad/pms/ai/command/PmsCommandMetadata.java`：确认命令参数、风险和资源类型与契约一致。
- `src/test/java/com/brad/pms/ai/connector/context/*`、`src/test/java/com/brad/pms/ai/command/development/*`：后端契约和前置条件测试。

### pms-cli / Connector

- `packages/pms-contracts/src/agentProgression.ts`：扩展草稿的字段来源、完成意图和缺失信息结果。
- `packages/pms-contracts/src/query.ts`：保持 context/saveContract 的向后兼容解析。
- `apps/pms-cli/src/commands/scenarios/AgentDraftContract.ts`：外部 Agent 草稿的安全校验和字段绑定。
- `apps/pms-cli/src/commands/scenarios/storyWorkbench.ts`：保留故事专用兼容路径，并接入统一详情补全流程。
- `apps/pms-cli/src/commands/scenarios/agentProgression.ts`（必要时拆出新文件）：统一需求、专题、故事的 draft/plan/execute。
- `tests/contract/*`、`tests/unit/*`、`tests/e2e/*`：schema、字段映射、版本冲突和真实 PMS 回读测试。

### 文档

- `docs/agent-contracts/pms/external-agent-progression-v1.md`：补充节点详情草稿、缺失字段和自动执行示例。
- `docs/e2e-agent-cli-runbook.md`：补充真实故事/需求场景和预期结果。

---

### Task 1: 固化节点详情契约和字段来源模型

**目标：** 让 Agent 能从 context 中知道“当前节点能填什么、什么必填、什么受保护、完成需要什么”。

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReader.java`
- Modify: `src/main/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadService.java`
- Modify: `packages/pms-contracts/src/query.ts`
- Modify: `packages/pms-contracts/src/agentProgression.ts`
- Test: `src/test/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReaderTest.java`
- Test: `tests/contract/agent-context-cli.test.ts`

**Interfaces:**
- 后端生成 `saveContract.contractVersion=node-detail/v1`、`fieldContracts`、`protectedFields`、`completionSupported`、`completionCommand` 和 `completionRequiredMetadata`。
- `AgentProgressionDraft` 增加可选 `fieldSources` 和 `requestedCompletion`；旧的 `patch`、facts 和 assumptions 保持兼容。

- [x] 先写失败测试：故事验收、需求测试和专题开发节点返回字段契约；required、options、protectedFields、当前 version 正确。
- [x] 运行后端 context 测试和 connector contract 测试，确认测试先失败。
- [x] 实现契约投影；字段定义来自真实模板和 `StoryNodeWorkbenchPolicy`/`WorkflowFieldValueValidator`，不在 CLI 重复维护字段清单。
- [x] 为旧 PMS/旧组件保留宽松解析：缺少新增字段时仍可读取，但不得伪造 completionSupported。
- [x] 运行：
  `mvn -q -Dtest=AiDevelopmentComponentReaderTest,AiWorkflowContextReadServiceTest test`
  和 `pnpm exec vitest run tests/contract/agent-context-cli.test.ts --reporter=dot`。
- [x] Review gate：检查字段契约是否覆盖需求、专题、故事三类资源，且没有泄漏保护字段当前值。
- [x] Commit：`feat(ai): publish node detail completion contracts`。

### Task 2: 增加外部 Agent 草稿的字段级安全校验

**目标：** 在不调用模型的前提下，确保外部 Agent 提交的每个字段都有来源、属于当前契约，并且不会携带凭据或覆盖保护字段。

**Files:**
- Modify: `packages/pms-contracts/src/agentProgression.ts`
- Modify: `apps/pms-cli/src/commands/scenarios/AgentDraftContract.ts`
- Modify: `apps/pms-cli/src/commands/scenarios/agentProgression.ts`
- Test: `tests/contract/agent-scenario-cli.test.ts`
- Test: `tests/unit/agent-progression.test.ts`

**Interfaces:**
- `fieldSources: Record<string, {sourceType: "pms_context"|"conversation"|"inference"; sourceRefs: string[]; confidence: "high"|"medium"|"low"}>`。
- `validateAgentProgressionDraft()` 返回字段级诊断：`unknownFields`、`protectedFields`、`missingSources`、`unsafePayloadKeys`、`unresolvedDecisions`。
- 缺少来源只对拟写入的 patch 字段报错；空 patch 仍可用于只读分析。

- [x] 写失败测试：未知字段、保护字段、无 sourceRefs、credential key、低置信 inference 写入高风险结论。
- [x] 运行测试，确认失败原因对应契约缺失。
- [x] 实现 schema 和校验；沿用现有 `assertSafeAgentPayload()`，禁止把原始聊天记录或密码放入 draft。
- [x] 将校验结果接入 `scenario progression draft`，只输出诊断和 contextVersion，不创建 preview。
- [x] 运行 connector contract/unit 全部相关测试。
- [x] Review gate：确认校验失败不会触发任何 PMS 写命令。
- [x] Commit：`feat(cli): validate field provenance for node detail drafts`。

### Task 3: 生成字段差异、缺失项和风险计划

**目标：** `plan` 直接告诉 Agent 哪些字段会被补全、哪些必填项仍缺失、是否允许自动执行。

**Files:**
- Modify: `apps/pms-cli/src/commands/scenarios/agentProgression.ts`
- Modify: `packages/pms-contracts/src/agentProgression.ts`
- Modify: `packages/pms-contracts/src/operation.ts`（仅在 preview envelope 需要新增结构化字段时）
- Test: `tests/unit/agent-progression-plan.test.ts`
- Test: `tests/contract/agent-scenario-cli.test.ts`

**Interfaces:**
- `plan` 结果增加 `fieldChanges[]`、`missingFields[]`、`preconditions[]` 和 `writeDecision`：`READY`、`NEEDS_INPUT`、`WAITING_CONFIRMATION`、`CAPABILITY_UNAVAILABLE`。
- `fieldChanges[]` 至少包含 `key`、`label`、`from`、`to`、`sourceType`、`confidence`。
- `COMPLETE_NODE` 缺少必填字段时返回 `NEEDS_INPUT`，不创建可执行写入 preview；`UPDATE_NODE_FIELDS` 只计划字段更新，不推进状态。

- [x] 写失败测试：完整草稿产生正确 diff；缺 owner/schedule/required field 只返回缺失项；UPDATE_NODE_FIELDS 不调用 complete-with-data。
- [x] 写失败测试：inference 低置信字段进入 WAITING_CONFIRMATION；版本不一致返回 CONFLICT。
- [x] 实现 plan 归一化：从 context 的 `saveContract` 读取 command、version、fieldContracts 和 completion metadata；禁止本地字段猜测。
- [x] 把 PMS preview 的 preconditions 原样保留，并将缺失项转换成可供外部 Agent 询问的结构。
- [x] 运行 plan/unit/contract 测试。
- [x] Review gate：检查 plan 输出是否足以让 Agent 一次性向用户提问，而不是逐字段反复确认。
- [x] Commit：`feat(cli): plan node detail autofill changes`。

### Task 4: 接入原子保存详情并完成节点

**目标：** 当用户明确要求完成节点且详情完整时，一次 PMS operation 同时保存详情、负责人、排期并推进节点。

**Files:**
- Modify: `apps/pms-cli/src/commands/scenarios/agentProgression.ts`
- Modify: `apps/pms-cli/src/commands/scenarios/storyWorkbench.ts`
- Modify: `src/main/java/com/brad/pms/ai/command/development/CompleteDevelopmentItemNodeWithDataCommand.java`（仅在测试发现字段契约未覆盖时修复）
- Test: `tests/contract/agent-scenario-cli.test.ts`
- Test: `tests/e2e/requirement-node-autofill.e2e.test.ts`
- Test: `tests/e2e/story-node-autofill.e2e.test.ts`

**Interfaces:**
- `execute` 只接受同一 `previewOperationId`、`idempotencyKey`、`contextVersion` 和经过 draft 校验的 patch。
- 需求/专题/故事统一使用 `development-item.node.complete-with-data`；仅补详情使用 `development-item.node.field.update`。
- 成功结果必须包含 `operation`、`readback.resource`、`readback.context`、`verification.patchApplied` 和 `verification.protectedFieldsUnchanged`。

- [ ] 先用固定外部 Agent fixture 写 E2E：需求测试节点的 patch 包含测试结果、构建版本、报告链接和遗留问题。
- [ ] 运行 E2E，确认旧实现无法满足新的字段来源和回读断言。
- [ ] 实现统一执行分派；保留故事专用路径作为兼容适配，但让它复用同一份字段级 draft/plan 规则。
- [ ] 对 `SUCCEEDED`、`CONFLICT`、`WRITE_RESULT_UNKNOWN`、幂等重放分别断言。
- [ ] 运行后端命令测试、connector E2E 和 CLI build。
- [ ] Review gate：确认节点状态只在 `requestedCompletion=true` 且所有前置条件通过时推进。
- [ ] Commit：`feat(cli): execute node detail autofill atomically`。

### Task 5: 补齐真实 PMS 场景和运行手册

**目标：** 在真实 PMS 上走通一条不修改数据的预览和一条明确授权的低/中风险写入，并把结果固定成可复现测试。

**Files:**
- Create: `tests/e2e/node-detail-autofill-real-pms.test.ts`
- Modify: `docs/agent-contracts/pms/external-agent-progression-v1.md`
- Modify: `docs/e2e-agent-cli-runbook.md`

- [ ] 启动真实 PMS 后端和前端，确认 `pms doctor`、`pms auth status`、`pms capabilities` 正常。
- [ ] 用真实需求或故事读取 `pms context ... --view focused --format agent`。
- [ ] 使用固定、可审计的外部 Agent 草稿执行 `draft` 和 `plan`，验证没有写入。
- [ ] 在独立测试节点上执行一次明确的低/中风险自动写入，验证字段回读、节点状态和操作记录。
- [ ] 记录浏览器授权、MySQL 客户端/容器查询方式、预期输出和清理策略。
- [ ] Review gate：确认真实场景不会依赖 CLI 内模型配置，也不会把凭据写入命令行历史或日志。
- [ ] Commit：`docs(cli): document node detail autofill e2e`。

### Task 6: 项目节点第二期（单独评审）

项目节点不和第一期混做。先为 project-node workbench 增加原子 `node.complete-with-data` 能力，再复用 Task 1–5 的 Agent contract；如果项目节点只需详情更新，则继续使用现有 `node.field.update`。

**验收条件：** 项目节点拥有真实的 `saveContract`、字段版本检查、前置条件和回读验证后，才打开项目资源的自动补全能力。

## 验收标准

- 对需求、专题、故事，Agent 可以仅凭 `pms context` 返回的契约生成合法详情草稿，不需要猜字段名。
- 用户给出完整信息时，一次 `plan → execute → readback` 能同时保存详情并推进节点。
- 缺失必填信息时，CLI 返回结构化缺失字段，零写入。
- 详情更新和节点推进的意图分离：补详情不会意外推进节点。
- 旧 CLI/旧 PMS context 仍能解析；新增字段缺失时不会伪造写入能力。
- 版本冲突、保护字段、低置信推断、凭据字段和回读失败都有明确结果。

## 推荐开发顺序

先做 Task 1 和 Task 2，确认“PMS 说能填什么、Agent 只能提交什么”；再做 Task 3，确认计划可读；最后做 Task 4 和 Task 5 的真实闭环。Task 6 单独评审，不阻塞需求/专题/故事的第一期上线。
