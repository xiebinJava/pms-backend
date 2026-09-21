# PMS–DSH Agent 能力平台实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持 DSH 工具面稳定的前提下，把 PMS 的查询、写入、批量操作和复杂业务流程统一升级为可发现、可预览、可执行、可审计的 Agent 能力平台，并为 OpenCLI 跨系统兜底和后续多 Agent 协同留出边界。

**Architecture:** DSH 只暴露 `pms_capabilities`、`pms_query`、`pms_command_preview`、`pms_command_execute`、`pms_workflow_execute` 等少量稳定工具；PMS 通过能力目录、命令注册表和工作流执行器承载具体业务操作。API/业务命令是 PMS 的主路径，OpenCLI 只负责没有 API 的长尾页面操作；所有写操作都遵守预览、确认、幂等执行、回读校验、统一刷新和审计流程。

**Tech Stack:** PMS：Spring Boot 3、Java 17、MyBatis-Plus、JUnit 5、现有 Command/Service/API；DSH：TypeScript、Cordis、Vitest、现有 `dsh-pms` 插件和 Agent preset；PMS 前端：Vue 3、TypeScript、Ant Design Vue、现有 DSH refresh bridge；兜底：OpenCLI Adapter；长流程：先使用 PMS 自有状态机，稳定后再评估 LangGraph.js。

**Spec:** `/Users/fs/Desktop/Project/pms-backend/docs/product-specs/pms-dsh-agent-platform.md`

## Global Constraints

- 不为每个 PMS 页面按钮新增一个 DSH 工具；具体业务能力进入 PMS 能力目录。
- PMS API、Service、Command 和 Workflow 是主执行路径；浏览器自动化不得绕过 PMS 权限。
- DSH Agent 只能使用当前 SSO 用户的 PMS 权限范围；Agent 配置不能扩大用户权限。
- 所有写操作必须先预览，等待用户在后续消息中明确确认，再执行精确的 `operationId`。
- 批量操作只生成一次预览、一次确认、一次批量执行和一次统一页面刷新。
- 结果未知时必须先查询确认，禁止直接重复执行可能产生重复数据的创建操作。
- 查询回答必须标记本次数据范围，禁止用历史消息或缓存冒充当前 PMS 结果。
- 能力目录、命令和工作流必须有中文展示字段；内部标识使用稳定的英文命名。
- 每个阶段完成后必须运行该阶段测试、检查权限和边界条件、审阅 diff，再进入下一阶段。
- 本计划不重新引入已移除的 Work Helper；范围只包含 PMS 后端、PMS 前端和 DSH。

---

## 1. 文件与模块边界

### PMS 后端

- 能力和命令核心：
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommand.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommandRegistry.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommandMetadata.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommandDescriptor.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/CommandName.java`
- 查询和上下文：
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshQueryService.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshQueryRequest.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshQueryResult.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/context/`
- DSH 集成接口：
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshCapabilityController.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshQueryController.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshCommandController.java`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/dsh/api/`
  - `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/dsh/security/DshAgentScopePolicy.java`
- 现有业务命令：`command/project`、`command/task`、`command/node`、`command/member`。

### DSH

- PMS 客户端：
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/types.ts`
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/remote.ts`
- PMS 工具：
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/query.ts`
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/command.ts`
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/project-list.ts`
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/project-detail.ts`
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/task-list.ts`
- PMS Agent 组合和提示词：
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/index.ts`
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/preset/agent-presets/`
- 会话和页面刷新：
  - `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/context/`
  - `/Users/fs/Desktop/Project/pms-front/src/integration/dsh-refresh-bridge.ts`

### PMS 前端

- 页面和业务组件：
  - `/Users/fs/Desktop/Project/pms-front/src/views/project/list/index.vue`
  - `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`
  - `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/TaskKanban.vue`
  - `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/Members.vue`
  - `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/WorkflowCustomFields.vue`
- 共享类型和 API：
  - `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
  - `/Users/fs/Desktop/Project/pms-front/src/api/project.ts`

### OpenCLI 适配层

第一阶段不把 OpenCLI 嵌入 PMS 核心。定义一个 DSH 侧 Adapter 接口，后续可在 DSH 独立包中实现：

```ts
type PmsUiFallbackAdapter = {
  capability: string;
  risk: "low" | "medium" | "high";
  execute(input: unknown, context: BrowserContext): Promise<UiExecutionResult>;
  verify(result: unknown, context: BrowserContext): Promise<VerificationResult>;
};
```

它只能被能力目录明确标记为 `executor: "browser"` 的能力调用。

---

## Stage 0：冻结协议和验收基线

**目标：** 在修改业务代码前固定概念、命名、兼容策略和端到端验收场景。

**Files:**

- Read: `/Users/fs/Desktop/Project/pms-backend/docs/product-specs/pms-dsh-agent-platform.md`
- Modify: `/Users/fs/Desktop/Project/pms-backend/README.md`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/AgentCapabilityContractTest.java`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/tests/agent-capability-contract.spec.ts`

**Interfaces:**

- 能力名称统一采用 `<domain>.<action>`，例如 `task.create`、`node.complete`。
- 能力返回包含 `name`、`version`、`label`、`risk`、`requiredScopes`、`inputSchema`、`executor`、`refreshScopes`。
- 旧的 `pms_project_list`、`pms_project_get`、`pms_task_list` 继续可用，直到统一查询协议完成迁移。
- `pms_command_preview` 和 `pms_command_execute` 的现有预览确认安全规则不改变。

### Tasks

- [ ] 写后端合同测试，断言能力描述使用稳定英文名称和中文展示字段。
- [ ] 写 DSH 合同测试，断言旧工具和新能力目录可以同时发现。
- [ ] 写批量操作验收用例：创建两个任务只产生一个刷新事件。
- [ ] 写权限验收用例：用户权限不足时能力被过滤，不能通过 Agent 配置绕过。
- [ ] 运行 `mvn -q -Dtest=AgentCapabilityContractTest test`，确认基线测试先失败或覆盖现有行为。
- [ ] 运行 `cd /Users/fs/Desktop/Project/deepseek-harness && node_modules/.bin/vitest run packages/pms/dsh-pms/tests/agent-capability-contract.spec.ts`。

### Review checkpoint

- [ ] 对照产品方案确认查询、写入、批量、工作流、浏览器兜底和权限边界全部有验收用例。
- [ ] 确认没有把 OpenCLI、浏览器 Agent 或任意 HTTP 请求定义为 PMS 主执行协议。
- [ ] 确认旧工具兼容策略不会导致同一写操作执行两次。

---

## Stage 1：把 PmsCommandRegistry 升级为能力目录

**目标：** 让 PMS 后端为每个业务能力提供统一的机器描述，DSH 能动态发现字段、权限、风险和刷新范围。

**Files:**

- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommandMetadata.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommandDescriptor.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommandRegistry.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/CommandName.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshCapabilityController.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/dsh/api/DshCapabilityDTO.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/dsh/api/DshCommandCapabilityDTO.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/dsh/api/DshFieldSchemaDTO.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/integration/dsh/api/DshCapabilityRisk.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/command/PmsCommandRegistryContractTest.java`

**Interfaces:**

```java
public record DshCapabilityDTO(
    String name,
    int version,
    String label,
    String description,
    String domain,
    String executor,
    String risk,
    List<String> requiredScopes,
    Map<String, DshFieldSchemaDTO> inputSchema,
    List<String> preconditions,
    boolean supportsPreview,
    boolean supportsBatch,
    boolean idempotent,
    List<String> refreshScopes
) {}
```

### Tasks

- [ ] 为 `PmsCommandMetadata` 增加领域、动作、中文标签、风险、Schema、批量、幂等和刷新字段。
- [ ] 为字段 Schema 增加 `type`、`label`、`required`、`enumValues`、`referenceType`、`format` 和 `description`。
- [ ] 让 `PmsCommandRegistry` 返回按当前用户过滤后的能力，而不是直接暴露全部 `CommandName`。
- [ ] 在 `DshCapabilityController` 中返回稳定排序的能力目录，并保留旧字段兼容。
- [ ] 为现有命令补齐能力元数据：项目、任务、节点、成员、归档和删除。
- [ ] 写测试覆盖字段缺失、风险级别、权限过滤、未知命令和版本字段。
- [ ] 运行 `mvn -q -Dtest=PmsCommandRegistryContractTest test`。

### Review checkpoint

- [ ] 能力目录只描述“可以做什么”，不能单独授予权限。
- [ ] 删除、归档、节点完成和权限变更被标记为高风险。
- [ ] 当前用户权限不足时不会通过能力目录泄露不可用写能力。
- [ ] 现有 DSH 旧工具调用仍然通过原有权限和预览规则。

---

## Stage 2：统一 PMS 查询协议和引用解析

**目标：** 让 Agent 使用一个查询入口获取项目、节点、任务、人员、组织、成员、表单和审批信息。

**Files:**

- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshQueryRequest.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshQueryResult.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshQueryService.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshQueryController.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshResourceType.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshReferenceResolver.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/query/DshQueryScope.java`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/query.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/types.ts`
- Test: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/query/DshQueryServiceTest.java`
- Test: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/tests/dsh-pms.spec.ts`

**Interfaces:**

```java
public record DshQueryRequest(
    DshResourceType resource,
    Map<String, Object> filters,
    List<String> fields,
    int page,
    int pageSize,
    String sort
) {}
```

```ts
type PmsQueryResult = {
  authoritative: boolean;
  dataScope: "current-page" | "filtered-result" | "full-result";
  resource: string;
  total: number;
  page: number;
  pageSize: number;
  fields: Array<{ name: string; label: string }>;
  items: unknown[];
  capturedAt: string;
};
```

### Tasks

- [ ] 扩展资源枚举：`projects`、`nodes`、`tasks`、`users`、`organizations`、`members`、`forms`、`approvals`。
- [ ] 统一分页、排序、字段选择和数据范围返回。
- [ ] 将人员、项目、节点和组织引用解析为稳定 ID，不允许模型直接猜 ID。
- [ ] 支持 `current_user`、`current_project`、`current_node` 这类受控引用。
- [ ] 将日期表达式解析成明确业务日期，并在预览中展示实际日期。
- [ ] 扩展 DSH `pms_query` Schema 和中文字段渲染。
- [ ] 增加查询失败、权限裁剪、空结果和分页边界测试。
- [ ] 运行后端查询测试和 DSH PMS 测试。

### Review checkpoint

- [ ] “PMS 里有多少人”可以得到人员资源的真实查询结果，而不是从项目中推断。
- [ ] 查询只返回当前用户权限范围内的数据。
- [ ] Agent 不会把第一页当作全部结果。
- [ ] 查询失败时提示“本次未读取到”，不引用历史快照。

---

## Stage 3：批量命令、幂等执行和统一刷新

**目标：** 解决创建多个任务只刷新一次、系统繁忙重复执行、部分成功和结果未知等问题。

**Files:**

- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/CommandPreviewRequest.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/CommandPreview.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/CommandPreviewService.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/CommandExecutionService.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/CommandResult.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/BatchCommandResult.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/RefreshScope.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshCommandController.java`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/command.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/context/pms-context-store.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/integration/dsh-refresh-bridge.ts`
- Test: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/command/CommandExecutionServiceTest.java`
- Test: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/tests/dsh-pms.spec.ts`
- Test: `/Users/fs/Desktop/Project/pms-front/src/integration/dsh-refresh-bridge.test.mjs`

**Interfaces:**

```java
public record CommandPreviewRequest(
    List<CommandRequest> commands,
    String clientRequestId
) {}
```

```java
public record BatchCommandResult(
    String operationId,
    String status,
    List<CommandResult> results,
    Set<RefreshScope> refreshScopes,
    String auditId
) {}
```

### Tasks

- [ ] 让预览请求同时接受单命令和命令列表，并保持旧单命令 JSON 可解析。
- [ ] 为每个命令生成稳定的幂等键，创建类命令必须带幂等约束。
- [ ] 将批量命令作为一个 operation 保存，执行时记录每条子命令状态。
- [ ] 对“系统繁忙”建立结果未知状态，执行重试前先用幂等键查询操作状态。
- [ ] 收集所有子命令的 `refreshScopes`，执行完成后合并去重。
- [ ] 让 DSH 在批量命令全部返回后只发送一个刷新事件。
- [ ] 前端刷新桥接按 scope 刷新项目列表、详情、看板，不重复刷新。
- [ ] 测试两个任务批量创建、一个成功一个失败、重复 execute、执行超时和结果未知。
- [ ] 运行后端命令测试、DSH 工具测试和前端刷新桥测试。

### Review checkpoint

- [ ] 同一 `operationId` 和 `idempotencyKey` 重试不会重复创建。
- [ ] 批量执行部分成功时可以准确报告每条命令状态。
- [ ] 页面只刷新一次，刷新发生在所有写操作完成后。
- [ ] 用户没有确认时，任何子命令都不会执行。

---

## Stage 4：PMS 业务工作流

**目标：** 把“完成节点”“项目初始化”等跨接口业务封装为确定性流程，避免让模型自行编排底层接口。

**Files:**

- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/workflow/PmsWorkflow.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/workflow/PmsWorkflowRegistry.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/workflow/PmsWorkflowContext.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/workflow/PmsWorkflowResult.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/workflow/node/CompleteNodeWorkflow.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/workflow/project/InitializeProjectWorkflow.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshCommandController.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshWorkflowController.java`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/command.ts`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/workflow.ts`
- Test: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/workflow/CompleteNodeWorkflowTest.java`
- Test: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/ai/workflow/InitializeProjectWorkflowTest.java`

**Interfaces:**

```java
public interface PmsWorkflow {
    String name();
    WorkflowDescriptor descriptor();
    WorkflowPreview preview(PmsWorkflowContext context, Map<String, Object> arguments);
    PmsWorkflowResult execute(String operationId, PmsWorkflowContext context);
}
```

### Tasks

- [ ] 定义工作流状态：`PLANNED`、`WAITING_CONFIRMATION`、`RUNNING`、`SUCCEEDED`、`PARTIAL_FAILURE`、`BLOCKED`、`FAILED`。
- [ ] 定义工作流前置条件返回结构，包含中文阻断原因、对象链接和补救建议。
- [ ] 实现 `node.complete` 的查询、校验、预览和执行闭环。
- [ ] 实现 `project.initialize` 的节点初始化、成员初始化和模板校验。
- [ ] 让工作流复用命令执行器，而不是直接绕过命令审计。
- [ ] 将工作流能力暴露给 `pms_capabilities` 和 `pms_workflow_execute`。
- [ ] 增加工作流超时、人工介入和恢复测试。
- [ ] 运行工作流测试和现有节点/项目服务测试。

### Review checkpoint

- [ ] “完成当前节点”会真实检查任务、表单、审批和权限。
- [ ] 前置条件不满足时不会强制写入。
- [ ] 工作流每一步都有状态和审计记录。
- [ ] 工作流失败后不会无限重试，也不会丢失已完成步骤。

---

## Stage 5：DSH PMS Agent 和能力发现接入

**目标：** 让 PMS Agent 根据能力目录工作，同时保持现有 Agent 选择、会话锁定和预览确认行为。

**Files:**

- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/index.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/query.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/command.ts`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/capabilities.ts`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/tools/workflow.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/types.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/preset/agent-presets/`
- Test: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/tests/dsh-pms.spec.ts`
- Test: `/Users/fs/Desktop/Project/deepseek-harness/packages/preset/agent-presets/tests/`

**Interfaces:**

- PMS Agent 首轮可以调用 `pms_capabilities` 发现当前需要的能力，不把全部能力描述一次性塞进上下文。
- PMS Agent 读取事实必须调用 `pms_query`，页面上下文只作为定位信息。
- PMS Agent 写入必须调用 preview，再等待下一条用户确认。
- PMS Agent 使用中文字段名、中文错误和中文预览。

### Tasks

- [ ] 增加 `pms_capabilities` 客户端和工具 Schema。
- [ ] 增加 `pms_workflow_execute` 客户端和工具 Schema。
- [ ] 将提示词改为“能力发现 → 查询引用 → 生成预览 → 明确确认 → 执行 → 回读”的固定规则。
- [ ] 保留旧项目/任务工具作为兼容别名，并逐步让新 Agent 优先使用统一协议。
- [ ] 增加 Agent 选择后只加载 PMS 能力的测试。
- [ ] 增加 generic Agent 无法发现或调用 PMS 工具的测试。
- [ ] 增加授权失败、能力过滤和中文字段展示测试。
- [ ] 运行 `cd /Users/fs/Desktop/Project/deepseek-harness && node_modules/.bin/vitest run packages/pms/dsh-pms/tests packages/preset/agent-presets/tests`。

### Review checkpoint

- [ ] PMS Agent 没有能力目录时能给出可解释错误，不会猜测能力名称。
- [ ] Agent 选择不会改变 PMS 服务端权限。
- [ ] 同一会话重连后仍使用相同 Agent、Skill 和工具组合。
- [ ] 旧会话和旧 PMS 工具仍可读取。

---

## Stage 6：OpenCLI UI 兜底 Adapter

**目标：** 让没有 API 的少量功能可以被 DSH 通过页面执行，但不污染 PMS API 主路径。

**Files:**

- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/integrations/opencli-pms/README.md`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/integrations/opencli-pms/src/adapter.ts`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/integrations/opencli-pms/src/manifest.ts`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/integrations/opencli-pms/src/verification.ts`
- Create: `/Users/fs/Desktop/Project/deepseek-harness/packages/integrations/opencli-pms/tests/adapter.spec.ts`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/ai/command/PmsCommandMetadata.java`

**Interfaces:**

```ts
type UiFallbackManifest = {
  capability: string;
  site: "pms" | string;
  route: string;
  executor: "opencli";
  risk: "low" | "medium" | "high";
  requiresConfirmation: boolean;
  successSignals: string[];
  refreshScopes: string[];
};
```

### Tasks

- [ ] 定义 Adapter manifest，明确能力、页面、风险、确认和成功信号。
- [ ] 只接入一个没有 API 的低风险 PMS 页面作为验证样例。
- [ ] 复用用户当前浏览器登录态，不持久化 Cookie、密码或 Token。
- [ ] 在页面操作前验证当前 URL、登录状态和页面版本。
- [ ] 在操作后通过页面数据或 API 回读验证结果。
- [ ] 保存页面地址、动作摘要、截图哈希和结果，不保存敏感凭证。
- [ ] 登录失效、元素缺失或页面变化时停止并返回人工处理，而不是无限重试。
- [ ] 运行 Adapter 单测和一次浏览器集成验证。

### Review checkpoint

- [ ] PMS 已有 API 的能力不会误走浏览器兜底。
- [ ] 高风险操作没有默认启用浏览器自动执行。
- [ ] OpenCLI 失败不会触发重复创建。
- [ ] 适配器结果仍然触发统一刷新和回读。

---

## Stage 7：审计、观测和端到端验证

**目标：** 让每一次自然语言操作都能被定位、复盘和验证。

**Files:**

- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/entity/OperationLogDO.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/service/OperationLogService.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshCommandController.java`
- Modify: `/Users/fs/Desktop/Project/pms-backend/src/main/java/com/brad/pms/controller/DshQueryController.java`
- Create: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/integration/dsh/DshAgentEndToEndContractTest.java`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/tests/pms-auth-client.spec.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/integration/dsh-refresh-bridge.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-backend/README.md`
- Modify: `/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/README.md`

### Tasks

- [ ] 审计记录增加用户、会话、Agent、Agent 版本、能力、operationId、幂等键、风险等级和结果。
- [ ] 不在日志中记录密码、Cookie、服务 Key、OIDC Token 或完整敏感表单值。
- [ ] 增加端到端测试：查询今日任务、创建两个任务、重新查询、统一刷新。
- [ ] 增加端到端测试：创建项目预览、确认、执行、回读项目详情。
- [ ] 增加端到端测试：权限不足、授权过期、系统繁忙、版本冲突、部分成功。
- [ ] 增加节点完成测试：阻断条件返回、确认执行、状态回读。
- [ ] 运行 PMS 后端相关 Maven 测试、DSH Vitest 测试和 PMS 前端 Node 测试。
- [ ] 运行 PMS 后端编译、DSH 包构建和 PMS 前端构建。
- [ ] 记录无法在本地运行的环境依赖，不把环境问题误判成业务通过。

### Final review checkpoint

- [ ] 查询、命令、工作流和浏览器兜底的边界清晰。
- [ ] 所有写入操作均可从审计记录还原。
- [ ] 批量操作只有一次统一刷新。
- [ ] Agent 失败后会停止、说明原因或请求人工决策。
- [ ] 没有引入 Work Helper。
- [ ] 多 Agent、Agent 市场、LangGraph 长流程仍然没有提前侵入第一版核心执行链路。

## Stage 8：后续多 Agent 和 Agent 市场

该部分不进入本轮 PMS 能力平台第一版，只在能力协议稳定后实施。

后续新增：

- Agent manifest：角色、提示词、Skill、工具、版本和适用团队。
- Planner、Manager、Worker 的 Agent handoff。
- PMS 项目团队绑定多个 Agent。
- Agent 执行记录和交接记录。
- Agent 市场筛选和安装。
- 人工决策节点。

这些 Agent 仍然只能调用统一 PMS 能力目录，不允许每个 Agent 自己直连数据库或维护一套项目状态。

## Self-Review 结果

- **覆盖完整性：** 查询、简单写入、批量写入、复杂工作流、页面兜底、权限、审计、刷新和失败恢复分别由 Stage 2–7 覆盖。
- **工具数量控制：** DSH 工具面固定，PMS 能力数量可增长。
- **权限闭环：** Agent 声明、DSH 挂载权限和 PMS 当前用户权限取交集，PMS 服务端最终裁决。
- **执行闭环：** 预览、用户确认、幂等执行、结果回读、统一刷新和审计均有明确阶段。
- **风险控制：** OpenCLI 不作为 PMS 高风险写入主路径；LangGraph 和多 Agent 延后，避免第一阶段过度设计。
- **兼容性：** 现有 PMS 工具、Agent 选择、会话锁定和授权链路保留兼容路径。
- **未发现的前置问题：** 开始 Stage 1 前，需要确认当前数据库迁移机制和 DSH 服务端是否已经具备批量 operation 持久化能力；该确认属于实现阶段的基线检查，不改变本方案。
