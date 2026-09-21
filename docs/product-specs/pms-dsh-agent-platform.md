# PMS–DSH Agent 能力平台方案

## 1. 目标

让用户通过 DSH 的自然语言对话，查询和操作 PMS 中有权限访问的业务信息，包括项目、节点、任务、人员、项目成员、排期、表单、审批、节点完成、归档和删除等操作。

目标不是把每一个 PMS 按钮都做成一个 DSH 工具，而是让 DSH 通过少量稳定协议调用 PMS 的业务能力目录和工作流。

## 2. 方案来源与取舍

本方案综合了两份外部方案以及当前 PMS/DSH 已有实现：

- ChatGPT 方案中关于 CopilotKit、Browser Use、Stagehand、LangGraph、Playwright MCP 的分层建议。
- DeepSeek 方案中关于“API/MCP 深度集成”和“浏览器自动化”双路线、高频能力固化、低频能力探索、从最小闭环开始的建议。
- 当前 PMS 的 `PmsCommandRegistry`、命令预览/执行协议、`DshQueryService` 和 DSH 的 `pms_query`、`pms_command_preview`、`pms_command_execute`。

最终取舍如下：

1. PMS API、业务命令和工作流是主执行链路。
2. DSH 保留少量通用工具，不为每个页面按钮新增工具。
3. PMS 能力目录是 Agent 发现能力、字段、权限和风险的唯一入口。
4. OpenCLI 作为跨系统连接和无 API 页面操作的兜底层。
5. 浏览器自动化不作为 PMS 高风险写入的主路径。
6. LangGraph 或同类编排框架只在长流程、跨轮次和多 Agent 协同场景引入。
7. 所有写操作必须预览、确认、执行、回读校验和审计。

## 3. 总体架构

```text
用户自然语言
      ↓
DSH PMS Agent
      ├── PMS 提示词
      ├── PMS Skill
      └── 固定的 DSH 工具
              ↓
PMS AI 能力网关
      ├── 能力目录
      ├── 查询引擎
      ├── 命令预览器
      ├── 批量执行器
      ├── 工作流执行器
      ├── 权限与风险策略
      ├── 幂等与版本校验
      ├── 审计日志
      └── 刷新事件
              ↓
PMS Service / API / 数据库

没有 API 或暂未建模的功能
      ↓
OpenCLI / 浏览器 Adapter 兜底
```

### 3.1 DSH 的职责

- 维护对话、Agent、Skill 和会话状态。
- 根据当前工作区选择 PMS Agent。
- 通过自然语言补全缺失信息。
- 调用能力目录了解可执行能力。
- 生成操作计划并向用户展示预览。
- 等待用户明确确认。
- 执行确认后的操作。
- 展示执行进度、成功结果和失败原因。
- 接收 PMS 刷新事件并刷新当前业务工作区。

### 3.2 PMS 的职责

- 维护业务数据和业务权限。
- 校验当前用户对目标对象和能力的权限。
- 校验字段、枚举、关联对象和业务前置条件。
- 执行事务、幂等、版本校验和补偿。
- 记录自然语言请求、命令、参数摘要、用户、Agent、结果和审计信息。
- 返回权威的业务结果。

### 3.3 模型不能直接做的事情

- 不能执行任意 SQL。
- 不能调用任意 PMS URL。
- 不能绕过 PMS 权限。
- 不能使用管理员身份替代当前用户。
- 不能凭空填写人员、项目、日期或表单值。
- 不能在没有预览和确认时执行写操作。
- 不能用浏览器自动化绕过 API 的权限和风控。

## 4. DSH 稳定工具面

DSH 最终保持以下稳定工具，工具数量不随 PMS 功能线性增长。

### 4.1 `pms_capabilities`

返回当前用户、当前 Agent 和当前 PMS 上下文可用的能力。

```json
{
  "workspace": "pms",
  "capabilities": [
    {
      "name": "task.create",
      "label": "创建任务",
      "domain": "task",
      "risk": "medium",
      "requiredScopes": ["pms:task:write"],
      "supportsPreview": true,
      "supportsBatch": true,
      "refreshScopes": ["project-detail", "task-board"],
      "inputSchema": {}
    }
  ]
}
```

### 4.2 `pms_query`

统一查询项目、节点、任务、人员、组织、成员、表单、审批和自定义业务对象。

```json
{
  "resource": "tasks",
  "filters": {
    "projectId": 22,
    "assignee": "current_user",
    "status": "open",
    "dueDate": "today"
  },
  "page": 1,
  "pageSize": 50,
  "fields": ["id", "name", "assignee", "dueDate", "status"]
}
```

返回必须包含：

- `authoritative: true|false`
- `dataScope: current-page|filtered-result|full-result`
- `total`
- `page`
- `pageSize`
- `staleAt` 或数据读取时间
- 字段中文标签
- 权限裁剪说明

Agent 只能根据本次返回的数据回答，不能把历史对话或页面缓存当成当前结果。

### 4.3 `pms_command_preview`

把一个或多个写操作转换为可读的操作计划。

```json
{
  "commands": [
    {
      "command": "task.create",
      "arguments": {
        "projectId": 22,
        "nodeId": 192,
        "name": "补充技术评审材料",
        "assigneeId": "current_user",
        "dueDate": "2026-09-30"
      }
    }
  ],
  "source": "natural-language",
  "clientRequestId": "stable-request-id"
}
```

预览返回：

- 规范化后的字段。
- 中文展示值。
- 前置条件检查结果。
- 警告和阻断原因。
- 风险等级。
- `operationId`。
- `previewHash`。
- 过期时间。
- 执行后刷新范围。

### 4.4 `pms_command_execute`

只能执行当前会话中最近一次、仍有效且用户明确确认过的预览。

必须支持：

- `operationId`。
- `idempotencyKey`。
- 批量结果。
- 部分成功。
- 版本冲突。
- 业务阻断。
- 重试建议。
- 审计编号。

### 4.5 `pms_workflow_execute`

用于项目初始化、节点完成、项目归档等多步骤业务流程。

工作流不把每一步交给模型临时决定，而由 PMS 后端定义流程状态、前置条件和终止规则。

## 5. PMS 能力目录

能力目录是本方案的核心，不等同于 DSH 工具注册表。

### 5.1 能力描述

```json
{
  "name": "node.complete",
  "version": 1,
  "label": "完成节点",
  "description": "完成当前项目节点并进入下一阶段",
  "domain": "node",
  "executor": "workflow",
  "risk": "high",
  "requiredScopes": ["pms:node:write"],
  "supportsPreview": true,
  "supportsBatch": false,
  "idempotent": true,
  "inputSchema": {
    "projectId": {"type": "long", "label": "项目"},
    "nodeId": {"type": "long", "label": "节点"},
    "comment": {"type": "string", "label": "完成说明"}
  },
  "preconditions": [
    "required_tasks_completed",
    "required_forms_completed",
    "current_user_can_complete"
  ],
  "refreshScopes": ["project-detail", "project-dashboard"]
}
```

### 5.2 能力目录的来源

能力目录可以由以下内容组合生成：

1. 现有 `PmsCommandRegistry` 和 `PmsCommandMetadata`。
2. PMS Service 的业务命令处理器。
3. OpenAPI 的接口 Schema。
4. 表单配置和流程模板。
5. 权限、风险和刷新范围等业务注解。

OpenAPI 可以帮助生成基础请求模型，但不能直接把所有接口暴露给模型。最终给 Agent 的能力必须经过业务包装。

## 6. 操作类型设计

### 6.1 只读查询

直接走 `pms_query`，不需要确认。

例子：

```text
我今天有几个任务需要完成？
当前有多少个紧急项目？
项目 22 有哪些成员？
```

### 6. 简单写入

走命令预览和执行。

例子：

```text
修改项目经理
调整任务截止日期
给项目添加成员
创建一个任务
```

### 6. 批量操作

一批命令生成一个预览、一次确认、统一执行、统一刷新。

例子：

```text
创建两个任务
给三个任务重新分配负责人
把本周到期的任务统一延期到下周五
```

### 6. 复杂工作流

走 `pms_workflow_execute`。

例子：

```text
帮我完成第一个节点
初始化这个项目
归档这个项目
```

### 6. 无 API 的功能

走 OpenCLI Adapter，但必须声明：

- 使用哪个系统。
- 使用哪个页面。
- 是否允许写入。
- 成功判断方式。
- 失败后的补偿方法。
- 是否需要人工确认。

## 7. “完成节点”的闭环流程

用户说：

```text
帮我完成当前节点
```

系统执行：

1. 读取当前项目和当前节点。
2. 查询节点负责人、排期和任务。
3. 查询必填表单和审批状态。
4. 查询当前用户是否有完成权限。
5. 判断所有前置条件。
6. 如果有阻断项，返回缺失项，不猜测、不强制完成。
7. 如果条件满足，生成节点完成预览。
8. 等待用户明确确认。
9. 执行节点完成工作流。
10. 回读节点状态、下一节点和项目进度。
11. 统一刷新 PMS 当前页面。
12. 返回结果和审计编号。

返回阻断示例：

```text
当前节点暂时不能完成：

1. 还有 2 个任务未完成。
2. “技术方案”表单尚未填写。
3. 当前用户没有完成节点权限。
```

## 8. 多 Agent 协同边界

多 Agent 不是第一阶段的重点。

未来可以采用：

```text
PMS 项目助手 Agent
      ↓
Planner Agent：拆分需求和计划
      ↓
Developer Agent：执行开发任务
      ↓
Tester Agent：执行测试和验收
      ↓
PMS 项目助手 Agent：汇总、判断、汇报
      ↓
用户：处理需要决策的问题
```

但这些 Agent 都应该通过 PMS 能力目录操作 PMS，不能各自维护一套项目状态。

PMS 是唯一事实源：

- Agent 的工作状态写入 PMS 任务或执行记录。
- Agent 的交接使用结构化 handoff。
- 失败原因和证据写入任务日志。
- 需要人决策时进入人工待办。

APM 和 ACPMS 可以借鉴 Planner、Manager、Worker、Handoff、Human-in-the-loop、代码执行记录等设计，但不能代替 PMS 的企业项目模型。

## 9. OpenCLI 兜底策略

OpenCLI 适合做 DSH 的跨系统 UI Adapter 层：

```text
PMS 有稳定 API       → PMS API
OA 只有网页          → OpenCLI Adapter
CRM API 覆盖不完整   → API 优先，OpenCLI 补充
第三方旧系统         → OpenCLI Adapter
```

浏览器兜底操作必须：

- 复用用户当前登录态。
- 不能获取或输出 Cookie、密码、Token。
- 不能绕过业务权限。
- 写操作仍要经过风险确认。
- 操作过程保留截图、页面地址和结果证据。
- 失败后停止，不进行无限重试。

## 10. 安全、审计和失败恢复

### 10.1 权限

最终权限由 PMS 服务端决定：

```text
SSO 用户身份
    ∩ PMS 用户权限
    ∩ Agent 允许能力
    ∩ 当前项目范围
    ∩ 当前命令风险策略
```

Agent 配置不能扩大用户权限。

### 10.2 写入确认

- 查询不确认。
- 普通创建和修改需要预览后确认。
- 批量操作需要显示数量和完整范围。
- 删除、归档、权限修改需要高风险确认。
- 用户未明确确认时不能执行。

### 10.3 重试

只允许对幂等查询和带幂等键的命令重试。

遇到以下错误必须停止并说明：

- 权限不足。
- 授权过期。
- 版本冲突。
- 参数不完整。
- 业务前置条件不满足。
- 系统繁忙但结果未知。

结果未知时先查询确认，不能直接重复创建。

### 10.4 循环终止

每个 Agent 工作流必须设置：

- 最大步骤数。
- 最大重试次数。
- 最大执行时间。
- 最大工具调用次数。
- 人工介入条件。

达到阈值后进入“需要人工处理”，不能继续循环。

## 11. 页面刷新策略

PMS 写入操作返回 `refreshScopes`：

```json
{
  "refreshScopes": ["project-list", "project-detail", "task-board"]
}
```

DSH 在一个批量操作的所有命令完成后，只发送一次刷新事件：

```text
任务 1 完成
任务 2 完成
任务 3 完成
↓
统一刷新一次 PMS 页面
```

如果部分成功，则刷新受影响的页面，并在对话中列出成功和失败项。

## 12. 分阶段范围

### 第一阶段：能力目录和统一协议

- 把 `PmsCommandRegistry` 扩展为能力目录。
- 统一能力 Schema、权限、风险、刷新范围。
- 保留现有查询和命令兼容性。
- DSH 继续使用现有工具，先不改变用户体验。

### 第二阶段：统一查询

- 扩展 `pms_query` 的资源类型。
- 增加用户、组织、成员、节点、表单和审批查询。
- 增加中文字段标签、分页和数据范围说明。
- 增加引用解析：当前用户、当前项目、当前节点和日期表达式。

### 第三阶段：批量命令和统一刷新

- 支持一次预览多个命令。
- 支持幂等批量执行。
- 支持部分成功和结果回读。
- 所有命令完成后只刷新一次。

### 第四阶段：业务工作流

优先实现：

1. 项目创建并初始化。
2. 任务批量创建与分配。
3. 节点负责人和排期更新。
4. 节点完成与回退。
5. 项目归档与删除。

### 第五阶段：OpenCLI 适配层

- 定义 Adapter 接口。
- 接入一个没有 API 的示例功能。
- 增加登录态、风险、审计和成功判断。
- 禁止 OpenCLI 作为 PMS 高风险写入主路径。

### 第六阶段：长流程和多 Agent

- 引入可持久化的流程状态。
- 增加 Planner、Manager、Worker 协同。
- 增加 Agent handoff。
- 增加人工决策节点。
- 增加市场中的 Agent 选择和项目团队绑定。

## 13. 验收标准

### 查询

- 能查询 PMS 中授权范围内的项目、任务、人员、成员、节点和表单数据。
- 回答明确标记数据范围和读取时间。
- 查询失败时不引用历史结果。

### 写入

- 创建、修改、分配、排期、成员和节点操作全部走预览确认。
- 批量操作只生成一次预览、一次确认和一次页面刷新。
- 重复执行不会重复创建数据。
- 结果未知时先查询确认。

### 工作流

- 节点完成会检查所有业务前置条件。
- 前置条件不满足时返回清晰阻断原因。
- 多步骤流程失败后可以恢复或进入人工处理。

### 权限

- Agent 不能扩大 PMS 用户权限。
- 删除、归档和权限类操作有额外确认。
- 所有执行都有审计记录。

### 兜底

- 无 API 功能可以通过 OpenCLI Adapter 执行。
- 页面变化或登录失效时能停止并反馈，不会无限重试。

