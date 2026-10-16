# Story Node Orchestrator v1 设计规格

## 状态

Ready for implementation。该规格针对外部 Claude/Codex Agent 通过 `pms-cli` 推进故事节点的第一个垂直切片。

## 目标

让 Agent 能够根据一个故事当前的流程节点，读取节点上下文，生成结构化字段草稿，发现缺失信息，并在用户确认后通过既有的预览绑定写入链路保存节点内容。

目标闭环：

```text
Claude/Codex 自然语言
→ pms-cli 读取 story focused context
→ Agent Draft
→ 节点契约和权限校验
→ 缺失字段/追问或生成 Plan
→ Preview
→ 用户确认
→ Execute existing node-field command
→ Story + focused context readback
→ Verification + ordered events
```

## 背景与现状

当前系统已经具备：

- `storyWorkbench` 场景，支持故事节点 `draft / plan / execute`。
- PMS 上下文中的 `story-node-workbench.saveContract`，声明命令、故事 ID、节点 ID、版本、variant、字段路径和可写字段。
- 预览绑定执行、幂等键、版本冲突和回读验证。
- `AgentDraft`、子 Agent 任务和证据引用校验。
- Orchestrator 会话、回合、计划、预览、确认、事件和增量 session 读取，但当前只支持 `REQUIREMENT_ANALYSIS_SAVE`。

当前缺口是：后端 Orchestrator 不能接受故事资源和故事草稿，Connector 合约也把 Orchestrator draft 和 plan 限定为需求分析。

## 范围

### 本次交付

1. 新增 Orchestrator Intent：`STORY_NODE_FIELD_UPDATE`。
2. 支持 `resourceType=story` 的会话创建。
3. 支持 `story-workbench/v1` 草稿协议。
4. 从 focused context 生成节点契约：必填字段、可写字段、受保护字段、允许动作和节点版本。
5. 对草稿做字段白名单、受保护字段、引用权限和上下文版本校验。
6. 缺少必填字段时返回 `NEEDS_INPUT`，不生成预览、不执行写入。
7. 字段完整时复用 `development-item.node.field.update` 的现有 preview/confirm/execute/readback 链路。
8. 将执行结果、回读验证和事件纳入现有 Orchestrator session。
9. 为 Claude/Codex 提供对应的 `pms-cli` 命令和机器可读 JSON 输出。

### 明确不做

- 不开发 PMS 内部聊天工作台。
- 不新增故事节点完成、回退、状态流转或自动推进能力。
- 不把模型调用放进 PMS Backend；模型仍由 Claude/Codex 或 Connector 外部 Provider 负责。
- 不绕过已有 `saveContract`、预览、确认、幂等和权限校验。
- 不在本次支持项目、专题和需求的 Orchestrator 扩展；它们沿用现有场景，后续复用本协议扩展。
- 不在本次实现子 Agent 委托、子 Agent 结果合并或自动拆解；v1 只接收主 Agent 产生的 `AgentDraft`，子 Agent 委托协议单独设计为后续版本。

## 领域不变量

- `Conversation` 绑定一个 story，是跨节点的会话容器；`Plan` 绑定一个 story、一个 `nodeId`、一个 `nodeVersion` 和一个组件 variant。
- 一个 turn 最多产生一个 Plan；Plan 一旦持久化不可修改。`NEEDS_INPUT` 的补充信息通过新 turn 生成新 Plan，服务端在新鲜上下文上重新校验。
- Conversation 只有在当前 Plan 处于 `SUCCEEDED`、`FAILED`、`CONFLICT` 或 `CANCELLED` 时才能切换到另一个节点；当前 Plan 处于 `NEEDS_INPUT`、`PREVIEWED`、`WAITING_CONFIRMATION`、`EXECUTING`、`VERIFYING` 或 `WRITE_RESULT_UNKNOWN` 时，提交不同 `nodeId` 必须返回 `CONVERSATION_TARGET_MISMATCH`。如果用户要并行推进另一个节点，应创建新的 Conversation。
- 一个 Plan 的 preview 和 execute 使用同一个 `pms_ai_operation` 记录；`previewOperationId` 就是该 Operation 的唯一 ID。幂等重放不创建第二条 Operation。
- `confirmedAt` 和 Operation 的 `userId` 是确认事实；v1 要求预览人与确认人是同一认证主体。跨用户审批不在本切片内。
- `WRITE_RESULT_UNKNOWN` 只允许重新读取和验证同一个 Operation；不能以新幂等键再次写入。
- `unresolvedDecisions` 是 Agent 提供的说明性信息，不单独决定是否生成 Plan；是否进入 `NEEDS_INPUT` 只由字段契约的 required/validation 结果决定。

## 合约设计

### 草稿

Connector 和 Backend 共同支持以下 draft envelope。节点定位不放进 draft，继续由现有 `AppendTurnRequest` 外层字段承载：

```json
{
  "contractVersion": "story-workbench/v1",
  "draft": {
    "facts": [{"text": "...", "sourceRefs": ["story:34"]}],
    "inferences": [],
    "assumptions": [],
    "unresolvedDecisions": [],
    "patch": {
      "implementationNote": "...",
      "selfTestResult": "..."
    }
  }
}
```

完整的 story turn HTTP request 形状为：

```json
{
  "message": "补充开发节点的实现说明和自测结果",
  "contractVersion": "story-workbench/v1",
  "draft": {
    "contractVersion": "story-workbench/v1",
    "draft": {
      "facts": [{"text": "当前节点是 development", "sourceRefs": ["story-node:702#component:story-node-workbench"]}],
      "inferences": [],
      "assumptions": [],
      "unresolvedDecisions": [],
      "patch": {"implementationNote": "...", "selfTestResult": "..."}
    }
  },
  "nodeId": 702,
  "contextVersion": 12,
  "requestId": "turn-20261016-001"
}
```

外层 `AppendTurnRequest.contractVersion` 选择请求路径，内层 `AgentDraft.contractVersion` 描述草稿协议；v1 要求二者相同。服务端用外层 `nodeId/contextVersion` 读取并绑定 focused context，再把 `node.version` 和 `saveContract.version` 写入持久化 plan。draft envelope、HTTP request 和 persisted/response `PlanView` 是三个不同形状，不能重复塞入节点字段，也不能让 Connector 自行从 draft 推断节点。

`patch` 是本轮相对于当前上下文的增量。继续 `NEEDS_INPUT` 时，服务端在事务中读取当前唯一待补充 Plan，将其有效 patch 与本轮 patch 合并，再对新鲜上下文重新校验；原 Plan 保持不可变。`patch` 只能包含当前节点 `saveContract.writableFields` 与节点变体契约的交集。Agent 不直接提供 `missingFields`，由服务端根据当前节点字段和内容计算，避免模型伪造完成状态。

节点字段契约的权威来源是 Backend 的 `StoryNodeWorkbenchPolicy`。它为每个 variant 暴露不可变的 `FieldContract`（字段 key、类型、是否必填、是否可为空、空值语义、校验规则、是否受保护）；工作流 JSON 只提供展示 label、顺序和组件用途。Orchestrator 不在自己的 switch 或提示词中复制业务规则。若某个 variant 没有必填字段，`missingFields` 必须为空，不得凭示例臆造必填项；新增必填字段必须先修改 policy/配置并补充契约测试。

v1 的产品约定是：development variant 的 `implementationNote` 和 `selfTestResult` 为必填；这个约定写入 `StoryNodeWorkbenchPolicy` 的 FieldContract，而不是写在 Orchestrator 中。其他 variant 继续以 policy 返回的 required 集合为准，直到有明确业务规则和测试覆盖。

字段更新语义固定为：未出现的 key 表示不修改；`null` 只有在 `nullable=true` 时才表示清空，否则拒绝；必填字符串去除首尾空白后为空、必填数组为空时视为缺失；`false` 和 `0` 是有效值，只有字段自身的范围规则可以拒绝它们。这样同一份草稿在 CLI、Connector 和 Backend 上会得到相同结果。

CLI 的 `questions` 是根据服务端返回的 `missingFields` 生成的稳定提示，不接受模型直接覆盖，保证同一个节点状态得到一致的追问。

### 节点契约

Plan 响应增加可选的 `nodeContract` 和 `missingFields`：

```json
{
  "nodeId": 702,
  "key": "development",
  "label": "开发",
  "version": 3,
  "requiredFields": [
    {"key": "implementationNote", "label": "实现说明", "type": "textarea", "required": true},
    {"key": "selfTestResult", "label": "自测结果", "type": "textarea", "required": true}
  ],
  "writableFields": ["implementationNote", "selfTestResult", "codeLink", "mergeStatus", "deployEnv", "testCases"],
  "protectedFields": ["status", "ownerId", "startDate", "dueDate"],
  "allowedActions": ["development-item.node.field.update"],
  "allowedTransitions": [],
  "missingInputRules": ["必填字段不能为空"],
  "writeCapable": true
}
```

`requiredFields`、`writableFields` 和 `protectedFields` 均由同一份 policy projection 生成。投影必须以实际 merge 行为为准：当前 policy 中 `writableFields` 列表与 `merge` 对 writing identity 字段的处理存在差异，实施任务必须先修正二者，或在修正前不对这些字段宣称可写；每个被宣称可写的字段都要有“输入后持久化内容确实改变”的回归测试。资源级的 `title` 只有在当前 variant 的 policy 明确列入且 merge 支持时才可写；它不被 Orchestrator 另行硬编码为全局禁止字段。最终可写集合始终是 `saveContract.writableFields ∩ policy.effectiveWritableFields - policy.protectedFields`。

### 状态

故事 Orchestrator 复用现有 `AiOrchestratorState`，不引入与代码不一致的 `PLANNED`、`CONFIRMED` 或 `REJECTED` 字符串。保留现有状态，并新增两个明确状态：`NEEDS_INPUT` 和 `WRITE_RESULT_UNKNOWN`。

| 产品语义 | 持久化状态/错误码 | 说明 |
| --- | --- | --- |
| 草稿已通过，可生成预览 | `PLAN_READY` | 对外 CLI 可显示为 plan ready |
| 缺少必填输入 | `NEEDS_INPUT` + `STORY_MISSING_INPUT` | 新一轮 turn 可补齐；不创建 operation |
| 已有持久化预览 | `PREVIEWED` | 绑定 preview operation |
| 等待用户确认 | `WAITING_CONFIRMATION` | 继续使用现有确认入口 |
| 已确认并执行 | `EXECUTING` → `VERIFYING` | 事件使用现有 `EXECUTION_STARTED`/`VERIFICATION_FINISHED` |
| 回读无法证明结果 | `WRITE_RESULT_UNKNOWN` + `WRITE_RESULT_UNKNOWN` | 同一幂等键只能重读验证，不得再次盲写 |
| 版本或预览前置条件不满足 | `CONFLICT` | 不执行旧 preview |
| 其他拒绝/执行错误 | `FAILED` + 结构化 errorCode | 不增加 `REJECTED` 状态 |

状态转换必须在 `AiOrchestratorState` 的 transition 表和单测中显式声明：`DRAFT_VALIDATED + INPUT_REQUIRED → NEEDS_INPUT`；`NEEDS_INPUT` 对当前 Plan 是终态，后续 turn 创建新的 Plan，不在原 Plan 上原地回退。`WRITE_RESULT_UNKNOWN` 只能进入回读重试、`SUCCEEDED`、`CONFLICT` 或 `FAILED`。需求分析路径保持现有状态和值不变；Connector schema 通过版本化 union 和可选新增字段保持滚动兼容，故事字段只在 story projection 中出现。

## 数据流与边界

1. `AiOrchestratorController` 只负责认证、客户端校验和请求映射。story turn request 必须带 `contractVersion=story-workbench/v1`、正数 `nodeId` 和非负 `contextVersion`；不能用 requirement draft 或省略节点定位字段冒充故事 turn。
2. `AiOrchestratorService` 负责会话状态机、持久化、事件和用户确认。
3. `StoryNodeContractResolver` 从 `AiWorkflowContextDTO` 提取节点契约和缺失字段；它是纯函数式可测试组件，不读取数据库。`node.version` 是节点字段写入的乐观锁版本，`saveContract.version` 必须与其一致；顶层 `contextVersion` 是上下文快照版本，`meta.contextVersion` 只是可追踪的快照标识，不能替代数值版本校验。
4. `DefaultAiConnectorPort` 继续调用现有上下文读取、预览、执行和资源读取服务。
5. Connector 的 `storyWorkbench` 继续负责故事字段路径转换和回读比对；Orchestrator 只组装同一 command 的参数，不复制写入逻辑。
6. `pms-cli` 只保存认证信息，conversation、turn、plan 和事件全部从 PMS 读取，支持跨进程继续执行。session 恢复必须从 PMS 的 plan/operation/verification 事实重建结果，不能由 CLI mapper 用当前时间或固定用户名临时拼接确认事实。

## 校验规则

- 会话资源必须是 `story`，故事 ID 必须为正整数；story turn request 的 `nodeId` 必须为正整数，`contextVersion` 必须为非负整数，且不能接受 requirement draft。
- `nodeId`、`contextVersion`、`node.version`、`saveContract.version` 必须与 focused context 的选中节点和版本一致；`meta.contextVersion` 仅用于诊断。
- 草稿证据引用只能来自当前上下文的主资源、可读 relation、已选节点 `story-node:<id>` 或已选组件 `story-node:<id>#component:<key>`。未选中的节点/组件和不可读 relation 必须拒绝。
- `patch` 的 key 必须属于 `saveContract.writableFields ∩ policy.effectiveWritableFields - policy.protectedFields`。
- 资源级 `status`、负责人、日期等受保护字段不得出现在 patch 中；`title` 是否可写只由当前 variant policy 决定，不由 Orchestrator 另行硬编码。
- 必填字段为空时返回 `NEEDS_INPUT`，不产生 preview operation。
- 预览必须使用 `development-item.node.field.update`，参数必须包含 `STORY`、故事 ID、节点 ID、节点版本和 `__components.story-node-workbench` 路径。
- confirm 必须提供幂等键；重复幂等键返回同一个 operation 结果。
- 执行成功后必须同时回读 story detail 和 focused context，并定位到请求中的 story/node/component；只检查 `operation.data` 不算验证。字段 patch 或资源级受保护字段不匹配时返回 `WRITE_RESULT_UNKNOWN`。
- `WRITE_RESULT_UNKNOWN` 下使用相同幂等键只允许重新读取并验证；不同幂等键必须返回冲突，不得再次执行写入。
- Conversation 切换节点时只能使用已结束 Plan 的新 turn；切换不能复用旧节点的 context snapshot、saveContract 或 preview operation。当前版本不新增取消接口。

## 事件

故事流程至少记录（前四个名称沿用现有事件枚举，新增事件必须同步更新枚举和 transition 测试）：

```text
CONTEXT_READ
DRAFT_VALIDATED
INPUT_REQUIRED 或 PLAN_CREATED
PREVIEW_CREATED
WAITING_CONFIRMATION
EXECUTION_STARTED / EXECUTION_FINISHED
VERIFICATION_FINISHED / READBACK_UNKNOWN / CONFLICT
```

事件继续使用 conversation-scoped monotonic `sequenceNo`，通过现有 session cursor 增量读取。

## 验收标准

### 只读和补充信息

- 读取故事节点时返回当前节点、版本、必填字段、可写字段和受保护字段。
- 草稿缺少必填字段时返回 `NEEDS_INPUT`，响应包含字段名和追问，数据库没有 preview operation。
- 引用不可读资源、写入受保护字段、未知字段和旧版本草稿均被拒绝。

### 安全写入

- 完整草稿只能经过 preview → confirm → execute。
- 同一个幂等键重放不会产生第二次写入。
- 新版本上下文会返回冲突，不能使用旧 preview 强行写入。
- 成功写入必须包含 resource/context/patch/protected-fields 四项验证。

### 外部 Agent 使用

- `pms-cli` 返回纯 JSON，不依赖 PMS 前端。
- Claude/Codex 可以在新的 CLI 进程中通过 conversation ID 和 plan ID 继续会话。
- 真实隔离 PMS E2E 覆盖故事节点读取、补充问题、preview、confirm、执行、回读、幂等重放和冲突；测试配置缺失时直接失败，不能通过全量 skip 显示绿色，且至少有一个测试实际执行。

### 会话恢复和兼容性约束

- plan 必须持久化 `previewOperationId`、确认事实、幂等键、状态、verification 和最后一次 operation 结果引用；session 读取时重新装配 operation/verification。
- 相同幂等键的 confirm 必须返回同一 operation 结果；并发 confirm 只有一个可以进入 `EXECUTING`，其余返回已存在结果或明确冲突。
- Java response 的新增 story 字段使用非空序列化策略，需求响应不输出未知字段；Connector 先发布可识别新字段的 schema，再发布 Backend，确保滚动升级期间旧 requirement client 仍可用。
- session 读取返回当前 Conversation 的 latest Plan；如果该 Plan 是 `NEEDS_INPUT` 的前任，后续 turn 产生的新 Plan 通过 `turnId` 和时间顺序成为最新事实，旧 Plan 仍可通过事件记录审计。

