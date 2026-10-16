# External Agent Resource Progression v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Each task ends with its own review and verification gate.

**Goal:** 让 Claude/Codex 通过 pms-cli，以统一的上下文、节点契约、预览、自动执行和回读协议推进项目、需求、专题和故事，并用真实端到端验收证明链路可靠。

**Architecture:** PMS 后端继续作为业务事实、权限、流程和写入规则的唯一来源；pms-ai-connector 提供统一的 Agent Progression 契约和四类资源适配器；外部 Agent 负责自然语言理解、草稿生成和子 Agent 协作，pms-cli 负责把结构化草稿绑定到真实 context/saveContract。低/中风险且能力目录标记为 automatic 的写入在 CLI 内部自动执行，但仍保留服务端 preview、幂等、版本校验、审计和回读；高风险操作继续要求明确确认。

**Tech Stack:** Spring Boot/Java、MySQL/Flyway、JUnit 5、TypeScript、Zod、Vitest、pms-cli、Claude/Codex 外部 Agent。

**Spec:** `docs/superpowers/specs/2026-10-16-resource-scenario-expansion-design.md`、`docs/superpowers/specs/2026-10-16-story-node-orchestrator-design.md`、`docs/roadmaps/2026-10-14-ai-collaboration-roadmap.md`。

## Global Constraints

- PMS 后端是权限、流程、字段白名单、版本和审计的唯一权威；Connector/CLI 不复制业务规则。
- 所有写入都必须先读取当前 context；节点字段只能使用当前节点的 `saveContract` 和版本。
- 写入必须经过 preview 或服务端 automatic operation、幂等键、乐观锁和执行后 readback；不得盲重试未知结果。
- `pms context` 的模板版本、资源版本、上下文版本和节点版本必须分开传递，不能相互替代。
- 不新增 PMS 内置聊天工作台、不把模型供应商配置放进 PMS 后端、不建设无人值守自动推进。
- 本阶段不扩展高风险删除、责任转移、上线承诺等操作的自动执行范围。
- `COMPLETE_NODE` 在本计划中只实现故事验收节点；项目、需求、专题的节点完成继续保持不支持，避免把“统一外壳”误解成“统一放开所有写入”。
- 真实写入只允许一次性隔离数据库，并要求显式设置 `PMS_E2E_WRITE=true`、`PMS_E2E_ISOLATED=true` 和数据库清理门禁。

## Review Focus

- **旧上下文或旧 saveContract 写入：** 必须在 preview 前返回版本冲突；由 Task 2 的 stale-context contract test 覆盖。
- **节点详情已写但节点未推进、或节点推进但详情未写：** 必须由同一事务的 complete-with-data 和真实回读覆盖；由 Task 3 覆盖。
- **Agent 提供受保护字段、未知字段或错误资源类型：** 必须拒绝且不产生 operation；由 Task 2/4 的 schema 与 adapter tests 覆盖。
- **automatic 操作的重复执行和回读失败：** 必须返回原 operation 或 `WRITE_RESULT_UNKNOWN`，不能产生第二次业务效果；由 Task 4/6 覆盖。
- **自然语言将事实、推断和假设混为一谈：** 必须在评估报告中单独评分，未验证结论不能写成 PMS 事实；由 Task 5 覆盖。

### Task 1: 固化当前故事测试成果与验证基线

**Repositories:** `pms-backend`、`pms-ai-connector`

**Files:**
- Modify/commit: `pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReader.java`
- Modify/commit: `pms-backend/src/main/java/com/brad/pms/workflow/TopicDevelopmentTestingPolicy.java`
- Test: `pms-backend/src/test/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReaderTest.java`
- Test: `pms-ai-connector/tests/e2e/resource-scenario-real-pms.test.ts`
- Create: `pms-backend/docs/agent-contracts/pms/resource-progression-baseline.md`

**Interfaces:**
- Consumes: 当前 `story-testing/v1` saveContract、`development-item.node.complete-with-data` 和已通过的故事 34 真实回读结果。
- Produces: 一份记录“代码测试通过/真实链路通过/自然语言尚未评估”的基线；当前未提交变更必须先单独提交，后续任务不得依赖工作区脏状态。

- [ ] 记录 backend/connector 当前 commit、测试命令、版本和真实操作 ID。
- [ ] 运行 backend 定向测试、connector typecheck/contract tests 和已有隔离 E2E；明确列出 skipped 项及原因。
- [ ] 运行 `git diff --check`，确认没有未说明的修改；提交当前 `story-testing` 契约和测试。
- [ ] 将故事 34 的测试节点回读结果写入基线文档，不把共享数据当作后续 E2E 夹具。

**Gate:** 当前改动已提交；目标测试通过；基线文档能区分代码证据、真实写入证据和未验证的模型质量。

### Task 2: 后端统一暴露节点字段写入与完成契约

**Files:**
- Modify: `pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReader.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/ai/orchestrator/contract/StoryNodeContractResolver.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/workflow/TopicDevelopmentTestingPolicy.java`
- Test: `pms-backend/src/test/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReaderTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/StoryNodeContractResolverTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/workflow/TopicDevelopmentTestingPolicyTest.java`
- Test: `pms-backend/src/test/java/com/brad/pms/ai/command/development/CompleteDevelopmentItemNodeWithDataCommandTest.java`

**Interfaces:**
- Consumes: 现有 JSON component policy、字段 merge/validate 行为和 `complete-with-data` 命令。
- Produces: 向兼容现有消费者的 saveContract 增加可选 `completionCommand`、`completionSupported` 和仅用于提示缺失信息的 `completionRequiredMetadata`；`command` 仍保持字段保存命令，避免破坏现有客户端。`ownerId/startDate/endDate` 仍是节点元数据，不进入组件 patch 的 writableFields。

- [ ] 先写测试：故事 acceptance/development/testing 节点的 saveContract 都能声明真实可用的完成命令；无可写组件时不声明完成能力；需求、专题、项目组件不因此获得节点完成能力。
- [ ] 先写测试：完成契约不能把 `title/status/ownerId/startDate/endDate` 放入组件 patch 的 writableFields。
- [ ] 实现后端契约投影，所有 writableFields 必须与实际 policy merge 的字段一致；不要从页面 DTO 推断字段。
- [ ] 为 complete-with-data 增加字段保存与节点完成的原子回滚测试：完成失败时详情不能部分落库。
- [ ] 运行 backend 定向测试和 compile/package。

**Gate:** Agent 可以从 focused context 得到“字段更新”和“带详情完成节点”两种明确能力，且 protected fields、版本和白名单均由服务端声明。

### Task 3: 完成故事验收节点垂直切片

**Files:**
- Modify: `pms-ai-connector/packages/pms-contracts/src/scenario.ts`
- Modify: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/storyWorkbench.ts`
- Modify: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/storyOrchestrator.ts`
- Test: `pms-ai-connector/tests/contract/story-workbench-contract.test.ts`
- Test: `pms-ai-connector/tests/unit/story-workbench-scenario.test.ts`
- Create: `pms-ai-connector/tests/e2e/story-acceptance-real-pms.test.ts`
- Modify: `pms-ai-connector/tests/e2e/support/storyFixture.ts`
- Create/modify: `pms-ai-connector/docs/agent-contracts/pms/story-acceptance-e2e.md`

**Interfaces:**
- Consumes: Task 2 的 focused saveContract、`StoryWorkbenchInput`、`development-item.node.complete-with-data`。
- Produces: 向后兼容的 `story-workbench/v1` 完成输入，新增可选 `complete/ownerId/startDate/endDate`，并继续支持 `resourceId/nodeId/version/variant/patch/idempotencyKey`；执行结果必须包含 `operationId/status/readback/currentNode/verification`。只有协议形状无法兼容时才另开版本。

- [ ] 写失败契约测试：acceptance patch 只能写 `acceptanceConclusion`、`acceptanceNote`；错误 variant、旧 version、受保护字段和未知字段在 preview 前失败。
- [ ] 写单元测试：`complete=true` 时使用服务端 completionCommand，并把 patch、负责人和排期放进同一 operation；`complete=false` 只保存字段。
- [ ] 实现 CLI 适配器：始终重新读取 focused context，使用当前 saveContract，不猜节点 ID 或字段。
- [ ] 写隔离 E2E：创建/选择一次性故事夹具，完成 acceptance 节点，验证字段持久化、当前节点切换、protected fields 未变化、同幂等键回放和旧版本冲突。
- [ ] 若操作成功但回读失败，返回 `WRITE_RESULT_UNKNOWN` 并保留 operationId，不自动再写。

**Gate:** 外部 Agent 可用一条结构化调用完成故事验收节点；PMS 页面和 `pms context` 都能读到相同详情和下一节点。

### Task 4: 统一项目、需求、专题、故事的资源推进入口

**Files:**
- Create: `pms-ai-connector/packages/pms-contracts/src/agentProgression.ts`
- Modify: `pms-ai-connector/packages/pms-contracts/src/resourceScenario.ts`
- Modify: `pms-ai-connector/packages/pms-contracts/src/index.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/agentProgression.ts`
- Modify: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts`
- Modify: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/resourceScenarioExecutor.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/storyProgressionAdapter.ts`
- Modify: `pms-ai-connector/apps/pms-cli/src/cli/runCli.ts`
- Test: `pms-ai-connector/tests/contract/agent-progression-contract.test.ts`
- Test: `pms-ai-connector/tests/unit/agent-progression-dispatch.test.ts`
- Test: `pms-ai-connector/tests/contract/automatic-write-policy.test.ts`

**Interfaces:**
- Consumes: 已有 `resource-scenario/v1` 的 project/requirement/topic adapter、Task 3 的 story adapter、capabilities 的 mode/risk。
- Produces: `agent-progression/v1`，统一输入 `resourceType/resourceId/nodeId/contextVersion/intent/draft/executeMode/idempotencyKey`；统一结果 `status/plan/operation/readback/verification/warnings`。资源差异只在 adapter 中，不在 Agent 层复制 PMS 规则。

- [ ] 定义 `intent` 为 `READ_CONTEXT`、`UPDATE_NODE_FIELDS`、`COMPLETE_NODE`；`COMPLETE_NODE` 只有故事 acceptance adapter 注册，其他资源返回 `CAPABILITY_UNAVAILABLE`；operation 名称必须由 capability/saveContract 解析，不能由模型自由拼接。
- [ ] 将现有 `resourceScenarioPlanSchema.requiresConfirmation: z.literal(true)` 改为由 capability mode/risk 推导的布尔值，并增加 `executionMode`；automatic 能力默认无人工确认但仍内部 preview/审计，非 automatic 能力返回 `WAITING_CONFIRMATION`。
- [ ] 写 dispatch 测试，覆盖四种 resourceType、未知资源、未注册操作和无权限资源。
- [ ] 写安全测试，证明 automatic 只允许低/中风险能力，读取能力不能走写入口，高风险不能被 `auto` 绕过。
- [ ] 让 `runCli` 暴露统一的机器可读入口，并保留现有旧场景命令作为兼容别名。
- [ ] 运行 connector 全量 typecheck、contract tests 和相关 unit tests。

**Gate:** Claude/Codex 只需要学习一个 Agent Progression 输入/输出协议；四类资源都能走相同的“读取—准备—执行—回读”外壳。

### Task 5: 建立自然语言影子评估和人工对照

**Files:**
- Create: `pms-ai-connector/tests/fixtures/progression-shadow-cases.json`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/ProgressionDraftProvider.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/FixedProgressionDraftProvider.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/ExternalProgressionDraftProvider.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/scenarios/progressionShadow.ts`
- Modify: `pms-ai-connector/apps/pms-cli/src/cli/runCli.ts`
- Test: `pms-ai-connector/tests/unit/progression-draft-provider.test.ts`
- Create: `pms-ai-connector/docs/e2e-progression-evaluation-report.md`

**Interfaces:**
- Consumes: Task 4 的 `agent-progression/v1`，并复用现有 OpenAI-compatible 请求实现的传输层；新增 `ProgressionDraftProvider` 只负责四类资源的结构化草稿，固定 Mock 与外部模型 provider 必须实现同一接口，不能把需求分析字段强行套到项目/专题/故事。
- Produces: `pms scenario progression shadow`，只读生成草稿，不写 PMS；输出结构化的 facts/inferences/assumptions/unresolvedDecisions、候选资源、节点、所需字段和引用。

- [ ] 准备至少 12 条用例，覆盖明确目标、信息不足、歧义资源、多个候选节点、权限不足、已有内容和重复请求。
- [ ] 为每条用例记录人工基线：主动投入时间、总耗时、澄清次数、遗漏、返工和质量评分。
- [ ] 用 Claude/Codex 分别运行影子用例，固定 prompt、模型、skill、CLI 和 PMS 版本；禁止影子运行写入。
- [ ] 评分事实准确性、来源覆盖、假设标记、缺失信息识别、字段完整性和人工修订次数；模型评分只能辅助，业务人员最终评分。
- [ ] 报告必须区分“程序通过”“模型输出通过”“人工验收通过”，不把未运行的外部模型实验写成收益。

**Gate:** 形成可复现的自然语言质量基线；只有主动投入下降且质量不下降，才扩大自动推进范围。

### Task 6: 完成 Orchestrator 真实隔离环境验收

**Files:**
- Modify: `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/AiOrchestratorIsolatedAcceptanceTest.java`
- Modify: `pms-ai-connector/tests/e2e/orchestrator-story-node.e2e.test.ts`
- Modify: `pms-ai-connector/tests/e2e/support/isolatedSession.ts`
- Modify: `pms-ai-connector/docs/agent-contracts/orchestrator/ai-orchestrator-v1-acceptance-report.md`
- Modify: `pms-backend/docs/agent-contracts/orchestrator/ai-orchestrator-v1.md`

**Interfaces:**
- Consumes: 已有 requirement/story 的 conversation/turn/plan/preview/confirm/session/events API、Task 3 的故事 fixture 和 Task 5 的输入边界；本任务不把 Orchestrator 扩展到 topic/project。
- Produces: 可审计的真实验收报告，覆盖 `NEEDS_INPUT → PLAN_READY → WAITING_CONFIRMATION → EXECUTING → VERIFYING → SUCCEEDED`、回读未知、同键恢复、异键冲突、第二客户端 session 恢复和事件游标。

- [ ] 先运行现有 isolated acceptance，确认所有环境变量和一次性数据库门禁有效。
- [ ] 补齐真实故事 acceptance 场景，验证多轮 patch 合并不绕过新鲜 context 和 saveContract。
- [ ] 验证 preview、confirm、readback 和事件顺序；确认失败不会产生第二次领域写入。
- [ ] 仅在隔离数据库和显式清理门禁下执行真实写入；未满足门禁时必须 skipped，而不是降级到共享数据库。
- [ ] 更新契约文档，使状态图、事件表和实际实现一致。

**Gate:** Orchestrator 的代码测试与真实环境证据一致，才进入下一阶段的完整会话层或持续任务。

## Implementation Order

按 Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 顺序执行。每个任务完成后执行本任务的测试和一次独立 review；发现契约、版本或权限问题时先修复，不跨任务堆叠未验证代码。

## Explicitly Deferred

- PMS 内置聊天页面、SSE 聊天工作台和新的前端主交互。
- 把模型配置、API Key 或 provider 逻辑放进 PMS 后端。
- 无人值守自动推进、定时 Agent 和跨项目批量写入。
- 一次性为所有历史流程模板生成统一字段；先以实际 saveContract 为准。

## Final Verification

完成全部任务后，必须同时提供：backend targeted/full test 结果、connector typecheck/全量相关 test 结果、隔离环境真实 E2E 操作 ID、自然语言影子评估报告、`git diff --check` 结果，以及未运行项目的明确清单。
