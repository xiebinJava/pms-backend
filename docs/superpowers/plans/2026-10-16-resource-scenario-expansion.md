# 需求、专题、项目 Agent 场景扩展实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (or superpowers:subagent-driven-development) to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不建设完整 Orchestrator 会话层的前提下，让 Claude/Codex 通过 `pms-cli` 对需求、专题、项目使用统一的 context → draft → plan → execute → readback 安全闭环。

**Architecture:** PMS Backend 继续作为能力、权限、上下文、预览、执行和业务事实的唯一来源；`pms-ai-connector` 增加无会话的资源场景协议和适配器。先抽取稳定的资源场景接口，再逐个接入需求、专题、项目；每个适配器复用现有 capability、operation preview/execute 和资源回读，不复制业务写入逻辑。

**Tech Stack:** Java 17 / Spring Boot / MyBatis-Plus / JUnit 5 / TypeScript / Zod / pnpm / Vitest 或现有测试工具 / Docker Testcontainers。

**Spec:** `docs/superpowers/specs/2026-10-16-resource-scenario-expansion-design.md`

## Global Constraints

- 本计划不新增 Conversation、Turn、Plan 或 session 事件持久化；每次调用必须自包含。
- `plan` 只读并生成预览；`execute` 必须绑定 `previewOperationId` 和 `idempotencyKey`。
- Backend capability/context 是字段、动作、权限和版本的唯一权威来源。
- execute 成功后必须回读资源和 workflow context；回读不完整时返回 `WRITE_RESULT_UNKNOWN`。
- 保留现有 `project-create`、`requirement-analysis`、`resource-write`、`story-workbench` CLI 兼容入口。
- 需求、专题、项目的自然语言场景 v1 不包含删除、归档、节点完成、回退和批量写入。
- Connector schema 先于 Backend 的新增投影发布；不新增数据库 migration。

## Review Focus

- plan 被误执行写入：T0/T1 的 contract tests 必须验证 plan 后资源版本和数据不变。
- execute 绕过 preview 或幂等键：T0/T4 必须验证请求在调用 PMS 写接口前失败。
- 资源版本与节点版本混用：T1/T2/T3 必须分别断言 entityVersion、contextVersion 和 node version。
- 三类资源的引用权限泄漏：T1/T2/T3 必须验证不可读项目/专题/故事引用被拒绝或过滤。
- 回读不完整却报告成功：T4 必须模拟资源回读、context 回读和 patch 验证分别失败的情况。

---

### Task 0: 建立统一资源场景协议和适配器注册表

**Files:**
- Create: `pms-cli/packages/pms-contracts/src/resourceScenario.ts`
- Create: `pms-cli/apps/pms-cli/src/commands/scenarios/resourceScenarioExecutor.ts`
- Create: `pms-cli/apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts`
- Modify: `pms-cli/packages/pms-contracts/src/index.ts`
- Modify: `pms-cli/apps/pms-cli/src/cli/runCli.ts`
- Test: `pms-cli/tests/contract/resource-scenario-contract.test.ts`
- Test: `pms-cli/tests/unit/resource-scenario-dispatch.test.ts`

**Interfaces:**
- Consumes: `PmsClient.context`, `PmsClient.get`, `operationPreview`, `operationExecutePreview`, `CapabilityResolver`。
- Produces: `resourceScenarioContractVersion = "resource-scenario/v1"`；`ResourceScenarioPlan`；`ResourceScenarioResult`；`ResourceScenarioAdapter`；`planResourceScenario` 和 `executeResourceScenario`。`resourceScenarioExecutor.ts` 只做协议执行和通用回读，`resourceScenarioRegistry.ts` 只做 adapter 注册和查找。

```ts
export interface ResourceScenarioAdapter {
  resourceType: "requirement" | "topic" | "project";
  supports(operation: string): boolean;
  loadContext(client: PmsClient, input: ResourceScenarioInput): Promise<ResourceScenarioContext | null>;
  normalizeArguments(
    input: ResourceScenarioInput,
    context: ResourceScenarioContext | null,
  ): Promise<Record<string, unknown>>;
  validate(
    input: ResourceScenarioInput,
    context: ResourceScenarioContext | null,
    normalizedArguments: Record<string, unknown>,
  ): void;
  resolveReadbackId(
    input: ResourceScenarioInput,
    operation: OperationResult,
  ): number | null;
  verifyReadback(
    before: ResourceScenarioSnapshot | null,
    after: ResourceScenarioSnapshot,
    normalizedArguments: Record<string, unknown>,
  ): ResourceScenarioVerification;
}

export function planResourceScenario(
  client: PmsClient,
  input: ResourceScenarioInput,
): Promise<ResourceScenarioPlan>;

export function executeResourceScenario(
  client: PmsClient,
  input: ResourceScenarioExecuteInput,
): Promise<ResourceScenarioResult>;
```

- [x] **Step 1: 写失败 contract tests**
  - 断言 plan/execute envelope 必须包含 `resourceType`、`operation`、`context`、`preview`、`verification` 和 `refreshScopes`。
  - 断言 `resourceType` 只允许 `requirement|topic|project`；不支持的 operation、缺 preview、缺幂等键和 execute 携带 arguments 均在调用 client 前失败。
  - 断言 adapter registry 能按资源类型和 operation 找到适配器，找不到时返回稳定错误码。
- [x] **Step 2: 运行失败测试**
  - Run: `pnpm vitest run tests/contract/resource-scenario-contract.test.ts tests/unit/resource-scenario-dispatch.test.ts`
  - Expected: FAIL，因为协议 schema、注册表和 dispatch 尚不存在。
- [x] **Step 3: 实现最小协议和共用执行器**
  - 在 `packages/pms-contracts/src/resourceScenario.ts` 用 Zod 定义输入、plan、execute 和 verification schema。
  - 在 `resourceScenarioExecutor.ts` 实现 `planResourceScenario` 和 `executeResourceScenario`：plan 负责加载 capability/context、规范化 arguments、调用现有 `operationPreview`，不得调用 execute；execute 只接受 `previewOperationId` 和 `idempotencyKey`，调用 `operationExecutePreview`，再按 adapter 的资源定位回读资源和 context。
  - 在 `resourceScenarioRegistry.ts` 定义 `ResourceScenarioAdapter` 注册接口；adapter 只提供资源类型、支持的操作、资源 ID 提取、上下文读取方式和 patch 验证，不实现 PMS 领域写入。
  - 在 CLI 增加兼容性新入口：`pms scenario resource plan|execute <requirement|topic|project> <operation> ...`，旧入口继续保留。
- [x] **Step 4: 运行通过测试**
  - Run: `pnpm vitest run tests/contract/resource-scenario-contract.test.ts tests/unit/resource-scenario-dispatch.test.ts`
  - Expected: PASS。
- [x] **Step 5: 提交**
  - `git add packages/pms-contracts/src/resourceScenario.ts packages/pms-contracts/src/index.ts apps/pms-cli/src/commands/scenarios/resourceScenarioExecutor.ts apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts apps/pms-cli/src/cli/runCli.ts tests/contract/resource-scenario-contract.test.ts tests/unit/resource-scenario-dispatch.test.ts`
  - `git commit -m "feat: add resource scenario contract"`

### Task 1: 接入需求场景

**Blocked by:** Task 0

**Files:**
- Create: `pms-cli/apps/pms-cli/src/commands/scenarios/requirementScenarioAdapter.ts`
- Modify: `pms-cli/apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts`
- Modify: `pms-cli/packages/pms-client/src/PmsClient.ts`
- Test: `pms-cli/tests/contract/requirement-resource-scenario.test.ts`
- Test: `pms-backend/src/test/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadServiceTest.java`（仅在发现 context 契约缺口时修改）

**Interfaces:**
- Consumes: Task 0 的 `ResourceScenarioAdapter` 和 `resource-scenario/v1`。
- Produces: `requirementScenarioAdapter`，支持 `requirement.update`、`requirement.analysis.save`、`requirement.execution-target.link/change/unlink`、`development-item.node.field.update`，并注册到 Task 0 的 registry。

- [x] **Step 1: 写失败需求场景测试**
  - 有效需求分析 patch 能读取需求详情和 focused context，preview 参数包含 requirementId、nodeId、node version 和 context identity。
  - 对 capability/context 声明为 required 的分析字段，缺失、未知字段、受保护字段、旧 node/context version 均在 preview 前失败；adapter 不自行硬编码字段规则。
  - 执行成功后同时回读 requirement detail 和 workflow context；回读任一失败返回 `WRITE_RESULT_UNKNOWN`。
  - 相同幂等键重放返回同一 operation，不再调用第二次 execute。
- [x] **Step 2: 运行失败测试**
  - Run: `pnpm vitest run tests/contract/requirement-resource-scenario.test.ts`
  - Expected: FAIL，因为 requirement adapter 尚未注册。
- [x] **Step 3: 实现需求 adapter**
  - 在 `requirementScenarioAdapter.ts` 复用现有 `requirementAnalysis` 的字段/事实读取逻辑，不复制 `AnalysisDraftValidator` 的规则。
  - 对基本信息和执行对象操作使用 capability input schema；对节点字段操作使用 focused context 的 `saveContract` 和 node version。
  - 预览和执行统一经过 Task 0 的执行器；成功后验证资源版本、节点版本和 patch 字段。
  - 不新增 requirement Conversation API，保留现有 `requirement-analysis orchestrate` 入口不变。
- [x] **Step 4: 运行通过测试**
  - Run: `pnpm vitest run tests/contract/requirement-resource-scenario.test.ts`
  - Expected: PASS。
- [x] **Step 5: 提交**
  - `git add apps/pms-cli/src/commands/scenarios/requirementScenarioAdapter.ts apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts packages/pms-client/src/PmsClient.ts tests/contract/requirement-resource-scenario.test.ts`
  - `git commit -m "feat: add requirement resource scenarios"`

### Task 2: 接入专题场景

**Blocked by:** Task 0

**Files:**
- Create: `pms-cli/apps/pms-cli/src/commands/scenarios/topicScenarioAdapter.ts`
- Modify: `pms-cli/apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts`
- Test: `pms-cli/tests/contract/topic-resource-scenario.test.ts`
- Test: `pms-backend/src/test/java/com/brad/pms/service/TopicStoryNodeAssociationTest.java`（仅补 AI 场景所需的挂载/权限回归断言）

**Interfaces:**
- Consumes: Task 0 的共用执行器和 Task 1 验证约定。
- Produces: `topicScenarioAdapter`，支持 `topic.create`、`topic.update`、`topic.project.link`、`development-item.node.field.update`，并注册到 registry。

- [x] **Step 1: 写失败专题场景测试**
  - 专题创建使用 global context，成功后能从 operation data 得到 topicId 并回读 topic detail/context。
  - 专题更新和项目关联必须读取当前专题/目标项目 context；目标项目不可读、已完成或已终止时 preview 失败。
  - 专题节点字段更新必须校验 `sourceProjectNodeKey`/项目上下文和节点 version，不得用项目默认模板重新推导旧专题挂载点。
  - 回读结果必须验证专题项目关系和流程节点没有漂移。
- [x] **Step 2: 运行失败测试**
  - Run: `pnpm vitest run tests/contract/topic-resource-scenario.test.ts`
  - Expected: FAIL，因为 topic adapter 尚未注册。
- [x] **Step 3: 实现专题 adapter**
  - 在 `topicScenarioAdapter.ts` 中，创建操作使用 capability schema 和 global context；更新/关联操作使用实际资源 context。
  - 节点字段操作只使用 context 返回的已绑定节点、saveContract 和版本；不读取默认模板覆盖历史快照。
  - readback 同时检查 topic resource、workflow context 和项目关系。
- [x] **Step 4: 运行通过测试**
  - Run: `pnpm vitest run tests/contract/topic-resource-scenario.test.ts`
  - Expected: PASS。
- [x] **Step 5: 提交**
  - `git add apps/pms-cli/src/commands/scenarios/topicScenarioAdapter.ts apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts tests/contract/topic-resource-scenario.test.ts`
  - `git commit -m "feat: add topic resource scenarios"`

### Task 3: 接入项目场景

**Blocked by:** Task 0

**Files:**
- Create: `pms-cli/apps/pms-cli/src/commands/scenarios/projectScenarioAdapter.ts`
- Modify: `pms-cli/apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts`
- Modify: `pms-cli/apps/pms-cli/src/commands/scenarios/projectCreate.ts`（将已有逻辑接入统一结果 envelope，保留兼容导出）
- Test: `pms-cli/tests/contract/project-resource-scenario.test.ts`
- Test: `pms-cli/tests/contract/project-create-readback.test.ts`

**Interfaces:**
- Consumes: Task 0 的共用执行器、Task 1/2 的 verification 结构。
- Produces: `projectScenarioAdapter`，支持 `project.create`、`project.update`、`node.field.update`；旧 `project-create` 结果可映射到新 envelope。

- [x] **Step 1: 写失败项目场景测试**
  - 项目创建 plan 使用 global context，不能因为没有项目 ID 而跳过 preview。
  - 项目更新必须读取 entityVersion；项目节点字段更新必须使用 `node.field.update`，同时校验 project context version、node version 和 workbench saveContract。
  - 创建成功后必须回读项目详情和流程上下文；更新成功后必须验证 patch 和受保护字段未改变。
  - 项目流程模板/项目类型不可用时 plan 返回能力错误，不允许由 CLI 猜测模板 ID。
- [x] **Step 2: 运行失败测试**
  - Run: `pnpm vitest run tests/contract/project-resource-scenario.test.ts tests/contract/project-create-readback.test.ts`
  - Expected: FAIL，因为 project adapter 尚未统一到新 envelope。
- [x] **Step 3: 实现项目 adapter**
  - 在 `projectScenarioAdapter.ts` 复用现有 `planProjectCreateScenario` 和 `createProjectWithReadback`，只做协议映射和共用验证，不复制创建逻辑。
  - 项目节点字段写入复用 `node.field.update`，由 project context 返回的 `saveContract` 决定字段和版本。
  - 保留旧 `scenario project-create` 路由，并在新 `scenario resource project` 路由返回相同 operation/readback 事实。
- [x] **Step 4: 运行通过测试**
  - Run: `pnpm vitest run tests/contract/project-resource-scenario.test.ts tests/contract/project-create-readback.test.ts`
  - Expected: PASS。
- [x] **Step 5: 提交**
  - `git add apps/pms-cli/src/commands/scenarios/projectScenarioAdapter.ts apps/pms-cli/src/commands/scenarios/resourceScenarioRegistry.ts apps/pms-cli/src/commands/scenarios/projectCreate.ts tests/contract/project-resource-scenario.test.ts tests/contract/project-create-readback.test.ts`
  - `git commit -m "feat: add project resource scenarios"`

### Task 4: 统一三类场景的 CLI、契约和错误行为

**Blocked by:** Tasks 1, 2, 3

**Files:**
- Modify: `pms-cli/apps/pms-cli/src/cli/runCli.ts`
- Modify: `pms-cli/apps/pms-cli/src/commands/scenarios/resourceScenarioExecutor.ts`
- Modify: `pms-cli/packages/pms-contracts/src/error.ts`
- Test: `pms-cli/tests/contract/resource-scenario-error-matrix.test.ts`
- Test: `pms-cli/tests/unit/cli-runner.test.ts`

**Interfaces:**
- Consumes: 三个资源 adapter 和统一 result envelope。
- Produces: 稳定 CLI 路由、错误码和 JSON 字段顺序/语义；不引入 session API。

- [x] **Step 1: 写失败错误矩阵测试**
  - 覆盖 `RESOURCE_NOT_FOUND`、`FORBIDDEN`、`CAPABILITY_UNAVAILABLE`、`CONTEXT_CONFLICT`、`PREVIEW_REQUIRED`、`IDEMPOTENCY_REQUIRED`、`WRITE_RESULT_UNKNOWN`。
  - 同一错误在 requirement/topic/project 的 stderr envelope 中都包含 `code`、`message`、`requestId` 和可选 `details`。
  - `plan` 输出不包含 execute operation result；`execute` 输出包含 `previewOperationId`、`verification`、`warnings`、`refreshScopes`。
- [x] **Step 2: 运行失败测试**
  - Run: `pnpm vitest run tests/contract/resource-scenario-error-matrix.test.ts tests/unit/cli-runner.test.ts`
  - Expected: FAIL，直到路由和错误映射统一。
- [x] **Step 3: 实现路由和错误映射**
  - 将资源类型、operation、arguments/draft、nodeId/contextVersion 解析为统一输入。
  - 对旧命令保留原错误语义；新路由只返回 `resource-scenario/v1` envelope。
  - 不自动重试 execute；用户需要使用原 preview/idempotency key 重新查询或重放。
- [x] **Step 4: 运行通过测试**
  - Run: `pnpm vitest run tests/contract/resource-scenario-error-matrix.test.ts tests/unit/cli-runner.test.ts`
  - Expected: PASS。
- [x] **Step 5: 提交**
  - `git add apps/pms-cli/src/cli/runCli.ts apps/pms-cli/src/commands/scenarios/resourceScenarioExecutor.ts packages/pms-contracts/src/error.ts tests/contract/resource-scenario-error-matrix.test.ts tests/unit/cli-runner.test.ts`
  - `git commit -m "feat: unify resource scenario cli errors"`

### Task 5: 真实 PMS 隔离 E2E 和使用文档

**Blocked by:** Task 4

**Files:**
- Create: `pms-cli/tests/e2e/resource-scenario-real-pms.test.ts`
- Modify: `pms-cli/scripts/e2e-mysql-client.sh`
- Create: `pms-cli/docs/resource-scenario-runbook.md`
- Modify: `pms-cli/docs/e2e-report.md`

**Interfaces:**
- Consumes: `resource-scenario/v1` CLI contract and existing isolated PMS/Testcontainers setup。
- Produces: 可重复的 requirement/topic/project 三条真实流程验收和 Claude/Codex 调用示例。

- [x] **Step 1: 写失败/未配置即失败的 E2E**
  - 每个资源至少创建或选择一个隔离 fixture，运行 plan → execute → readback。
  - 明确断言 plan 阶段数据库业务数据不变、execute 成功后版本递增、相同幂等键不重复写入。
  - 缺少 `PMS_E2E_BASE_URL`、认证或 MySQL 查询能力时测试失败并说明缺失，不通过全量 skip 伪造绿色。
- [x] **Step 2: 运行 E2E 验证失败原因**
  - Run: `pnpm vitest run tests/e2e/resource-scenario-real-pms.test.ts`
  - Expected: 未配置环境时明确失败；配置隔离 PMS 后才能通过。
- [x] **Step 3: 接入容器内 MySQL 查询和清理**
  - 固化测试需要的 MySQL 客户端/容器执行方式、fixture 前缀、清理 SQL 和 operation 审计查询。
  - E2E 只使用专用测试账号和隔离资源 ID，不连接用户现有项目。
- [x] **Step 4: 运行真实验收**
  - Run: `pnpm vitest run tests/e2e/resource-scenario-real-pms.test.ts`
  - Expected: requirement、topic、project 三条主流程 PASS，输出 operationId、auditId、previewOperationId 和 verification。
- [x] **Step 5: 写 CLI 使用文档并提交**
  - 文档包含自然语言到 CLI 参数的映射、plan/execute 示例、确认前检查、冲突恢复和 `WRITE_RESULT_UNKNOWN` 处理。
  - `git add tests/e2e/resource-scenario-real-pms.test.ts scripts/e2e-mysql-client.sh docs/resource-scenario-runbook.md docs/e2e-report.md`
  - `git commit -m "test: verify resource scenarios against isolated pms"`

## 依赖与执行顺序

按 `Task 0 → Task 1 → Task 2 → Task 3 → Task 4 → Task 5` 顺序执行。Task 1、2、3 理论上可以并行，但它们会共同修改资源场景注册表和协议映射；为了遵守“一个任务完成并 review 后再做下一个任务”，实际执行采用串行顺序。

每个任务完成后必须：

1. 运行该任务的 focused tests。
2. 检查 git diff 和兼容性。
3. 做一次 review，Critical/Important 问题关闭后再开始下一个任务。

## 完成定义

- TypeScript typecheck 和完整 Connector 测试通过。
- Backend 相关上下文/能力回归测试通过；若修改 Backend，补充 Maven focused tests。
- 隔离真实 PMS E2E 三类资源均通过，且没有通过全量 skip 掩盖配置问题。
- 旧 CLI 命令行为不变，新命令输出 `resource-scenario/v1`。
- 文档能够让 Claude/Codex 在没有前端的情况下完成一次 plan → 确认 → execute → readback。
