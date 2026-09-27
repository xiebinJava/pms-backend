# PMS AI Connector Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建独立的 `pms-ai-connector` 项目，通过同一套 PMS 能力协议同时支持 OpenCLI 和 MCP，并在不增加人工确认步骤的情况下安全执行 PMS 读写操作。

**Architecture:** PMS 后端继续作为唯一业务真源，在现有命令注册表、领域 Service、权限和审计链路之上增加中立的 `/integration/ai/v1` 集成门面。连接器只维护 TypeScript API Client、共享 Schema、MCP Server 和 OpenCLI Plugin；两种入口共同调用 PMS，不复制业务逻辑。

**Tech Stack:** PMS：Spring Boot 3.5、Java 17、MyBatis-Plus、Flyway、JUnit 5；Connector：TypeScript、Node.js 24.10.0（兼容 Node.js 22+）、pnpm 10.18.2、MCP SDK、OpenCLI Plugin API、Vitest、Docker。

**Spec:** `docs/superpowers/specs/2026-09-27-pms-ai-connector-design.md`

## Global Constraints

- 连接器必须位于独立仓库 `/Users/fs/Desktop/Project/pms-ai-connector`，不能把 OpenCLI 或 MCP 运行时依赖直接塞进 PMS 前端。
- PMS 后端是唯一业务真源；连接器不得访问数据库、绕过权限或复制项目/需求/专题/故事/任务的业务规则。
- 新连接器写操作默认自动执行，不增加人工确认；但每次操作必须经过 PMS 的权限、业务规则、版本、幂等和审计校验。
- 现有 `/integration/dsh/v1` 预览/确认接口保持兼容，不因新连接器改变旧 DSH UI 的行为。
- 能力、流程模板、流程节点、组件和字段必须通过能力目录动态发现，不得硬编码“开发与迭代控制”等节点名称。
- 需求只能关联一个执行对象：项目、专题或故事三者之一；连接器只能调用后端规则，不能自行拆分或绕过。
- 迭代计划不绑定流程模板，不创建流程节点。
- 负责人、节点负责人和任务执行人的成员自动加入、解绑清理由 PMS 后端领域服务处理。
- 每个写操作必须携带当前用户 Token、`clientId`、`requestId`、`idempotencyKey` 和可选的 `expectedVersion`。
- 连接器不记录密码、长期 Token 或敏感字段原文；错误响应不得泄露 SQL、堆栈和内部凭据。
- 每个阶段必须完成聚焦测试、权限/边界检查、diff Review 后才能进入下一阶段。

## Review Focus

- 自动执行重试：相同幂等键不得重复创建项目、专题、故事或任务；由 Task 2 的自动执行测试覆盖。
- 动态流程变更：流程模板把组件移动到另一个节点后，连接器必须从能力目录读取新位置；由 Task 3 的动态能力测试覆盖。
- 单一需求执行对象：需求不能同时关联项目和专题，替换关联必须保留历史；由 Task 3 的业务合约测试覆盖。
- 无权限和越权请求：能力目录过滤不能代替服务端校验，直接调用也必须拒绝；由 Task 2 和 Task 4 的权限测试覆盖。
- 多客户端一致性：同一操作从 MCP 和 OpenCLI 发起时必须得到一致的 PMS 结果和审计记录；由 Task 5 的集成测试覆盖。

## 文件与模块边界

### PMS 后端现有模块

- `src/main/java/com/brad/pms/ai/command/PmsCommandRegistry.java`：稳定命令注册表，继续作为写能力的白名单。
- `src/main/java/com/brad/pms/ai/command/PmsCommandMetadata.java`：补齐命令 Schema、风险、权限和刷新范围。
- `src/main/java/com/brad/pms/ai/command/AiOperationService.java`：继续承载持久化操作和幂等执行。
- `src/main/java/com/brad/pms/ai/command/CommandPreviewService.java`：复用内部预览和参数校验。
- `src/main/java/com/brad/pms/ai/command/CommandExecutionService.java`：复用现有命令执行。
- `src/main/java/com/brad/pms/controller/DshCapabilityController.java` 和 `DshCommandController.java`：保持已有 DSH 合约兼容。

### PMS 后端新增模块

- `src/main/java/com/brad/pms/ai/connector/AutomaticOperationRequest.java`：连接器自动执行请求。
- `src/main/java/com/brad/pms/ai/connector/AutomaticCommandExecutionService.java`：内部预览后自动执行，不新增人工确认步骤。
- `src/main/java/com/brad/pms/ai/connector/AiConnectorCapabilityService.java`：对当前用户过滤并组装能力目录。
- `src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java`：统一资源查询、候选项和上下文。
- `src/main/java/com/brad/pms/controller/AiConnectorController.java`：`/integration/ai/v1` HTTP 门面，只做认证、校验、服务编排和响应转换。
- `src/main/java/com/brad/pms/integration/ai/api/`：连接器请求、响应、能力和错误 DTO。
- `src/main/java/com/brad/pms/integration/ai/security/AiConnectorScopePolicy.java`：连接器权限范围白名单。
- `src/main/resources/db/migration/V50__ai_connector_operation_metadata.sql`：记录来源客户端、请求 ID 和自动执行模式；当前 release 分支的最大迁移版本为 V49。

### 新连接器仓库

根目录：`/Users/fs/Desktop/Project/pms-ai-connector`

- `apps/mcp-server/src/server.ts`：MCP Server 入口。
- `apps/mcp-server/src/tools/`：能力发现、查询、自动执行、流程动作工具。
- `apps/opencli-plugin/src/index.ts`：OpenCLI 插件入口。
- `apps/opencli-plugin/src/commands/`：OpenCLI 命令映射。
- `packages/pms-contracts/src/`：请求、响应、错误、能力和资源 Schema。
- `packages/pms-client/src/PmsClient.ts`：PMS HTTP Client，统一 Token、请求 ID、重试和错误处理。
- `packages/pms-capabilities/src/CapabilityResolver.ts`：动态能力和流程组件解析。
- `packages/result-format/src/`：MCP 和 OpenCLI 共用结构化结果格式。
- `skills/pms-project-management/SKILL.md`：给 Agent 的 PMS 使用规则和自然语言映射。
- `tests/contract/`：PMS 后端合约测试。
- `tests/mcp/`：MCP 工具测试。
- `tests/opencli/`：OpenCLI 命令测试。
- `tests/e2e/`：真实 PMS API 闭环测试。
- `deploy/Dockerfile`、`deploy/docker-compose.yml`：MCP Server 部署文件。

## Task 1: 创建连接器仓库和共享协议

**Files:**

- Create: `/Users/fs/Desktop/Project/pms-ai-connector/package.json`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/pnpm-workspace.yaml`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tsconfig.json`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/operation.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/capability.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/query.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/error.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-contracts/src/index.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/schema.test.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/README.md`

**Interfaces:**

```ts
export type ResourceType =
  | "requirement" | "project" | "project_node" | "topic"
  | "topic_node" | "story" | "task" | "subtask"
  | "iteration_plan" | "workflow_template" | "user";

export interface AutomaticOperationRequest {
  operation: string;
  resource?: { type: ResourceType; id?: number };
  arguments: Record<string, unknown>;
  context?: { id: string; version: string };
  expectedVersion?: number;
  idempotencyKey: string;
  clientId: "mcp" | "opencli";
  requestId: string;
}

export interface OperationResult {
  operationId: string;
  status: "SUCCEEDED" | "REJECTED" | "CONFLICT";
  data: Record<string, unknown>;
  warnings: string[];
  refreshScopes: string[];
  auditId?: string;
}
```

- [ ] **Step 1: 创建 pnpm workspace 和最小包结构。** 锁定 Node.js 24.10.0、pnpm 10.18.2、TypeScript strict mode，并让 `pnpm test` 能发现空测试目录；`engines.node` 保留 `>=22`。
- [ ] **Step 2: 写共享 Schema 的失败测试。** 使用 Zod 或选定的 Schema 库断言缺少 `operation`、`idempotencyKey`、`clientId`、`requestId` 时拒绝；断言资源类型和结果状态只能使用枚举值。
- [ ] **Step 3: 实现 `pms-contracts` 类型和运行时 Schema。** 所有 MCP/OpenCLI 输入在进入 `pms-client` 前完成校验；不要在适配器中重新定义同名接口。
- [ ] **Step 4: 写 README 的仓库边界说明。** 明确 PMS 是真源、连接器不访问数据库、自动写入仍执行服务端安全校验。
- [ ] **Step 5: 运行 `pnpm test` 和 `pnpm typecheck`。** 预期全部通过。
- [ ] **Step 6: 自审并提交。** 检查协议是否覆盖需求、项目、专题、故事、任务和迭代计划；提交 `chore: scaffold pms ai connector`。

## Task 2: 增加 PMS 自动执行门面

**Files:**

- Create: `src/main/java/com/brad/pms/ai/connector/AutomaticOperationRequest.java`
- Create: `src/main/java/com/brad/pms/ai/connector/AutomaticCommandExecutionService.java`
- Modify: `src/main/java/com/brad/pms/ai/command/AiOperationService.java`
- Create: `src/main/java/com/brad/pms/integration/ai/api/AiAutomaticExecuteRequest.java`
- Create: `src/main/java/com/brad/pms/integration/ai/api/AiOperationResultDTO.java`
- Create: `src/main/java/com/brad/pms/integration/ai/security/AiConnectorScopePolicy.java`
- Create: `src/main/java/com/brad/pms/controller/AiConnectorController.java`
- Modify: `src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java`
- Modify: `src/main/java/com/brad/pms/entity/AiOperationDO.java`
- Modify: `src/main/java/com/brad/pms/mapper/AiOperationMapper.java`
- Create: `src/main/resources/db/migration/V50__ai_connector_operation_metadata.sql`
- Test: `src/test/java/com/brad/pms/ai/connector/AutomaticCommandExecutionServiceTest.java`
- Test: `src/test/java/com/brad/pms/controller/AiConnectorControllerTest.java`

**Interfaces:**

```java
public record AutomaticOperationRequest(
        CommandName name,
        Map<String, Object> arguments,
        String contextId,
        String contextVersion,
        String contractId,
        String contractVersion,
        String idempotencyKey,
        String clientId,
        String requestId) {}

public interface AutomaticCommandExecutionService {
    CommandResult execute(AutomaticOperationRequest request);
}
```

`execute` 必须在一次服务调用内完成：权限校验 → 命令预览 → 操作持久化 → 幂等检查 → 命令执行 → 结果写回 → 审计信息返回。自动执行的原子性和幂等检查由 `AiOperationService` 承载，门面只负责请求归一化和用户上下文校验；可以复用现有 `PmsCommandRegistry`、`CommandPreviewService`、`CommandExecutionService` 和 `AiOperationService`，不能复制命令的领域逻辑。

- [ ] **Step 1: 写自动执行失败测试。** 覆盖无 Token、无 scope、未知命令、参数不完整、资源权限不足、版本冲突、相同幂等键重试和不同幂等键重复操作。
- [ ] **Step 2: 写迁移测试。** 断言新增来源客户端、请求 ID、执行模式字段；旧记录可以读取，旧 DSH 操作不受影响。
- [ ] **Step 3: 增加 `AiConnectorScopePolicy`。** 至少定义 `pms:query:read`、`pms:command:execute`、`pms:workflow:write`、`pms:project:write`、`pms:development:write`、`pms:iteration:write`，并要求后端权限与连接器 scope 取交集。
- [ ] **Step 4: 实现自动执行服务。** 在 `AiOperationService` 增加带幂等键预占的自动执行事务：先锁定同用户同幂等键的历史记录，再生成内部预览、持久化 `AUTOMATIC_RUNNING` 状态并执行命令；不得把状态暴露为“等待用户确认”，执行失败时事务回滚，重复幂等键返回原结果。
- [ ] **Step 5: 增加上下文规则。** 全局操作在服务端归一化为 `global:pms` 上下文；节点操作必须提供实际对象、节点和版本，不能用全局上下文绕过节点校验。
- [ ] **Step 6: 实现 `/integration/ai/v1/operations/execute`。** Controller 从 `UserContext` 获取当前用户，不接受请求体覆盖用户身份；统一返回业务错误和 `requestId`。
- [ ] **Step 7: 运行 `mvn -q -Dtest=AutomaticCommandExecutionServiceTest,AiConnectorControllerTest test`。** 预期通过。
- [ ] **Step 8: 自审并提交。** 重点检查自动执行没有关闭原有 `PmsCommandScopeGuard`、对象权限、命令版本和事务；提交 `feat: add automatic ai connector execution facade`。

## Task 3: 扩展能力目录、查询和动态流程上下文

**Files:**

- Create: `src/main/java/com/brad/pms/ai/connector/AiConnectorCapabilityService.java`
- Create: `src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java`
- Create: `src/main/java/com/brad/pms/integration/ai/api/AiCapabilityDTO.java`
- Create: `src/main/java/com/brad/pms/integration/ai/api/AiQueryRequest.java`
- Create: `src/main/java/com/brad/pms/integration/ai/api/AiQueryResultDTO.java`
- Create: `src/main/java/com/brad/pms/integration/ai/api/AiWorkflowContextDTO.java`
- Create: `src/main/java/com/brad/pms/ai/command/requirement/CreateRequirementCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/requirement/UpdateRequirementCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/requirement/LinkRequirementTargetCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/CreateTopicCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/UpdateTopicCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/LinkTopicProjectCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/CreateStoryCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/UpdateStoryCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/LinkStoryTopicCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/UpdateDevelopmentItemNodeOwnerCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/UpdateDevelopmentItemNodeScheduleCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/UpdateDevelopmentItemNodeFieldCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/CompleteDevelopmentItemNodeCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/development/CreateDevelopmentItemTaskCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/iteration/IterationPlanUpdateCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/iteration/CreateIterationPlanCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/iteration/AddStoryToIterationPlanCommand.java`
- Create: `src/main/java/com/brad/pms/ai/command/iteration/RemoveStoryFromIterationPlanCommand.java`
- Modify: `src/main/java/com/brad/pms/ai/command/CommandName.java`
- Modify: `src/main/java/com/brad/pms/ai/command/PmsCommandRegistry.java`
- Modify: `src/main/java/com/brad/pms/ai/command/PmsCommandMetadata.java`
- Modify: `src/main/java/com/brad/pms/ai/command/PmsCommandDescriptor.java`
- Modify: `src/main/java/com/brad/pms/controller/AiConnectorController.java`
- Modify: `src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java`
- Modify: `src/main/java/com/brad/pms/service/WorkflowTemplateService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Test: `src/test/java/com/brad/pms/ai/connector/AiConnectorCapabilityServiceTest.java`
- Test: `src/test/java/com/brad/pms/ai/connector/AiConnectorQueryServiceTest.java`
- Test: `src/test/java/com/brad/pms/ai/connector/DynamicWorkflowCapabilityTest.java`

**Interfaces:**

```java
public interface AiConnectorCapabilityService {
    AiCapabilityDTO capabilities();
}

public interface AiConnectorQueryService {
    AiQueryResultDTO query(AiQueryRequest request);
    AiWorkflowContextDTO context(String resourceType, Long resourceId);
}
```

新增命令使用以下稳定编码；具体输入字段由能力目录返回：

```text
requirement.create
requirement.update
requirement.execution-target.link
requirement.execution-target.change
requirement.execution-target.unlink
topic.create
topic.update
topic.project.link
story.create
story.update
story.topic.link
development-item.node.owner.update
development-item.node.schedule.update
development-item.node.field.update
development-item.node.complete
development-item.task.create
iteration-plan.create
iteration-plan.update
iteration-plan.story.add
iteration-plan.story.remove
```

`development-item.*` 命令的参数必须包含 `itemType`、`itemId` 和 `nodeId`，由后端根据项目、专题或故事类型路由到对应领域服务；不允许通过客户端传入任意表名或 URL。

- [ ] **Step 1: 写能力目录测试。** 断言当前用户只能看到有权限的资源和命令；能力包含 action、inputSchema、workflowVersion、node、component、refreshScopes。
- [ ] **Step 2: 写动态流程测试。** 使用两个不同的已发布模板版本，把专题组件绑定到不同项目节点；断言能力目录和上下文返回实际绑定节点，而不是固定节点名。
- [ ] **Step 3: 写查询测试。** 覆盖需求、项目、专题、故事、任务、迭代计划的分页、候选项和无数据响应。
- [ ] **Step 4: 扩展命令元数据。** 为需求、专题、故事、迭代计划和动态节点字段补齐稳定命令名、中文说明、权限 scope、Schema 和刷新范围。
- [ ] **Step 5: 为新命令接入现有领域 Service。** `requirement/*` 调用需求管理和执行对象服务，`topic/*` 调用专题管理服务，`story/*` 调用故事管理服务，`iteration/*` 调用迭代计划服务；禁止在命令类中复制成员同步和关联校验。
- [ ] **Step 6: 实现统一能力和查询服务。** 只调用现有领域 Service 和 Workflow Service；查询结果必须带资源版本和关联摘要。
- [ ] **Step 7: 暴露 `GET /integration/ai/v1/capabilities`、`POST /integration/ai/v1/query` 和 `GET /integration/ai/v1/context/{resourceType}/{resourceId}`。** 普通 PMS 登录 Token 使用用户权限；委托 Token 额外取连接器 scope 与用户权限的交集。
- [ ] **Step 8: 运行聚焦后端测试和已有 DSH 测试。** 运行 `mvn -q -Dtest=AiConnectorCapabilityServiceTest,AiConnectorQueryServiceTest,DynamicWorkflowCapabilityTest,DshCapabilityControllerTest test`。
- [ ] **Step 9: 自审并提交。** 检查新增流程类型、字段和组件不需要重新编译连接器；提交 `feat: expose dynamic pms ai capabilities`。

## Task 4: 实现共享 PMS Client

**Files:**

- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-client/src/PmsClient.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-client/src/AuthProvider.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-client/src/RequestContext.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-client/src/Errors.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-client/src/index.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-capabilities/src/CapabilityResolver.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/packages/pms-capabilities/src/index.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/pms-client.test.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/contract/capability-resolver.test.ts`

**Interfaces:**

```ts
export interface PmsClient {
  capabilities(): Promise<CapabilityCatalog>;
  query(request: QueryRequest): Promise<QueryResult>;
  context(resource: ResourceRef): Promise<WorkflowContext>;
  execute(request: AutomaticOperationRequest): Promise<OperationResult>;
}
```

- [ ] **Step 1: 写 HTTP Mock 测试。** 断言所有请求携带 Bearer Token、`X-Request-Id`、`X-Client-Id`，错误码按 401/403/409/422/429/5xx 分类。
- [ ] **Step 2: 实现 `AuthProvider`。** 第一版支持环境变量或进程注入的短期 Token，不持久化密码；为后续 OAuth 留接口。
- [ ] **Step 3: 实现 `PmsClient`。** 统一 base URL、超时、请求 ID、错误转换和有限重试；409 不自动重试写操作。
- [ ] **Step 4: 实现结构化结果。** 让 MCP 和 OpenCLI 直接复用同一个 `OperationResult`、`QueryResult` 和 `CapabilityCatalog`。
- [ ] **Step 5: 实现 `CapabilityResolver`。** 将后端动态能力目录解析为资源、动作、上下文要求和组件字段；未知组件不得静默降级为固定节点。
- [ ] **Step 6: 运行 `pnpm test --filter pms-client --filter pms-capabilities` 和 `pnpm typecheck`。** 预期通过。
- [ ] **Step 7: 自审并提交。** 确认 Token 不出现在错误、日志或异常对象中；提交 `feat: add shared pms api client`。

## Task 5: 实现 MCP Server

**Files:**

- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/mcp-server/src/server.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/mcp-server/src/tools/capabilities.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/mcp-server/src/tools/query.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/mcp-server/src/tools/execute.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/mcp-server/src/tools/workflow.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/mcp-server/src/transport.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/mcp/tools.test.ts`

**Interfaces:**

MCP 暴露以下稳定工具：

```text
pms_capabilities
pms_search
pms_get
pms_get_context
pms_execute_operation
pms_workflow_action
```

- [ ] **Step 1: 写 MCP 工具测试。** 断言工具 Schema 与 `pms-contracts` 完全一致，读工具调用查询，写工具调用自动执行接口。
- [ ] **Step 2: 实现 stdio transport。** 用于本地 DeepSeek Harness 和其他本地 MCP 客户端。
- [ ] **Step 3: 实现 HTTP transport。** 仅允许配置的 HTTPS/反向代理环境；生产环境拒绝不安全的任意来源配置。
- [ ] **Step 4: 实现工具错误映射。** 候选项、权限不足、业务冲突和版本冲突返回结构化字段，不能只返回自然语言。
- [ ] **Step 5: 运行 `pnpm test --filter mcp-server`。** 预期通过，并验证读写都带请求 ID。
- [ ] **Step 6: 自审并提交。** 确认 MCP 工具没有暴露任意 URL、任意 HTTP 或数据库能力；提交 `feat: add pms mcp server`。

## Task 6: 实现 OpenCLI Plugin 和 Agent Skill

**Files:**

- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/opencli-plugin/src/index.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/opencli-plugin/src/commands/capabilities.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/opencli-plugin/src/commands/query.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/opencli-plugin/src/commands/execute.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/apps/opencli-plugin/src/commands/workflow.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/opencli/commands.test.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/skills/pms-project-management/SKILL.md`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/README.md`

**Interfaces:**

命令保持稳定的领域命名：

```text
opencli pms capability list
opencli pms requirement search
opencli pms project get --id 78
opencli pms topic create
opencli pms story create
opencli pms task create
opencli pms workflow action
opencli pms iteration-plan add-story
```

- [ ] **Step 1: 写命令测试。** 断言命令参数转换到共享 `AutomaticOperationRequest`，输出包含资源 ID、状态、审计 ID和刷新范围。
- [ ] **Step 2: 实现 OpenCLI 插件入口。** 只调用共享 `PmsClient`，不从命令层直接拼接后端业务接口。
- [ ] **Step 3: 实现命令解析和结构化输出。** 支持 JSON 默认输出，并提供人类可读摘要；错误码保持非零退出。
- [ ] **Step 4: 编写 Skill。** 说明需求只能有一个执行对象、动态流程规则、项目状态限制、成员自动同步和迭代计划无流程等规则。
- [ ] **Step 5: 运行 OpenCLI 插件测试和安装烟测。** 使用本地打包结果执行 `opencli plugin install`，验证插件可发现能力。
- [ ] **Step 6: 自审并提交。** 确认 Skill 是使用规则，不包含凭据、不替代后端校验；提交 `feat: add pms opencli plugin`。

## Task 7: 闭环集成测试和发布部署

**Files:**

- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/e2e/closed-loop.spec.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/tests/e2e/invalid-relations.spec.ts`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/deploy/Dockerfile`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/deploy/docker-compose.yml`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/.env.example`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/README.md`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/CHANGELOG.md`

- [ ] **Step 1: 写闭环 E2E。** 创建需求，关联项目，依据动态项目节点创建专题，在专题节点创建故事，创建任务，把故事加入迭代计划，再从 MCP 和 OpenCLI 查询完整关系。
- [ ] **Step 2: 写非法关系 E2E。** 验证一个需求不能同时关联多个目标、已完成/已终止/已删除项目不能绑定专题、迭代计划不能创建流程节点。
- [ ] **Step 3: 写并发和幂等 E2E。** 并发更新同一对象时至少一个请求得到版本冲突；重复幂等键只返回同一操作结果。
- [ ] **Step 4: 写多客户端一致性 E2E。** 用 MCP 和 OpenCLI 对同一测试数据执行等价操作，断言最终数据库结果、审计来源和刷新范围一致。
- [ ] **Step 5: 创建 Docker 镜像和健康检查。** MCP Server 只通过环境变量读取 PMS 地址和认证配置，不把 Token 写入镜像。
- [ ] **Step 6: 运行完整验证。** 运行后端 Maven 测试、连接器 `pnpm test`、`pnpm typecheck`、Docker 构建和 MCP Inspector/stdio 烟测。
- [ ] **Step 7: 自审并提交。** 输出测试证据、风险清单、兼容矩阵和回滚说明；提交 `test: verify pms ai connector closed loop`。

## Task 8: 阶段 Review、发布和后续扩展边界

**Files:**

- Create: `/Users/fs/Desktop/Project/pms-ai-connector/docs/security-model.md`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/docs/chatgpt-mcp-setup.md`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/docs/deepseek-harness-setup.md`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/docs/opencli-setup.md`
- Create: `/Users/fs/Desktop/Project/pms-ai-connector/docs/compatibility-matrix.md`
- Modify: `/Users/fs/Desktop/Project/pms-ai-connector/README.md`

- [ ] **Step 1: 做代码和协议全量 Review。** 检查所有写路径最终都进入 PMS Service/Command，连接器没有直接业务 SQL或重复成员同步逻辑。
- [ ] **Step 2: 做安全 Review。** 检查 Token、用户身份、scope、日志脱敏、任意 URL、SSRF、重试和高风险操作。
- [ ] **Step 3: 做业务 Review。** 对照设计方案逐条验证需求单一执行对象、动态流程、专题/故事层级、迭代计划和成员自动同步。
- [ ] **Step 4: 编写安装和部署文档。** 区分本地 OpenCLI/stdio MCP 与 ChatGPT 所需的远程 HTTPS MCP。
- [ ] **Step 5: 创建首个版本。** 以 `0.1.0` 发布连接器，记录支持的 PMS AI API 版本和已知限制。
- [ ] **Step 6: 生成阶段 Review 报告。** 报告测试结果、未覆盖场景、后续 OAuth/OIDC 和浏览器兜底计划；等待用户确认后再继续下一轮开发。

## 完成定义

本计划完成的条件：

- `pms-ai-connector` 是独立可安装项目。
- OpenCLI 和 MCP 都能发现同一份 PMS 能力目录。
- 查询和写入都使用当前用户身份。
- 写操作无需人工确认，但不会跳过后端权限、业务、版本、幂等和审计。
- 需求 → 项目/专题/故事 → 流程节点 → 任务 → 迭代计划的闭环 E2E 通过。
- 流程模板节点和组件变化后，连接器不改代码即可重新发现能力。
- 现有 PMS Web 和旧 DSH 接口回归测试通过。
- README、安装、部署、安全和兼容文档齐全。
