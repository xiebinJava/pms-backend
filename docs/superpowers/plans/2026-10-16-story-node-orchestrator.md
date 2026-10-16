# Story Node Orchestrator v1 实施计划

> 依据规格：`docs/superpowers/specs/2026-10-16-story-node-orchestrator-design.md`

## 执行规则

- 每个任务先写失败测试，再实现；任务完成后必须运行该任务的验收命令。
- 每个任务都是一个可独立验证的行为切片；任务之间的依赖关系写在 `Blocked by` 中。
- 每个任务完成后先做一次 review，Critical/Important 问题关闭后才进入下一个任务。
- PMS Backend 是业务真相源；Connector 只负责协议、CLI 编排和客户端侧格式校验。
- 所有故事写入都必须经过现有 `saveContract`、preview、confirm、幂等和 readback 链路。
- 不修改前端，不实现 PMS 内部聊天工作台，不实现子 Agent 委托和子 Agent 结果合并。
- Connector schema 先发布，Backend 后发布；需求分析 response 不输出新增 story 字段。
- 本切片不需要数据库 migration：现有 `AiPlanDO` 的 JSON/VARCHAR 列可以保存 story draft/context/verification。若实现发现长度、索引或并发约束不足，必须在对应任务中补 migration、回滚说明和测试，不能静默改表。

## T0：统一故事节点字段契约

**Blocked by**：无（共享 seam 的必要前置重构）

**交付行为**：Backend 能从同一份 `StoryNodeWorkbenchPolicy` 得到 required、nullable、effective writable、protected 和校验语义；每个对外宣称可写的字段都能真正持久化。

**修改范围**：

- `pms-backend/src/main/java/com/brad/pms/workflow/StoryNodeWorkbenchPolicy.java`
- `pms-backend/src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReader.java`
- `pms-backend/src/test/java/com/brad/pms/workflow/StoryNodeWorkbenchPolicyTest.java`
- `pms-backend/src/test/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReaderTest.java`

**步骤**：

1. 先补失败测试：development 的 `implementationNote`、`selfTestResult` 为 required；null、空白字符串、空数组、false、0 的语义稳定；effective writable 中的每个字段经过 merge 后内容确实变化。
2. 增加不可变 `FieldContract`/variant projection；workflow JSON 只补 label、顺序和用途。
3. 对齐 `writableFields` 与 `merge`，未被 merge 支持的 writing identity 字段暂不进入 effective writable projection。
4. 将 policy projection 通过 `saveContract` 投影到 focused context，保留现有 requirement 行为。

**验收命令**：

```bash
mvn -Dtest=StoryNodeWorkbenchPolicyTest,AiDevelopmentComponentReaderTest test
```

**完成条件**：字段契约只有一个权威来源；测试覆盖每个 effective writable 字段和 required/empty 语义；review 通过。

## T1：故事节点只读对话和 NEEDS_INPUT

**Blocked by**：T0

**交付行为**：Claude/Codex 可以通过 CLI 创建 story conversation、读取指定节点上下文、提交 draft，并收到可信的 `nodeContract`、`missingFields` 和 `questions`；缺少必填字段时不创建 operation。

**修改范围**：

- Backend：`AiOrchestratorIntent`、`AiAgentDraftValidator`、`StoryNodeContract`、`StoryNodeContractResolver`、`AiOrchestratorRequest/Response`、`AiOrchestratorService/Controller`
- Connector：`packages/pms-contracts/src/orchestrator.ts`、`packages/pms-client/src/OrchestratorClientTypes.ts`、`packages/pms-client/src/orchestrator.ts`
- CLI：`apps/pms-cli/src/cli/runCli.ts`、新增 `apps/pms-cli/src/commands/scenarios/storyOrchestrator.ts`
- Tests：Backend resolver/controller tests、Connector contract/client tests、CLI route tests

**步骤**：

1. 先写失败测试：story turn 外层缺失/非法 `nodeId/contextVersion` 被拒绝；draft envelope 不接受重复节点字段；sourceRef 只允许主资源、可读 relation、当前节点和当前组件。
2. 接入 `STORY_NODE_FIELD_UPDATE`，创建 story conversation 并绑定 resource ID。
3. story turn 读取 focused context，生成 node contract；缺少 required 字段时返回 `NEEDS_INPUT + STORY_MISSING_INPUT`，不调用 preview。
4. 规定 Conversation/Plan 目标约束：当前 Plan 未结束时提交不同 nodeId 返回 `CONVERSATION_TARGET_MISMATCH`。
5. CLI 输出纯 JSON：conversation、nodeContract、missingFields、questions 和事件 cursor。

**验收命令**：

```bash
mvn -Dtest=AiAgentDraftValidatorTest,StoryNodeContractResolverTest,AiOrchestratorControllerTest test
cd /Users/fs/Desktop/Project/pms-ai-connector && pnpm test -- tests/contract/story-orchestrator-contract.test.ts tests/unit/story-orchestrator-cli.test.ts && pnpm typecheck
```

**完成条件**：只读路径可演示；NEEDS_INPUT 没有 preview operation；invalid field/ref/target 都有结构化错误；review 通过。

## T2：完整草稿生成 Plan 并 Preview

**Blocked by**：T1

**交付行为**：当 draft 补齐 required 字段后，CLI 可以生成 `PLAN_READY`，并通过现有 `development-item.node.field.update` 创建受版本保护的 preview。

**修改范围**：

- Backend：`AiOrchestratorService`、`DefaultAiConnectorPort`、`AiOrchestratorResponse` 及对应 service/controller tests
- Connector：orchestrator schema、client types 和 story orchestrator CLI tests
- Session contract：`pms-ai-connector/docs/orchestrator-session-contract.md`

**步骤**：

1. 先写失败测试：完整 story draft 生成 `PLAN_READY`；patch key 必须是 effective writable；preview 参数包含 STORY、story ID、node ID、node version、component path。
2. `NEEDS_INPUT` 的后续 turn 创建新 Plan；服务端将前一待补充 Plan 的有效 patch 与本轮 patch 合并，在新鲜 context 上重新校验，旧 Plan 不修改。
3. 复用现有 `CommandPreviewService`，不直接写 story 业务表。
4. Plan 持久化 story/node/version/context snapshot、draft、previewOperationId 和结构化校验错误。
5. 对 requirement response 使用非空序列化，确保旧 requirement client 不看到 story 专属字段。

**验收命令**：

```bash
mvn -Dtest=AiOrchestratorServiceTest,AiOrchestratorControllerTest test
cd /Users/fs/Desktop/Project/pms-ai-connector && pnpm test -- tests/contract/orchestrator-client.test.ts tests/contract/story-orchestrator-client.test.ts && pnpm typecheck
```

**完成条件**：完整草稿可以得到可审阅 preview；任何 preview 都绑定当前 node/version；review 通过。

## T3：Confirm、Execute、Readback 和 Verification

**Blocked by**：T2

**交付行为**：用户确认后，系统只能执行已预览的 Operation，完成 story/node/component 回读验证，并返回 `SUCCEEDED`、`CONFLICT` 或结构化失败。

**修改范围**：

- Backend：`AiOrchestratorState`、`AiOrchestratorEventType`、`AiOrchestratorVerificationService`、`AiOrchestratorService`、`AiConnectorPort`、`DefaultAiConnectorPort`
- Tests：state transition、verification、service/controller tests
- Connector：confirm/execute/session response schemas and CLI route tests

**步骤**：

1. 先写失败测试：没有 preview 不能 confirm；confirm 必须带幂等键；preview 和 execute 使用同一个 `pms_ai_operation`；不同 node/version 的旧 preview 返回 conflict。
2. 增加 `NEEDS_INPUT`、`WRITE_RESULT_UNKNOWN` 状态，以及 `INPUT_REQUIRED`、`READBACK_UNKNOWN` 事件和对应 transition，保持 requirement 状态字符串兼容。
3. confirm 复用已有执行服务，要求预览人与确认人是同一认证主体；`confirmedAt` 和 Operation `userId` 作为确认事实。
4. 验证服务重新读取同一 story/node/component，检查 patch、resource、context/node/version 和 protected fields；`operation.data` 不作为成功证明。
5. CLI 输出 operation、verification 和事件序列。

**验收命令**：

```bash
mvn -Dtest=AiOrchestratorStateTest,AiOrchestratorVerificationServiceTest,AiOrchestratorServiceTest test
cd /Users/fs/Desktop/Project/pms-ai-connector && pnpm test -- tests/unit/story-orchestrator-cli.test.ts tests/contract/story-orchestrator-client.test.ts && pnpm typecheck
```

**完成条件**：正常写入闭环可演示；没有 preview、旧版本和受保护字段都无法写入；成功结果包含 verification 和事件；review 通过。

## T4：跨进程恢复、幂等和 WRITE_RESULT_UNKNOWN

**Blocked by**：T3

**交付行为**：CLI 进程退出后，另一个 CLI 进程只凭 conversation/plan ID 恢复状态；重放同一幂等键不产生第二次写入；回读未知时只能重试验证。

**修改范围**：

- Backend：`AiOrchestratorService`、session mapper/persistence、operation recovery tests
- Connector：`PmsClient`、`AgentWorkSession`、`docs/orchestrator-session-contract.md`
- Tests：session recovery、concurrent confirm、same/different key、readback unknown

**步骤**：

1. 先写失败测试：新进程 session 能恢复 preview、operation、verification 和确认事实；同一 key 返回同一 Operation；不同 key 返回 conflict。
2. 明确一个 Plan 对应一个 Operation；session 从 `previewOperationId` 重新读取 Operation，而不是由 CLI mapper 生成时间或确认人。
3. 增加并发 confirm 的持久化锁/幂等检查，只有一个请求进入 EXECUTING。
4. `WRITE_RESULT_UNKNOWN` 的同 key 请求只执行 readback verification；没有新的 execute 调用。
5. 更新 session contract，明确 latest Plan、事件 cursor 和跨节点规则。

**验收命令**：

```bash
mvn -Dtest=AiOrchestratorServiceTest,AiOrchestratorControllerTest test
cd /Users/fs/Desktop/Project/pms-ai-connector && pnpm test -- tests/contract/orchestrator-session.test.ts tests/unit/orchestrator-session.test.ts && pnpm typecheck
```

**完成条件**：两个独立 CLI 进程可以继续同一会话；幂等和未知结果行为可观测且不会重复写入；review 通过。

## T5：真实 PMS 隔离环境验收

**Blocked by**：T4

**交付行为**：在真实 PMS Backend、Connector 和 CLI 上验证故事节点闭环，测试配置缺失时失败而不是全量 skip。

**新增/修改文件**：

- `pms-ai-connector/tests/e2e/orchestrator-story-node.e2e.test.ts`
- `pms-ai-connector/tests/e2e/support/storyFixture.ts`
- 新增 `pms-ai-connector/tests/e2e/support/operationAudit.ts`
- `pms-ai-connector/package.json`
- `pms-backend/src/test/java/com/brad/pms/ai/orchestrator/AiOrchestratorIsolatedAcceptanceTest.java`
- `pms-backend/docs/agent-contracts/pms/story-node-orchestrator-e2e.md`

**步骤**：

1. 运行前校验 PMS URL、CLI auth、测试账号、workflow fixture、容器和 `PMS_E2E_DB_URL`；缺失配置直接失败。
2. 创建本次 run 的隔离 story 和 workflow node，记录所有资源 ID 和 runStart 时间。
3. 验证读取、NEEDS_INPUT、无 operation、完整 preview、confirm、execute、readback 和事件序列。
4. 启动两个独立 CLI 进程覆盖跨进程恢复；覆盖无效字段、无效 sourceRef、无权限 relation、preview 前后版本冲突。
5. 模拟回读未知，验证 WRITE_RESULT_UNKNOWN、同 key 重试和不同 key conflict。
6. `operationAudit.ts` 使用只读数据库账号查询现有 `pms_ai_operation` 的时间窗、command_name 和 arguments_json；不新增生产查询 API。
7. 使用 `finally` 清理隔离资源；清理失败时打印资源 ID 并使测试失败；断言至少有一个测试实际执行。

**验收命令**：

```bash
cd /Users/fs/Desktop/Project/pms-ai-connector
pnpm run verify:real-pms:story-orchestrator --maxWorkers=1 --minWorkers=1
```

**完成条件**：真实环境全链路通过；没有测试通过 skip 伪装成绿色；review 通过。

## T6：最终发布门禁

**Blocked by**：T5

**交付行为**：确认实现没有引入第二套故事写入协议、破坏 requirement 兼容性或绕过 preview/verification。

**检查项**：

- Connector typecheck、契约测试、CLI build。
- Backend Orchestrator、command、migration 关键测试和打包。
- 前端保持不变，但登录和 CLI 授权 smoke test 可用。
- `git diff --check`、migration 语法和干净 checkout 验证。
- 所有 story write 调用都具备 preview operation ID、幂等键、版本检查和 readback verification。
- 使用 `superpowers:requesting-code-review` 做最终独立 review。
- 若出现 Critical/Important 问题，修复后重新运行受影响任务，不直接进入发布。

**完成定义**：

1. T0-T5 的验收命令全部通过。
2. NEEDS_INPUT、非法字段、受保护字段、未授权引用、版本冲突、幂等重放和回读未知均有测试。
3. Claude/Codex 可以通过 CLI 在不同进程中继续会话。
4. 子 Agent 委托没有混入 v1 写入路径。
5. 最终 review 无 Critical/Important 未处理问题。
