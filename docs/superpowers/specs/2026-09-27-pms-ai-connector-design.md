# PMS AI Connector 设计方案

日期：2026-09-27

状态：方案已确认，待阶段 0 实施

## 1. 已确认的产品决策

本方案根据以下决策制定：

1. 新建独立项目 `pms-ai-connector`。
2. 同时支持 OpenCLI 和 MCP。
3. 支持读操作和写操作；连接器默认自动执行写操作，不增加人工确认步骤。
4. PMS 后端继续作为唯一业务真源。
5. 能力、流程模板、流程节点和节点组件全部动态发现，不在连接器中硬编码业务节点。

这里的 OpenCLI 指 GitHub 上的 [jackwener/OpenCLI](https://github.com/jackwener/OpenCLI)。

“不需要人工确认”不等于“跳过安全校验”。每次写操作仍然必须由 PMS 后端校验当前用户身份、权限、业务规则、对象版本和幂等键，并留下审计记录。

## 2. 目标

用户可以在 ChatGPT、DeepSeek Harness、OpenCLI 或其他 MCP 客户端中使用自然语言操作 PMS：

- 查询需求、项目、专题、故事、任务、子任务和迭代计划。
- 创建和编辑需求、项目、专题、故事和任务。
- 更新项目、专题、故事的流程节点负责人、排期和动态字段。
- 完成或回退允许操作的流程节点。
- 将故事加入迭代计划，并查看迭代中的工作项。
- 查询从需求到执行对象、流程节点、任务和迭代计划的完整关系。
- 在流程模板发生变化后，自动识别新的节点和组件能力。

成功标准是：AI 的操作结果与 PMS Web 页面使用同一套后端业务规则，不能出现“页面能做、AI 不能做”或“AI 绕过页面限制”的第二套逻辑。

## 3. 非目标

第一版不包含：

- 让 AI 直接连接 MySQL 或执行 SQL。
- 在连接器中复制项目、专题、故事、任务的业务规则。
- 在连接器中固定写死“开发与迭代控制”等具体节点名称。
- 通过管理员身份替普通用户执行操作。
- 用浏览器自动化替代 PMS API 主链路。
- 一次性重做 PMS Web 前端。

浏览器自动化可以作为未来连接外部系统的兜底能力，但 PMS 自身优先使用稳定 API 和后端命令能力。

## 4. 总体架构

```text
ChatGPT / Claude / DeepSeek Harness / OpenCLI
                 │
        MCP / OpenCLI Adapter
                 │
        pms-ai-connector
        ├── PMS API Client
        ├── MCP Server
        ├── OpenCLI Plugin
        ├── Skills / 使用说明
        └── Shared Schemas
                 │
        PMS Backend AI Capability Layer
        ├── Capability Catalog
        ├── Query Facade
        ├── Automatic Execute Facade
        ├── Permission and Business Validation
        ├── Idempotency and Version Guard
        └── Audit and Readback
                 │
              PMS Database
```

### 4.1 PMS 后端职责

PMS 后端继续负责：

- 当前用户身份和权限。
- 需求、项目、专题、故事、任务、迭代计划和流程模板数据。
- 流程实例、节点组件和动态字段。
- 需求只能关联一个执行对象等业务约束。
- 项目、专题、故事和任务的成员自动同步。
- 事务、并发版本、幂等、操作日志和审计。

现有的 `PmsCommandRegistry`、`AiOperationService`、`DshCapabilityController` 和现有 AI 命令作为基础继续演进，不另建平行业务服务。

### 4.2 连接器职责

`pms-ai-connector` 负责：

- 将统一 PMS 能力暴露为 MCP 工具。
- 将统一 PMS 能力包装成 OpenCLI 插件命令。
- 管理 Token、请求上下文、错误转换和结构化输出。
- 把自然语言客户端传来的参数转换为 PMS 能力协议。
- 在写操作完成后返回结果和刷新范围。

连接器不访问数据库，也不自行判断用户是否有权限。

## 5. 为什么采用 OpenCLI + MCP

OpenCLI 和 MCP 是互补关系：

| 场景 | 入口 |
| --- | --- |
| 本地终端 | OpenCLI 插件 |
| DeepSeek Harness 本地运行 | OpenCLI 插件或 stdio MCP |
| ChatGPT | 远程 HTTPS MCP Server |
| Claude 等 MCP 客户端 | MCP Server |
| PMS 自有业务判断 | PMS 后端 |

两种入口共同使用：

```text
pms-contracts
       ↓
pms-client
       ↓
MCP Server / OpenCLI Plugin
```

不让 OpenCLI 和 MCP 各自实现一套 PMS 规则。

## 6. 业务对象和闭环

连接器需要识别以下对象：

| 对象 | 能力范围 |
| --- | --- |
| 需求 | 创建、查询、编辑、关联一个执行对象、变更或解除关联 |
| 项目 | 创建、查询、编辑、更新项目流程节点 |
| 项目流程节点 | 负责人、排期、动态字段、任务、完成和回退 |
| 专题 | 创建、查询、编辑、绑定项目、更新专题流程 |
| 专题流程节点 | 负责人、排期、动态字段、故事、任务 |
| 故事 | 创建、查询、编辑、绑定专题、更新故事流程 |
| 故事流程节点 | 负责人、排期、动态字段、任务 |
| 任务/子任务 | 创建、编辑、分配执行人、修改状态 |
| 迭代计划 | 查询、创建、编辑、添加故事、移除故事 |
| 工作流模板 | 查询类型、版本、节点、组件和字段定义 |

业务层级为：

```text
需求
  ↓
项目 / 专题 / 故事（三选一作为需求执行对象）
  ↓
对应流程节点
  ↓
动态配置的节点组件
  ↓
任务 / 子任务
  ↓
迭代计划（无独立流程）
```

### 6.1 需求执行对象

一个需求只能关联一个执行对象：项目、专题或故事三者之一。

连接器收到冲突操作时必须让 PMS 后端拒绝，不允许把一个需求同时写入多个目标。

变更执行对象必须是明确的替换操作，并记录旧目标、新目标、操作者、时间和原因（如果后端规则要求原因）。

### 6.2 动态流程和组件

连接器不能写死：

```text
项目节点 = 开发与迭代控制
```

正确做法是动态读取：

```text
对象类型
  → 当前绑定的已发布流程版本
  → 当前流程节点
  → 当前节点组件
  → 组件字段和允许的操作
```

例如，专题绑定到项目流程模板配置的某个项目节点后，AI 应从能力目录识别该节点当前绑定的专题组件；未来组件改名或改绑到其他节点时，连接器无需改代码。

故事和专题节点同理。迭代计划不绑定流程模板，也不生成流程节点。

上下文分为两类：

- 全局操作（例如创建需求、创建项目、查询流程模板）可以不由客户端传页面上下文；后端使用服务端固定的 `global:pms` 上下文标识调用现有命令链路。
- 节点操作（例如更新节点字段、完成节点、在节点下创建任务）必须传入对象 ID、节点 ID和对象版本，不能用全局上下文代替。

这样既兼容现有 AI 操作表的上下文字段，又不会要求用户在自然语言中手工提供页面上下文。

### 6.3 成员自动同步

以下操作由 PMS 后端的领域服务统一处理：

- 专题负责人不在关联项目成员中时，自动加入项目。
- 故事负责人不在关联专题成员范围内时，自动加入专题。
- 节点负责人和任务执行人不在所属对象成员范围内时，自动加入。
- 解绑时，仅在人员不存在其他有效关联时才自动移除。

连接器只提交业务意图，不自行拼接成员增删操作。

## 7. 统一能力协议

第一版不向 AI 暴露大量零散接口，而是提供少量稳定入口。

### 7.1 能力发现

```text
pms_capabilities
```

返回：

- 资源类型。
- 可执行动作。
- 当前用户可用权限范围。
- 输入字段 Schema。
- 当前流程模板版本。
- 当前流程节点和组件。
- 前置条件。
- 风险级别。
- 操作后的刷新范围。

建议返回结构：

```json
{
  "version": "v1",
  "resources": [
    {
      "type": "topic",
      "actions": ["list", "get", "create", "update", "delete"],
      "workflow": {
        "templateVersionId": 12,
        "nodes": []
      }
    }
  ],
  "commands": []
}
```

### 7.2 查询能力

```text
pms_search
pms_get
pms_get_context
```

支持按名称、状态、负责人、项目、专题、故事、流程节点、迭代计划等条件查询。

查询结果必须包含：

- 资源类型和 ID。
- 展示名称。
- 当前状态。
- 当前流程节点。
- 关联关系摘要。
- 当前数据版本。
- 当前用户可继续执行的操作。

### 7.3 自动写入能力

```text
pms_execute_operation
pms_workflow_action
```

连接器默认使用自动执行模式，不要求用户再次点击确认。

但后端每次执行仍要完成：

1. Token 用户解析。
2. Agent/Connector 权限范围校验。
3. 资源权限校验。
4. 输入字段和流程组件 Schema 校验。
5. 业务前置条件校验。
6. 对象版本校验。
7. 幂等校验。
8. 事务执行。
9. 审计记录。
10. 执行结果回读。

请求示例：

```json
{
  "operation": "topic.update",
  "resource": {
    "type": "topic",
    "id": 7
  },
  "arguments": {
    "ownerId": 12,
    "projectId": 78
  },
  "expectedVersion": 4,
  "idempotencyKey": "pms-connector-20260927-001",
  "client": "opencli"
}
```

后端返回：

```json
{
  "operationId": "op_20260927_001",
  "status": "SUCCEEDED",
  "data": {},
  "refreshScopes": ["topic-detail", "topic-list", "project-detail"],
  "auditId": "audit_123"
}
```

### 7.4 内部预览

虽然不要求人工确认，但后端仍可以在自动执行前内部生成预览，用于：

- 校验依赖对象。
- 计算成员自动同步。
- 计算刷新范围。
- 记录执行前后的差异。
- 保证写入和审计使用同一份操作定义。

这个预览是内部安全机制，不作为用户必须确认的步骤。

## 8. 自动写入的安全边界

由于用户明确要求不增加人工确认，安全边界必须落在服务端：

- 连接器永远使用当前用户 Token，不接受请求体中的任意 `userId`。
- Agent 只能缩小后端允许的权限，不能扩大权限。
- 连接器不能使用管理员 Token 代替普通用户执行。
- 删除、终止、解绑等高风险操作仍受 PMS 原有权限和状态规则约束。
- 每个写操作必须有幂等键。
- 版本冲突不得静默覆盖。
- 重试请求必须返回上一次执行结果，而不是重新创建对象。
- 所有操作记录来源客户端、Agent、用户、请求 ID、操作 ID和目标对象。
- 连接器日志不记录访问 Token、密码和敏感字段原文。

现有 `/integration/dsh/v1` 的“预览后确认”接口保持兼容；新连接器使用通用的自动执行门面，不破坏现有 DSH UI 流程。

## 9. 后端接口边界

新增中立的集成入口：

```text
/integration/ai/v1/capabilities
/integration/ai/v1/query
/integration/ai/v1/context
/integration/ai/v1/operations/execute
/integration/ai/v1/workflow/actions
```

其中 `capabilities`、`query` 和 `context` 支持全局操作；`operations/execute` 和 `workflow/actions` 根据能力目录的 `requiresContext` 标志决定是否必须提供具体对象上下文。

这些 Controller 只负责：

- 认证和请求校验。
- 调用现有能力目录、查询服务、命令服务和领域服务。
- 统一响应格式。

不会在 Controller 中实现项目、专题、故事的业务逻辑。

对于已有命令，复用：

- `PmsCommandRegistry`
- `CommandPreviewService`
- `CommandExecutionService`
- `AiOperationService`
- 现有项目、节点、任务、专题、故事、需求、迭代计划 Service

如果现有 `AiOperationService` 的操作状态只支持“等待确认”，则增加一个明确的 `AUTOMATIC` 执行路径，而不是修改旧状态的含义。

## 10. 连接器仓库结构

```text
pms-ai-connector/
├── apps/
│   ├── mcp-server/
│   └── opencli-plugin/
├── packages/
│   ├── pms-contracts/
│   ├── pms-client/
│   ├── pms-capabilities/
│   ├── pms-auth/
│   └── result-format/
├── skills/
│   └── pms-project-management/SKILL.md
├── tests/
│   ├── contract/
│   ├── integration/
│   ├── mcp/
│   ├── opencli/
│   └── e2e/
├── deploy/
│   ├── Dockerfile
│   └── docker-compose.yml
├── README.md
└── package.json
```

技术建议：TypeScript、pnpm workspace、Node.js 22+。当前本地验证环境为 Node.js 24.10.0 和 pnpm 10.18.2，连接器首版按该环境锁定；后续再验证 Node.js 22 的兼容性。具体 MCP/OpenCLI SDK 版本在阶段 0 锁定。

## 11. 客户端安装方式

### OpenCLI

本地安装插件后配置 PMS 地址和当前用户 Token：

```text
opencli plugin install github:<org>/pms-ai-connector
```

### ChatGPT

ChatGPT 不能直接运行用户电脑上的 OpenCLI 本地插件，需要部署远程 HTTPS MCP Server：

```text
https://<domain>/mcp
```

可参考 [OpenAI Apps SDK examples](https://github.com/openai/openai-apps-sdk-examples) 和 [ChatGPT MCP Apps 说明](https://help.openai.com/en/articles/12584461-developer-mode-and-mcp-apps)。

### DeepSeek Harness

支持配置本地 stdio MCP、远程 MCP，或者使用 OpenCLI 本地插件。三种方式均调用同一个 `pms-client` 协议。

## 12. 版本策略

连接器单独版本化，PMS 后端提供能力协议版本：

```text
pms-ai-connector 0.1.x
PMS AI API v1
```

兼容规则：

- 后端新增字段不能破坏旧连接器。
- 删除能力前先标记 deprecated。
- 流程模板变化通过能力发现返回，不通过连接器发版解决。
- 连接器启动时校验后端 API 版本和能力协议版本。
- 不兼容变更升级为 `v2`，不覆盖 `v1`。

## 13. 分阶段实施

### 阶段 0：协议与基线

- 新建连接器项目骨架。
- 固定资源类型、动作名、响应结构和错误码。
- 固定自动执行策略和权限范围。
- 设计 PMS `/integration/ai/v1` 接口。
- 写能力协议、自动写入和动态流程的合同测试。

阶段 Review：确认连接器没有复制业务规则，自动写入没有绕过权限，需求—项目/专题/故事—任务—迭代计划闭环有验收用例。

### 阶段 1：PMS 后端通用能力门面

- 增加中立的 AI 集成 Controller。
- 扩展能力目录，覆盖需求、项目、专题、故事、任务、迭代计划和工作流模板。
- 增加自动执行服务，复用现有命令注册表和领域服务。
- 保留旧 DSH 预览确认接口。
- 增加用户、作用域、幂等、版本和审计测试。

阶段 Review：检查 Controller 没有业务逻辑，所有写入都经过同一套 Service 和事务。

### 阶段 2：连接器共享协议和只读能力

- 实现 `pms-contracts`。
- 实现 `pms-client`。
- 实现能力发现、搜索、详情和上下文。
- 实现结构化错误和候选项返回。
- MCP 和 OpenCLI 共用同一套客户端。

阶段 Review：同一个查询通过 MCP 和 OpenCLI 返回的数据、权限范围和版本信息一致。

### 阶段 3：自动写入和动态工作流

- 实现项目、需求、专题、故事、任务写操作。
- 实现负责人、排期、动态字段和节点操作。
- 读取当前发布的流程模板和动态组件 Schema。
- 实现需求单一执行对象规则。
- 实现成员自动加入和安全清理。
- 实现迭代计划和故事关联。

阶段 Review：执行完整端到端闭环，并覆盖非法关联、版本冲突、重复请求、历史账号和解绑成员场景。

### 阶段 4：MCP、OpenCLI 和部署

- 完成 MCP Server。
- 完成 OpenCLI Plugin。
- 增加 Docker 部署。
- 增加 ChatGPT、DeepSeek Harness 和本地 OpenCLI 文档。
- 增加健康检查、指标和日志脱敏。

阶段 Review：使用三个客户端各执行一遍相同业务操作，确认最终 PMS 数据完全一致。

### 阶段 5：发布与生产加固

- 增加 OAuth/OIDC 或现有 SSO 适配。
- 增加 Token 撤销和连接器权限管理。
- 增加限流和失败重试策略。
- 发布 GitHub Release 和兼容矩阵。
- 建立升级、回滚和审计查询流程。

## 14. 验收场景

至少必须通过以下流程：

```text
创建需求
  → 将需求关联项目
  → 根据项目流程节点的动态组件创建专题
  → 设置专题负责人并自动补充项目成员
  → 在专题节点下创建故事
  → 设置故事负责人和任务执行人
  → 将故事加入迭代计划
  → 查询完整关系和当前进度
  → 修改任意节点字段
  → 再次读取并验证页面与 AI 返回一致
```

同时验证：

- 一个需求不能同时关联项目和专题。
- 已完成、已终止或已删除项目不能绑定专题。
- 故事可以不绑定专题。
- 迭代计划不生成流程节点。
- 流程模板把组件绑定到其他节点后，AI 能自动发现新位置。
- 同一幂等键重复提交不会重复创建。
- 对象版本冲突不会覆盖最新数据。
- 当前用户无权限时，能力不会出现在目录中，直接调用也会被拒绝。

## 15. 方案结论

采用独立 `pms-ai-connector` 项目，使用 OpenCLI + MCP 双入口，PMS 后端作为唯一业务真源，所有流程和组件动态发现，写操作自动执行但不绕过服务端安全校验。

这套设计能让未来增加新的业务对象、流程类型和节点组件时，主要通过 PMS 的能力目录和流程模板配置完成，而不是重新开发每个 AI 客户端的插件逻辑。
