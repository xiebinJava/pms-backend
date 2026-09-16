# DSH × PMS 工作区集成设计

日期：2026-09-16

状态：待评审

范围：第一版 DSH 主工作区 + PMS 可展开业务工作区

## 1. 目标

将 DeepSeek Harness（以下简称 DSH）确定为 AI 主工作区，将 PMS 确定为可展开的业务工作区。用户在 DSH 中通过自然语言提出问题或操作意图，Agent 通过 PMS 工具查询数据、生成变更预览，并在用户确认后执行；用户同时可以在右侧直接查看真实 PMS 页面。

第一版需要完成：

1. DSH 能加载 PMS 插件并识别当前 PMS 项目上下文。
2. Agent 能查询项目、项目详情、任务和节点信息。
3. Agent 能生成创建、分配、更新任务的可审计预览。
4. 写操作必须经过用户确认，确认后由 PMS 执行。
5. PMS 以真实网页形式出现在 DSH 右侧工作区，而不是只做一份静态摘要。
6. 用户切换项目时复用同一个会话，只更新会话标签和 PMS 当前页面。

## 2. 非目标

第一版不包含：

- 让大模型直接连接 PMS 数据库。
- 让大模型直接调用 PMS 写接口。
- 用 DSH 复制一套 PMS 项目管理页面。
- 一次性覆盖全部 PMS 领域对象和所有命令。
- 将 Work Helper 和 DSH 同时作为两个独立的 Agent 运行时。

## 3. 固定架构决策

### 3.1 职责边界

| 系统 | 职责 |
| --- | --- |
| DSH | Agent 运行时、自然语言对话、工具路由、流式输出、会话和工作区编排 |
| PMS | 业务数据、资源权限、项目页面、命令预览、命令执行、审计和幂等 |
| PMS 插件 | 将 PMS 的能力注册为 DSH 可调用的工具，并负责 PMS 工作区 UI |
| Work Helper | 迁移期兼容层；不再新增第二套 PMS Agent 逻辑 |

所有真正改变 PMS 数据的动作最终都必须回到 PMS 的 preview/execute 链路，由 PMS 再次校验权限、版本和业务规则。

### 3.2 工作区形态

DSH 为主界面，右侧 PMS 为可展开业务工作区：

- 默认宽度：560px。
- 可拖拽范围：360px–720px。
- 支持收起、恢复、最大化和在新页面打开。
- 第一阶段优先嵌入真实 PMS 页面；摘要视图仅作为加载失败或快速概览时的降级方案。
- 生产环境通过同源代理或受控嵌入地址访问 PMS，避免在浏览器中暴露长期凭据。

## 4. 代码归属和目录结构

### 4.1 DSH 插件

```text
deepseek-harness/packages/pms/dsh-pms/
├── package.json
├── cordis.patch.yml
├── src/
│   ├── index.ts
│   ├── config.ts
│   ├── types.ts
│   ├── auth/
│   │   └── pms-token-exchange.ts
│   ├── client/
│   │   ├── index.ts
│   │   └── PmsWorkspaceController.ts
│   ├── context/
│   │   ├── pms-context-store.ts
│   │   └── pms-session-tags.ts
│   ├── tools/
│   │   ├── project-list.ts
│   │   ├── project-detail.ts
│   │   ├── task-list.ts
│   │   ├── task-create.ts
│   │   ├── task-assign.ts
│   │   └── task-update.ts
│   ├── events/
│   │   └── refresh-scopes.ts
│   └── __tests__/
└── README.md
```

### 4.2 PMS 集成层

```text
pms-backend/src/main/java/com/brad/pms/integration/dsh/
├── DshCapabilityController.java
├── DshIntegrationController.java
├── DshTokenExchangeController.java
├── DshTokenExchangeService.java
├── DshRequestContext.java
├── DshCorrelation.java
└── DshIntegrationExceptionHandler.java
```

现有 `/ai/*` 接口保留兼容；新增 `/integration/dsh/v1/*` 作为稳定的 DSH 集成门面，避免 DSH 依赖 PMS 内部控制器结构。

## 5. 工具边界

### 5.1 第一版对 Agent 暴露的工具

只暴露与当前范围直接相关的工具：

- `pms.project.list`
- `pms.project.get`
- `pms.task.list`
- `pms.task.create`
- `pms.task.assign`
- `pms.task.update`

工具必须返回结构化结果，不能只返回拼接好的自然语言。模型负责解释结果，前端负责渲染预览和状态。

### 5.2 查询工具

查询工具包含：

- `userId`
- `sessionId`
- 当前 PMS 上下文标签
- 过滤条件
- 分页参数
- `requestId`

工具只能查询当前用户有权限看到的资源。没有数据时返回空集合和明确的查询范围，不允许用模型常识补全。

### 5.3 写工具

写工具不能直接执行数据库变更，统一返回 `operationPreview`：

```json
{
  "operationId": "op_20260916_001",
  "commandName": "task.create",
  "status": "awaiting_confirmation",
  "target": {
    "projectId": "12",
    "nodeId": "3"
  },
  "changes": [
    {
      "field": "title",
      "before": null,
      "after": "补充技术评审材料"
    }
  ],
  "expiresAt": "2026-09-16T10:35:00+08:00",
  "version": "project-12-v18"
}
```

模型不能自行拼接 execute 请求。只有用户点击确认后，DSH UI 才能调用 execute。

## 6. 写操作生命周期

```text
用户意图
  ↓
Agent 选择工具
  ↓
PMS 校验权限、参数和资源版本
  ↓
生成 operation preview
  ↓
DSH 展示变更明细和风险
  ↓
用户确认 / 取消
  ↓
PMS execute
  ↓
返回结果、审计信息和 refreshScopes
  ↓
刷新 DSH 对话卡片与右侧 PMS 页面
```

执行请求必须携带：`operationId`、`idempotencyKey`、`expectedVersion`、`userId`、`sessionId` 和 `requestId`。

同一个幂等键重复执行只能产生一次业务变更。版本冲突时不得静默覆盖，必须让 PMS 返回冲突信息，并要求重新读取和生成预览。

## 7. 会话、上下文和项目切换

### 7.1 上下文采用指针

不把整页 DOM 或整页文本永久塞进每轮提示词，而是把当前工作区定位信息传给插件和工具：

```json
{
  "source": "pms",
  "pageType": "project-detail",
  "projectId": "12",
  "nodeId": "3",
  "contextVersion": "project-12-v18",
  "route": "/projects/12",
  "userId": "10001",
  "updatedAt": "2026-09-16T10:30:00+08:00"
}
```

用户问“今天有几个任务需要完成”时，Agent 通过 `pms.task.list` 查询；只有查询结果进入回答上下文，避免初始上下文过大和数据过期。

### 7.2 会话标签

切换项目不新建会话，只更新标签：

- `pms:project:12`
- `pms:node:3`
- `pms:page:project-detail`

会话由 DSH 用户维度保存；项目、节点和页面是会话标签，不是会话主键。这样用户刷新页面或切换项目后仍能恢复同一会话。

### 7.3 上下文变化事件

```json
{
  "type": "pms.context.changed",
  "version": 1,
  "requestId": "req_123",
  "sessionId": "dsh_session_123",
  "payload": {
    "projectId": "12",
    "nodeId": "3",
    "pageType": "project-detail",
    "contextVersion": "project-12-v18"
  }
}
```

## 8. 真实 PMS 页面工作区

### 8.1 第一阶段

PMS 工作区内部默认加载真实项目页面，例如：

```text
/pms-workspace/projects/12
```

开发环境可以暂时映射到 `http://127.0.0.1:5174/projects/12`；生产环境不直接写死本地端口，而是由 DSH 服务端配置受控的 PMS base URL，并通过同源代理转发。

右侧工具栏必须提供：

- 当前项目切换。
- 业务摘要 / PMS 页面切换。
- 刷新当前 PMS 页面。
- 在新页面打开。
- 连接状态和最后同步时间。

### 8.2 事件同步

PMS 页面或 Agent 操作完成后，通过 `refreshScopes` 通知 DSH：

- `project`
- `project-detail`
- `tasks`
- `dashboard`
- `node`

DSH 不猜测数据是否变化，只根据范围刷新对应查询或发送刷新事件给嵌入页面。

### 8.3 长期演进

高频场景稳定后，再把 PMS 的项目概览、当前节点和今日任务做成 DSH 原生 Workspace Panel。原生面板和真实 PMS 页面共用同一套 PMS API、权限和事件，不复制业务规则。

## 9. PMS 集成 API

建议提供以下稳定门面：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/integration/dsh/v1/capabilities` | 查询插件能力和版本 |
| POST | `/integration/dsh/v1/token/exchange` | DSH 用户换取短期 PMS token |
| GET | `/integration/dsh/v1/projects` | 查询项目列表 |
| GET | `/integration/dsh/v1/projects/{projectId}` | 查询项目详情 |
| GET | `/integration/dsh/v1/projects/{projectId}/tasks` | 查询项目任务 |
| POST | `/integration/dsh/v1/commands/preview` | 生成写操作预览 |
| POST | `/integration/dsh/v1/operations/{operationId}/execute` | 确认后执行操作 |

已有 `/ai/context/inspect`、`/ai/query/tasks`、`/ai/commands`、`/ai/commands/preview` 和 `/ai/operations/{operationId}/execute` 保持兼容，并逐步由门面复用其领域服务。

## 10. 认证和权限

### 10.1 Token exchange

DSH 先识别当前用户，再向 PMS 换取短期、用户范围的 token。浏览器不保存长期 PMS 管理员 token。

token 至少包含：

- `sub`
- `aud: dsh-pms`
- `session_id`
- `scope`
- `exp`
- `jti`

PMS 仍然做资源级权限校验；Agent 的 `readonly` 或 `preview` 能力不能绕过 PMS 权限。

### 10.2 错误处理

| 情况 | 处理 |
| --- | --- |
| PMS 不可用 | 显示离线状态，明确告知无法读取或执行，不猜测 |
| token 过期 | 自动刷新一次，失败后要求重新连接 |
| 401 | 重新建立连接 |
| 403 | 不重试，说明权限不足 |
| 404 | 说明资源不存在或已被删除 |
| 409 | 刷新数据并重新生成预览 |
| 预览过期 | 重新预览，禁止执行旧预览 |
| 429 / 5xx | 有限重试，仍失败则返回可操作错误 |

## 11. Agent 配置

PMS 中配置的是 Agent 的声明和能力，不承担运行时执行：

- “我是谁”：Agent 身份、职责和边界。
- “我怎么干活”：查询优先、工具选择、确认策略、错误处理和输出格式。
- Skill：注入工作方法和领域规则。
- Plugin：声明可调用的 PMS 工具集合。
- 权限：限制可用的工具和数据范围。

真正运行 Skill 和 Plugin 的进程在 DSH / Work Helper 侧。PMS 保存配置版本，DSH 在启动或配置变更后同步发布版本。

## 12. 审计和可观测性

每次工具调用和执行至少记录：

- `userId`
- `dshSessionId`
- `agentId`
- `toolCallId`
- `requestId`
- `projectId`
- `nodeId`
- `commandName`
- `operationId`
- `idempotencyKey`
- `resultStatus`
- `latencyMs`

日志中禁止记录长期 token 和完整敏感请求体。所有跨 DSH/PMS 的请求必须可通过 `requestId` 关联。

## 13. 分阶段实施

### Phase 1：只读闭环

- DSH PMS 插件骨架和能力发现。
- token exchange。
- 项目列表、项目详情、任务查询。
- 真实 PMS 页面右侧工作区。
- 项目切换、会话标签和上下文事件。
- Agent 流式输出和连接状态。

验收：在 DSH 询问“当前项目今天有哪些任务”，结果与 PMS 页面一致；切换项目后会话不丢失，查询范围随标签变化。

### Phase 2：确认式写操作

- `task.create`、`task.assign`、`task.update`。
- preview 卡片、确认/取消、幂等、版本冲突处理。
- 操作完成后的 PMS 页面和 DSH 卡片刷新。

验收：用户不确认时数据库不变；确认一次只变更一次；冲突时不会覆盖他人修改。

### Phase 3：原生业务面板

- 项目概览、当前节点、今日任务原生化。
- 高频跳转保留“在 PMS 中打开”。
- 统一 DSH 和 PMS 的刷新事件。

### Phase 4：能力扩展

- 项目创建、节点分配、节点更新。
- 更多 Skill 和 Plugin。
- Work Helper 兼容层收敛并关闭重复运行时。

## 14. 测试策略

### 14.1 PMS 后端

- 集成 API 鉴权测试。
- 查询范围和资源权限测试。
- preview/execute 的幂等和版本冲突测试。
- token 过期、403、404、409、429、5xx 测试。
- 审计字段完整性测试。

### 14.2 DSH 插件

- 工具 schema 和参数校验测试。
- 上下文标签切换测试。
- token refresh 测试。
- preview 卡片确认 / 取消测试。
- refreshScopes 事件测试。
- Agent 流式输出和断线恢复测试。

### 14.3 浏览器端

- 打开、关闭、拖拽、最大化右侧 PMS 工作区。
- 项目切换后 iframe / 原生摘要同步。
- PMS 页面加载失败时的降级提示。
- 写操作完成后当前页面刷新。
- 刷新 DSH 页面后会话按用户恢复。

## 15. 完成标准

第一版只有满足以下条件才能进入下一阶段：

1. DSH 能在真实 PMS 数据上完成一次完整只读问答。
2. Agent 无法绕过 preview/execute 直接修改数据。
3. 用户、项目、节点和页面标签可在刷新后恢复。
4. 右侧展示真实 PMS 页面，且项目切换可同步。
5. 连接失败、权限不足和数据冲突都有明确反馈。
6. 日志能够从 DSH 会话追踪到 PMS 请求和业务结果。
7. 现有 `/ai/*` 调用不被破坏。

## 16. 静态 Demo 与正式实现的关系

当前静态 Demo 用于验证产品交互：DSH 主界面、右侧可拖拽 PMS 工作区、项目切换、摘要 / 真实 PMS 页面切换。它不承担认证、Agent 调度或真实写操作。

正式实现应复用 Demo 已验证的交互，不直接复用 Demo 的假数据和浏览器端跨域配置。下一步应先按 Phase 1 建立 DSH 插件和 PMS 集成门面，再把静态 Demo 的 iframe 地址替换为受控的生产工作区路由。
