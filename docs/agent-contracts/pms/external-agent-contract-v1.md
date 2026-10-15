# External Agent Contract v1

**Status:** proposed

## Goal

让 Claude、Codex 等外部 Agent 能够通过 `pms-cli` 读取 PMS 上下文、创建只读子 Agent、生成节点必填字段草稿，并在用户确认后安全推进项目、需求、专题和故事。

## Product boundary

- 外部 Agent 负责自然语言理解、任务拆解、子 Agent 协作和结果汇总。
- `pms-cli` 负责能力发现、上下文读取、节点契约读取、草稿校验、预览、执行和回读验证。
- PMS 后端负责事实、工作流、权限、版本、幂等和最终写入。
- PMS 前端继续承担数据浏览和人工维护，不承担主 AI 对话入口。
- PMS 不在主路径内调用模型；`PMS_MODEL_*` 不属于外部 Agent 主路径的必要配置。

## Core lifecycle

```text
read context
  → read node contract
  → create read-only child tasks when useful
  → merge facts and draft changes
  → preview
  → explicit user confirmation
  → execute with scoped write authority
  → read back and verify
```

## Shared contract envelope

所有场景使用相同的外层对象，业务场景可以保留自己的 `scenarioDraft`：

```text
AgentWorkSession
├── contractVersion
├── sessionId
├── resource
├── contextSnapshot
├── nodeContract
├── agentTask
├── draft
├── preview
├── confirmation
├── operation
└── verification
```

读取阶段的 `draft`、`preview`、`confirmation`、`operation` 和 `verification` 可以为空；只有进入草稿、预览或写入阶段后才填充对应对象。

### ContextSnapshot

必须携带资源引用、PMS 上下文版本、来源引用、当前节点、工作流节点、关系、允许动作和警告。快照是一次 Agent 任务的输入，不得被后续回读数据静默替换。

### NodeContract

必须携带节点标识、节点版本、必填字段、可写字段、保护字段、字段说明、可用动作、允许的流程转换和缺失输入规则。字段级可写范围以 PMS 返回的 `saveContract` 为最终依据。

### AgentTask

必须携带任务标识、父任务标识、目标、角色、资源引用、快照标识和权限模式。当前版本的子 Agent 只接收快照和任务，不接收 PMS 登录凭据，也不直接调用 `pms-cli`；主 Agent 负责读取、合并和最终写入。未来若需要子 Agent 直接读取 PMS，必须先增加服务端签发的独立只读委派凭据。

### AgentDraft

必须区分事实、推断、假设、未决问题、建议字段变更、缺失字段、追问和证据引用。不得把推断写入 PMS 事实字段，除非用户明确确认。

## Safety rules

- 读取和草稿阶段不得产生 PMS 写入。
- 所有写入先预览，再等待用户确认。
- 执行必须使用幂等键、上下文版本和操作编号。
- 版本冲突返回 `CONFLICT`，不得覆盖新数据。
- 回读失败返回 `WRITE_RESULT_UNKNOWN`，不得盲目重试。
- 子 Agent 不得拥有主 Agent 的写入权限。
- PMS 后端是最终权限裁决方，Agent Contract 不能扩大权限。

## Non-goals

- 不建设 PMS 内置聊天工作台。
- 不在本阶段实现 PMS 内置模型路由或模型计费平台。
- 不为每个必填字段创建独立 Agent。
- 不拆分 PMS 微服务。
- 不一次性覆盖所有复杂自动化场景。

## Acceptance criteria

1. 项目、需求、专题和故事都能返回统一的上下文和节点契约外层结构。
2. Claude/Codex 能仅通过 `pms-cli` 完成读取、草稿、预览、确认、执行和回读验证。
3. 子 Agent 的输出可以被主 Agent 合并，且子 Agent 默认无法写入 PMS。
4. 读取、草稿、取消、冲突、未知结果和成功写入都有结构化结果。
5. 现有 `requirement-analysis/v1` 和 `story-workbench/v1` 保持兼容，并能映射到统一外层结构。
