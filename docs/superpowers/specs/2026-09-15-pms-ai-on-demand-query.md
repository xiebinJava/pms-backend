# PMS AI 按需查询设计

## 目标

让 PMS 项目助手只携带轻量页面定位信息，用户提出事实问题时再由 Agent 通过 Work Helper 的受控工具查询 PMS 权威数据。

## 核心原则

1. 页面上下文不是业务事实，只用于定位页面类型、项目、节点和筛选范围。
2. 项目、任务、人员和状态事实必须来自 PMS 后端查询结果。
3. Work Helper 只能调用已注册的工具，不能执行任意 Shell，也不能直连数据库。
4. PMS 后端必须用短期 AI 委托令牌恢复当前登录用户，并重新执行数据权限校验。
5. 查询工具只读、可自动执行；写操作仍走命令预览和用户确认流程。
6. 结果必须返回结构化数据、可读标签、数据范围和时间信息，便于 Agent 准确回答并说明范围。

## 第一阶段工具

### `pms.context.inspect`

按页面定位信息读取权威页面快照。用于“当前页面有哪些紧急项目”“这个项目当前节点是什么”等页面相关问题。它不再由 ChatService 每轮自动预取，只有 Agent 判断需要时才调用。

### `pms.task.query`

查询当前用户或指定可读项目范围内的任务。

请求参数：

```json
{
  "scope": "mine",
  "due": "today",
  "status": "open",
  "projectId": null,
  "nodeId": null,
  "page": 1,
  "pageSize": 50
}
```

约束：

- `scope=mine` 默认按当前登录用户的 `assigneeId` 查询，模型不能传入任意用户 ID。
- `scope=project` 必须提供 `projectId`，并经过当前用户项目可见权限校验。
- `due` 支持 `today`、`overdue`、`upcoming`、`any`。
- `status` 支持 `open`、`done`、`all`，默认 `open`。
- `pageSize` 最大 100。

响应必须包含：`dataScope`、`authoritative`、`timezone`、`asOfDate`、`total`、`pagination` 以及任务的标题、项目、节点、负责人、状态/状态标签、优先级/优先级标签、截止日期和版本。

## 调用链

```text
PMS 页面
  └─ 发送轻量 page_context
       └─ Work Helper 组装 Agent 工具
            └─ Agent 判断是否调用 pms.context.inspect / pms.task.query
                 └─ PMS /ai/query/tasks 或 /ai/context/inspect
                      └─ 委托令牌 + 当前用户权限 + 结构化结果
                           └─ Agent 组织答案
```

## 失败与边界

- 工具失败时 Agent 必须明确说明“未读取到”，不能根据页面元数据猜测。
- 查询结果只代表请求中的范围和时间点；分页结果不能被表述成全量结果。
- 访问已完成、已终止或无当前节点的项目时，要保留空值并说明原因。
- 写命令仍要求预览、确认、版本校验、幂等键和审计记录，本阶段不扩大写权限。

