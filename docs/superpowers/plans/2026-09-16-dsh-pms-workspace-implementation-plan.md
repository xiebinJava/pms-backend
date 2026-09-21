# DSH × PMS 工作区集成实施计划

日期：2026-09-16

关联设计：[2026-09-16-dsh-pms-workspace-design.md](../specs/2026-09-16-dsh-pms-workspace-design.md)

状态：进行中（Phase 0/1 只读闭环、查询能力，以及任务、节点和项目管理确认式写操作已完成；其余 PMS 管理能力继续按同一协议补充）

## 1. 实施目标

按设计实现第一版 DSH 主工作区 + PMS 右侧业务工作区，先完成只读闭环，再完成确认式写操作。实施过程中保持现有 `/api/ai/*` 能力兼容，并保证 DSH 成为唯一的 PMS Agent 运行时。

## 2. 实施边界

本计划涉及三个代码库：

- PMS 后端：`pms-backend`
- DSH：`deepseek-harness`
- Work Helper 迁移兼容层：`work-helper`

当前 5174 页面对应的 PMS 前端工程不在上述三个目录中。Phase 0 必须先确认其实际仓库路径和启动方式；在路径确认前，只改 PMS 后端集成契约和 DSH 客户端包，不把前端改动写入错误仓库。

## 3. 不可违反的规则

1. DSH 模型不能直接访问 PMS 数据库。
2. Agent 不能直接执行 PMS 写接口，写操作必须经过 preview、用户确认和 execute。
3. 用户身份、资源权限和操作归属由 PMS 服务端判断，不能信任请求体中的 `userId`。
4. execute 超时只能使用原 `operationId + idempotencyKey` 恢复结果，不能新建操作。
5. Work Helper 只做迁移期兼容，不再新增一套 PMS 工具和上下文规则。
6. 所有新 API 使用 `/api/integration/dsh/v1`，旧 `/api/ai/*` 保持兼容。
7. 每完成一个阶段都必须通过该阶段的验收测试，才进入下一阶段。

## 4. Phase 0：接口、安全和仓库基线

### 4.1 确认工程边界

1. 确认 5174 PMS 前端项目的本地路径、构建命令和反向代理配置。
2. 确认 DSH 当前 Web/Client 包的加载入口和 Cordis 插件注册方式。
3. 确认生产环境 DSH 与 PMS 的域名、TLS、反向代理和 Cookie 归属。
4. 记录当前三个项目的 Git 状态，避免覆盖用户已有修改。

### 4.2 固化 API contract

在 PMS 后端新增集成 API 的公共 DTO 和错误码，建议目录：

```text
src/main/java/com/brad/pms/integration/dsh/api/
├── DshApiEnvelope.java
├── DshErrorCode.java
├── DshCapabilityDTO.java
├── DshContextSnapshot.java
├── DshProjectQuery.java
├── DshTaskQuery.java
├── DshPreviewRequest.java
├── DshExecuteRequest.java
└── DshAgentConfigDTO.java
```

固定以下规则：

- 所有响应包含 `code`、`message`、`requestId`、`data`。
- 项目、节点、任务的状态和优先级同时返回 `code` 与 `label`。
- 查询接口统一分页字段 `page`、`pageSize`、`total`、`items`。
- `userId`、`sessionId`、`agentId` 从认证上下文和服务端请求头解析，不接受客户端覆盖。
- `contextId` 统一定义为 PMS 资源上下文标识，例如 `project:12:node:3`。
- `contextVersion` 由 PMS 生成并在 preview 时校验，不能仅由前端拼接。
- 错误码至少覆盖 `UNAUTHORIZED`、`FORBIDDEN`、`NOT_FOUND`、`CONFLICT`、`PREVIEW_EXPIRED`、`RATE_LIMITED` 和 `UPSTREAM_UNAVAILABLE`。

### 4.3 统一认证策略

在 PMS 中：

1. 扩展 `JwtTokenProvider`，统一 DSH-PMS 短期 Token 的 `aud`、`jti`、`sid`、`scope` 和 `exp`。
2. 抽取 `AiDelegationRoutePolicy`，替代 `AuthInterceptor` 中只匹配 `/ai/*` 的硬编码分支。
3. 让旧 `/api/ai/*` 和新 `/api/integration/dsh/v1/*` 都通过同一套路由策略鉴权。
4. 新增 DSH 服务端到 PMS 的 Token exchange，要求服务凭证和用户会话断言；浏览器不能直接调用该接口。
5. 最小权限 scope 固定为：
   - `pms:project:read`
   - `pms:project:write`
   - `pms:task:read`
   - `pms:command:preview`
   - `pms:command:execute`
   - `pms:workspace:embed`
6. 为本地开发提供明确的环境变量和启动文档，禁止把 `PMS_JWT_SECRET` 写入代码或提交到仓库。

### 4.4 Phase 0 验收

- 新旧 API 都能使用正确 scope 访问。
- 缺少 scope、Token 过期、Token audience 错误时分别返回明确错误。
- 浏览器不能直接完成 token exchange。
- `/api/ai/*` 现有测试全部通过。
- API contract fixture 可以被 PMS、DSH 和 Work Helper 三方复用。

## 5. Phase 1：PMS 只读集成门面

### 5.1 新增集成层

在以下目录实现集成层：

```text
src/main/java/com/brad/pms/integration/dsh/
├── DshCapabilityController.java
├── DshIntegrationController.java
├── DshTokenExchangeController.java
├── DshTokenExchangeService.java
├── DshRequestContext.java
├── DshCorrelation.java
├── DshApiMapper.java
└── DshIntegrationExceptionHandler.java
```

实现原则：

- Controller 只负责 HTTP、鉴权上下文和 DTO 映射。
- 查询复用现有 Project/Task 领域服务，不复制业务查询 SQL。
- 资源权限在领域服务或权限服务中完成，不在 DSH 插件中判断。
- 所有请求生成或透传 `X-Request-Id`，并写入 MDC。

### 5.2 只读接口

按以下顺序实现：

1. `GET /api/integration/dsh/v1/capabilities`
2. `GET /api/integration/dsh/v1/projects`
3. `GET /api/integration/dsh/v1/projects/{projectId}`
4. `GET /api/integration/dsh/v1/projects/{projectId}/tasks`
5. `GET /api/integration/dsh/v1/agents/{agentId}/published-config`

返回结构必须包含：

- 当前用户可见范围。
- 项目编号、名称、状态 code/label、优先级 code/label。
- 当前节点及节点状态 code/label。
- 任务负责人、截止日期、状态和分页信息。
- `contextSnapshot` 和 `contextVersion`。

### 5.3 集成测试

新增 PMS 测试：

- `DshCapabilityControllerTest`
- `DshProjectQueryContractTest`
- `DshTaskQueryPermissionTest`
- `DshTokenExchangeTest`
- `DshIntegrationErrorContractTest`

测试至少覆盖普通用户、项目无权限用户、失效 Token、过期 Token、空结果和分页。

## 6. Phase 1：DSH PMS 插件和只读工具

### 6.1 插件包

在 DSH 中新增：

```text
deepseek-harness/packages/pms/dsh-pms/
├── package.json
├── cordis.patch.yml
├── src/
│   ├── index.ts
│   ├── config.ts
│   ├── types.ts
│   ├── auth/pms-token-exchange.ts
│   ├── client/PmsIntegrationClient.ts
│   ├── workspace/PmsWorkspaceController.ts
│   ├── context/pms-context-store.ts
│   ├── context/pms-session-tags.ts
│   ├── tools/project-list.ts
│   ├── tools/project-detail.ts
│   ├── tools/task-list.ts
│   ├── events/pms-events.ts
│   └── __tests__/
└── README.md
```

实现步骤：

1. 先按 DSH 现有 Cordis 包规范完成 package、依赖和插件生命周期注册。
2. `PmsIntegrationClient` 只调用新 `/api/integration/dsh/v1` 门面。
3. 首次连接完成 capabilities 协商和 Token exchange。
4. 工具只暴露结构化数据，不在工具层拼接自然语言。
5. 工具的 project/node 参数必须与当前 context snapshot 绑定。
6. PMS 不可用时返回明确的结构化错误，不返回模拟数据。

### 6.2 会话上下文

在 DSH 会话中保存：

- 当前项目、节点、页面类型。
- `contextVersion`。
- 当前用户和 Agent 的服务端关联标识。
- 每条消息对应的 context snapshot。
- 项目切换产生的 `pms.context.changed` 事件。

项目切换不新建会话，但新消息必须使用新的上下文快照；旧消息不能自动成为当前项目事实。

### 6.3 流式输出

沿用 DSH/Work Helper 现有 SSE 传输能力，统一事件：

```text
message.start
message.delta
tool.started
tool.result
preview.ready
operation.executed
workspace.refresh
message.done
error
```

每个事件包含 `eventId`、`version`、`requestId`、`sessionId`。客户端按 `eventId` 去重，网络重连通过 `resumeFrom` 恢复，不重复执行工具。

## 7. Phase 1：PMS 真实页面工作区

### 7.1 嵌入策略

在已确认的 PMS 前端仓库中新增嵌入模式路由；如果前端实际与后端同仓，则在对应前端目录实现，不能默认写入 pms-backend。

DSH 客户端新增独立 UI 包：

```text
deepseek-harness/packages/client/ui-pms-workspace/
├── package.json
├── src/index.ts
├── src/PmsWorkspacePanel.tsx
├── src/pms-workspace-events.ts
└── tests/
```

工作区功能：

- 右侧抽屉默认 560px，可拖拽到 360px–720px。
- 收起、恢复、最大化、新页面打开。
- 项目切换时更新 PMS 路由和 DSH context tags。
- 真实 PMS 页面完整壳体（默认收起；打开后保留 PMS 顶栏、左侧导航和配置管理）。
- 收起/关闭只隐藏 iframe，不清除当前会话的 PMS locator；新会话默认不绑定 PMS。
- 加载、断线、无权限和降级状态。
- 只允许配置的 `frame-ancestors` 来源嵌入。

### 7.2 iframe 和消息安全

1. 第一版使用 allowlist 校验后的 PMS URL 直接 iframe；生产环境可切换到 DSH 同源 `/pms-workspace/*` 路由。
2. PMS 不把长期 Token 放入 iframe URL、查询参数或 localStorage。
3. 页面刷新和项目切换通过受控服务端会话恢复身份。
4. DSH 与 iframe 的 `postMessage` 必须校验来源、会话、消息版本和项目上下文。
5. Agent 操作完成后发送 `workspace.refresh`，iframe 只刷新指定资源范围。

### 7.3 浏览器测试

- 抽屉拖拽和最大化。
- 项目 12 / 22 切换后真实页面同步。
- DSH 刷新后会话和当前项目恢复。
- iframe 未登录、Token 过期、403、CSP 拦截时有明确反馈。
- 默认不自动打开 PMS；打开后必须出现完整 PMS 外层导航。

## 8. Phase 2：确认式写操作

### 8.1 PMS 命令实现

当前命令注册表已有 `task.create` 和 `task.assign`；已按同一 preview/execute 协议补充：

1. `CommandName.TASK_UPDATE` / `TaskUpdateCommand`
2. `CommandName.NODE_OWNER_UPDATE` / `NodeOwnerUpdateCommand`
3. `CommandName.NODE_SCHEDULE_UPDATE` / `NodeScheduleUpdateCommand`
4. `CommandName.PROJECT_CREATE` / `CreateProjectCommand`
5. `CommandName.MEMBER_ADD`、`MEMBER_REMOVE`
6. `CommandName.PROJECT_ARCHIVE`、`PROJECT_DELETE`
7. 对应参数校验、项目/节点/成员版本校验和权限校验。

`task.update` 第一版只允许修改明确字段，例如标题、描述、负责人、截止日期和状态；字段和状态流转必须由 PMS 规则校验。

### 8.2 操作记录增强

新增 Flyway migration，例如 `V44__dsh_operation_context.sql`，为 `pms_ai_operation` 增加：

- `agent_id`
- `dsh_session_id`
- `request_id`
- `tool_call_id`
- `target_json`
- `scope_json`
- `context_snapshot_json`

保留现有用户级幂等唯一约束，并补充操作目标和版本校验所需索引。对应更新：

- `AiOperationDO`
- `AiOperationMapper`
- `CommandPreviewService`
- `AiOperationService`
- `OperationExecuteRequest`

### 8.3 确认流程

1. Agent 调用 preview。
2. DSH 生成可读变更卡片。
3. 用户确认或取消。
4. 只有确认事件能触发 execute。
5. PMS 在 execute 时重新校验用户、权限、操作状态、预览 TTL 和资源版本。
6. 成功后返回结果、审计信息和 `refreshScopes`。
7. DSH 刷新对话卡片和 PMS 工作区。

自动化测试必须验证：未确认不落库、重复确认不重复变更、执行超时可恢复、版本冲突不覆盖他人修改。

## 9. Work Helper 迁移

迁移策略：

1. 保留现有 `/api/chat/stream`、会话和配置接口，保证旧入口可回退。
2. 将 PMS Client 增加 `legacy` / `dsh-integration` 后端模式开关。
3. 兼容期默认仍可调用旧 `/api/ai/*`，新 DSH 链路默认调用 `/api/integration/dsh/v1/*`。
4. 不在 Work Helper 中新增新的 PMS command registry。
5. 将现有 SSE 事件转换为统一 DSH 事件；确认事件最终仍调用 PMS execute。
6. 当 DSH 只读和写操作稳定后，再删除重复的 Work Helper PMS 工具实现。

验收要求：切换运行时后，同一用户的会话标签、Agent 配置和 PMS 权限语义保持一致。

## 10. Agent 配置和 Skill/Plugin 同步

1. PMS 配置页继续保存“我是谁”“我怎么干活”、Skill、Plugin 和权限声明。
2. 发布后产生递增的 `publishedVersion`。
3. DSH 启动或发现版本变化时读取已发布配置。
4. DSH 只加载已发布配置，不读取草稿。
5. 未知 Skill/Plugin 或权限升级时，DSH 拒绝加载并记录明确错误。
6. 配置更新不影响已运行请求；新配置从下一次请求生效。

Work Helper 当前已有配置版本、Skill 和 Plugin 校验逻辑；迁移时复用其数据兼容规则，但运行时以 DSH 插件为准。

## 11. 测试和验证命令

### PMS 后端

```text
mvn test
mvn -Dtest='*Dsh*Test,*Ai*Test' test
mvn -DskipTests package
```

重点验证：鉴权、DTO contract、枚举、资源权限、preview/execute、幂等、冲突、审计和旧 `/api/ai/*` 兼容。

### Work Helper

```text
pytest -q
pytest -q tests/test_pms_tools.py tests/test_chat.py tests/test_agent_configuration.py
```

重点验证：SSE、Token header、PMS client 路由、tool confirm、上下文标签和配置发布。

### DSH

```text
pnpm typecheck
pnpm --filter @deepseek-ai/dsh-pms test
pnpm test:gui
pnpm lint
```

如果 DSH 包名还未注册，先完成 workspace package 注册后再执行 filter 测试；不得绕过仓库的 Cordis/catalog 生成脚本手工改生成文件。

### 浏览器验收

使用真实启动的 DSH、PMS 和数据库完成：

1. 登录同一用户。
2. 打开 DSH AI 工作区。
3. 打开右侧 PMS 项目 12。
4. 查询当前项目任务并核对 PMS 页面。
5. 切换到项目 22，确认同一会话保留但上下文快照变化。
6. 创建任务预览，取消一次，确认数据库无变化。
7. 再次预览并确认，验证任务只创建一次。
8. 外部修改项目后执行旧预览，验证返回版本冲突。
9. 刷新页面，验证会话、项目标签和 iframe 登录态恢复。

## 12. 分阶段提交和回滚

建议按以下提交边界实施：

1. `docs`: contract 和安全基线。
2. `pms`: DSH read-only facade。
3. `dsh`: PMS plugin 和只读工具。
4. `dsh-ui`: PMS workspace panel。
5. `work-helper`: 兼容路由和事件适配。
6. `pms`: task.update 与 operation audit migration。
7. `integration`: 端到端测试和部署配置。

每个提交都应可单独回滚。生产启用通过 feature flag 控制：

- `dsh_pms_read_enabled`
- `dsh_pms_workspace_enabled`
- `dsh_pms_write_preview_enabled`
- `dsh_pms_write_execute_enabled`

任何写操作异常时，关闭 write feature flag 不影响只读查询和旧 `/api/ai/*`。

## 13. 最终验收标准

- DSH 能通过真实 PMS 数据完成项目和任务只读问答。
- Agent 不能绕过 preview/execute 直接修改数据。
- Token、权限、项目、节点和会话上下文都由服务端绑定和校验。
- 右侧展示真实 PMS 页面，且不会暴露长期凭证。
- 项目切换不会污染新消息的当前上下文。
- 流式输出支持事件去重、取消和断线恢复。
- task.create、task.assign、task.update、node.owner.update、node.schedule.update、project.create、member.add、member.remove、project.archive、project.delete 均通过统一 preview/execute 生命周期；涉及已有资源的命令具备版本冲突保护，删除和归档保留用户确认理由。
- PMS、DSH 和 Work Helper 的日志可以通过 `requestId` 串联。
- 旧 `/api/ai/*` 和原有 Work Helper 对话链路仍可回退。
