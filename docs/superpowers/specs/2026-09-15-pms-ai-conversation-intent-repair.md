# PMS AI Conversation Intent Repair Specification

## Goal

让 PMS 项目助手能够在同一个用户会话中保留上下文，理解“确认”“当前项目”“当前节点”“我”等省略表达，并在创建任务前生成准确、可审计的变更预览。

## Scope

本期只修复项目助手的对话编排和任务创建闭环，覆盖 Work Helper、PMS AI bridge 和 PMS 前端确认交互。查询工具继续保持只读；任务创建继续必须预览并经过用户确认。

## Required behavior

1. 用户提出“在当前项目当前节点创建两个任务，负责人都是我，截止 9 月 25 日”后，Agent 必须形成一个结构化任务草稿，而不是立即猜测或丢失参数。
2. “确认”或“就在当前节点下创建”必须合并到上一轮未完成的任务草稿中，不得被当成独立问题。
3. `projectId`、`nodeId`、当前用户 ID 必须由 PMS 权威上下文和当前委托身份解析或校验，不能由模型自由编造。
4. 每个任务都必须经过 `pms.command.preview`；前端展示全部预览后才允许执行。
5. 任务创建成功后，刷新项目详情、任务看板和项目看板。
6. 会话按用户和 Agent 保存；切换项目只更新消息的项目标签，不新建或覆盖会话。
7. 刷新页面后，历史消息、未完成的任务预览和待确认状态可以恢复；过期的 PMS operation 必须提示重新预览。
8. Agent 仍然不能访问数据库、执行任意 shell 或绕过 PMS 权限。

## Contract decisions

### Conversation history

Work Helper persists user/assistant messages in `ChatSession.messages`. Before each new model turn, it loads a bounded history window, converts it to AgentScope messages, and passes it through `AgentState.context`. The current user message is appended only once.

### Pending task intent

The session stores a structured pending action:

```json
{
  "kind": "task.create",
  "status": "needs_confirmation",
  "projectRef": "current_project",
  "nodeRef": "current_node",
  "tasks": [
    {"title": "任务1", "assigneeRef": "current_user", "dueDate": "2026-09-25"},
    {"title": "任务2", "assigneeRef": "current_user", "dueDate": "2026-09-25"}
  ],
  "previews": []
}
```

The state is persisted with the session so a follow-up confirmation does not depend on an in-memory Agent object.

### PMS resolution

Work Helper may use the page context only as a locator. PMS resolves and validates the current project, current node, current authenticated user, project/node versions and permissions. A failed or ambiguous resolution returns a clarification request and never creates a preview.

### Confirmation

The frontend keeps the explicit “确认执行” button as the primary safe path. If a pending preview exists, a typed confirmation such as “确认” is handled by the pending-action path and does not start a new free-form model turn. Cancellation clears the pending action without executing anything.

## Acceptance examples

```text
用户：帮我在这个项目下创建2个任务，任务1、任务2，负责人都是我，截止日期是9月25号
助手：识别当前项目和节点，展示两个任务的完整预览，请确认执行。
用户：确认
助手：已创建2个任务，并刷新相关页面数据。
```

If the current project/node or current user cannot be resolved, the assistant must state exactly which field is missing and ask only for that field. It must not claim that a task was created.
