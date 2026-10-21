# AI Orchestrator v1 设计方案

日期：2026-10-15（Asia/Shanghai）

## 目标

在现有 PMS 模块化单体、AI Connector 和业务规则之上，补齐一条可审计、可确认、可回读的 AI 协作闭环：

```text
用户消息 / 外部 Agent 草稿
  → 读取真实上下文
  → 校验意图和草稿来源
  → 生成执行计划
  → 预览变更
  → 等待用户确认
  → 执行既有 PMS 命令
  → 回读并验证结果
  → 返回有序进度事件和最终结果
```

当前 Orchestrator 保留需求分析和故事节点的既有路径，并扩展 `project/topic` 的资源节点会话路径，用于验证“自然语言读取上下文、生成节点草稿、预览、确认、执行和回读”的统一闭环。模型仍由 Claude、Codex、DeepSeek 或其他外部 Agent 持有；后端不保存模型密钥，也不直接调用模型。

## 范围和非目标

范围：会话、轮次、上下文快照、结构化 Agent Draft、预览、确认、执行、事件和回读验证。

非目标：完整聊天 UI、SSE、跨资源事务、多 Agent 调度、模型供应商平台、微服务拆分、复制一套 PMS 业务规则。

## 领域对象和状态

### 对象

- `Conversation`：一个用户围绕一个 PMS 资源进行的协作会话。
- `Turn`：用户消息或 Agent 草稿的一次输入记录。
- `Intent`：按资源和动作映射为 `READ_CONTEXT`、`UPDATE_NODE_FIELDS`、`COMPLETE_NODE`；需求分析保存继续使用既有 `REQUIREMENT_ANALYSIS_SAVE`。
- `ContextSnapshot`：由 PMS 当前上下文接口返回的 JSON、资源版本、当前节点和读取时间。
- `Plan`：把一个已验证意图绑定到具体资源、节点、版本和结构化补丁。
- `Preview`：既有 Connector/PMS 命令生成的变更预览，作为确认依据。
- `Operation`：复用现有 `pms_ai_operation` 的持久化执行记录。
- `OperationEvent`：按序记录上下文读取、预览、执行和验证阶段。
- `Verification`：执行后重新读取资源和上下文，并判断目标补丁是否已生效、受保护字段是否未变。

### Plan 状态（状态与事件分开定义）

```text
RECEIVED
  → CONTEXT_READY
  → DRAFT_VALIDATED
  → PLAN_READY
  → NEEDS_INPUT          （当前 Plan 终态；补充信息后生成新 Plan，不在状态机内回环）
  → PREVIEWED
  → WAITING_CONFIRMATION
  → EXECUTING
  → VERIFYING
  → SUCCEEDED
  → WRITE_RESULT_UNKNOWN （回读无法证明写入结果；只能用原幂等键重新读取验证）
```

终态还包括 `FAILED`、`CONFLICT`、`CANCELLED`。旧版本执行必须进入 `CONFLICT`，不自动刷新、不自动重试。`NEEDS_INPUT` 的多轮续接由服务层合并新一轮 patch 并创建不可变的新 Plan 实现，不能把它画成状态机回环。

### 事件类型

`CONTEXT_READ`、`DRAFT_VALIDATED`、`INPUT_REQUIRED`（NEEDS_INPUT 输出）、`PLAN_CREATED`、`PREVIEW_CREATED`、`WAITING_CONFIRMATION`、`EXECUTION_STARTED`、`EXECUTION_FINISHED`、`VERIFICATION_FINISHED`、`READBACK_UNKNOWN`（回读未证明，进入 WRITE_RESULT_UNKNOWN 时输出）、`FAILED`、`CONFLICT`、`CANCELLED`。

每个会话的事件使用从 1 开始的 `sequence_no`，由数据库唯一约束
`(conversation_id, sequence_no)` 保证顺序不重复；客户端通过会话级
`afterSequence` 轮询，不依赖 SSE。一个会话可以包含多个计划，游标不能按计划重置。

## Agent Draft 契约

需求分析继续使用 `requirement-analysis/v1`；project/topic 的节点会话使用 Connector 的 `resource-node/v1` 草稿契约。两者都由外部 Agent 生成、由 PMS 校验，不能混用字段形状。

`resource-node/v1` 的核心形状为：

```json
{
  "contractVersion": "resource-node/v1",
  "resourceType": "project",
  "intent": "UPDATE_NODE_FIELDS",
  "nodeId": "node-123",
  "nodeVersion": 7,
  "componentKey": "project-basic-info",
  "fieldChanges": [{"fieldKey": "description", "value": "...", "sourceRefs": ["project:42"]}],
  "requestedCompletion": false,
  "facts": [],
  "inferences": [],
  "assumptions": [],
  "unresolvedDecisions": []
}
```

需求分析的既有形状如下：

```json
{
  "contractVersion": "requirement-analysis/v1",
  "draft": {
    "facts": [{"text": "...", "sourceRefs": ["requirement:33"]}],
    "inferences": [{"text": "...", "sourceRefs": ["requirement:33"]}],
    "assumptions": [{"text": "...", "sourceRefs": []}],
    "unresolvedDecisions": [{"text": "...", "sourceRefs": []}],
    "patch": {
      "analysis": "..."
    }
  }
}
```

`draft` 内部字段继续与 Connector 的 `AnalysisDraft` 保持一致；`contractVersion` 是 Orchestrator 请求外层的版本声明，不修改已有 Connector 场景契约。后端必须拒绝：未知字段、不可读的 `sourceRefs`、缺少四个数组、空白 patch、以及修改 `systemId`、`targetType`、`targetId`、`ownerId`、`status`、`startDate`、`endDate` 等受保护字段。所有来源必须来自本次上下文快照，不能由客户端伪造。

## 后端边界

Orchestrator 位于 `com.brad.pms.ai.orchestrator`，只负责会话和流程状态；业务权限、流程节点、版本校验和写入仍由现有 Connector/PMS 命令服务负责。通过 `AiConnectorPort` 隔离调用：

```java
public interface AiConnectorPort {
    AiWorkflowContextDTO readContext(String resourceType, Long resourceId,
                                     ContextReadOptions options);
    AiOperationPreviewDTO preview(CommandPreviewRequest request);
    AiOperationResultDTO execute(OperationExecuteRequest request);
    AiResourceDetailDTO readResource(String resourceType, Long resourceId);
}
```

适配器只能调用现有 `AiWorkflowContextReadService`、`CommandPreviewService`、`CommandExecutionService` 或其现有安全入口，不能直接访问业务 Mapper。

资源与命令映射固定如下：

| 资源 | READ_CONTEXT | UPDATE_NODE_FIELDS | COMPLETE_NODE |
| --- | --- | --- | --- |
| project | focused context + `project_node` capability | `node.field.update`；`project-basic-info` 走 `project.update` | `node.complete-with-data` |
| requirement | focused context + development-item capability | `development-item.node.field.update` | `development-item.node.complete-with-data` |
| topic | focused context + development-item capability | `development-item.node.field.update` | `development-item.node.complete-with-data` |
| story | focused context + story workbench binding | `development-item.node.field.update`（workbench variant） | `development-item.node.complete-with-data` / workbench `completionCommand` |

可写组件选择遵循统一 resolver：恰好一个可写 `saveContract` 时可省略
`componentKey`；多个必须显式指定，否则返回 `CAPABILITY_UNAVAILABLE`；零个只阻止写入意图，读取仍返回 `writeCapable=false`。project/topic 没有声明的 `fieldContracts` 不得由 Orchestrator 伪造，必填信息只从后端 preview preconditions 或当前组件合同得出。

## HTTP 协议（v1）

路由统一使用 `/integration/ai/v1/orchestrator`：

```text
POST /conversations
  body: { resourceType, resourceId }
  result: { conversationId, status }

POST /conversations/{conversationId}/turns
  body: { message, contractVersion, draft, nodeId, nodeVersion, componentKey, contextVersion, requestId }
  result: { turnId, planId, status, contextSnapshot, validationErrors }

POST /plans/{planId}/preview
  result: { planId, operationId, status, preview }

POST /plans/{planId}/confirm
  body: { idempotencyKey }
  result: { planId, operationId, status, verification }

GET /conversations/{conversationId}
GET /conversations/{conversationId}/events?afterSequence=0
```

所有请求使用认证用户 `UserContext.userId()`，不接受 body 中的用户 ID。确认接口必须带幂等键；相同幂等键重复请求返回原 operation 和验证结果。
Session/latestPlan 同时返回 `idempotencyKey`、`confirmedAt` 和 `executedAt`，用于独立
CLI 进程恢复已持久化的确认事实；这些字段不替代 Connector 的回读验证。

Connector 的 `resource-orchestrator resume` 命令只读取上述会话和事件接口，
并在 CLI 侧投影 `nextAction`。它不新增后端状态，也不创建 turn、preview、
operation 或幂等键。`NEEDS_INPUT`、`WAITING_CONFIRMATION`、
`WRITE_RESULT_UNKNOWN`、执行中状态和成功/取消终态分别映射为
`PROVIDE_INPUT`、`CONFIRM`、`RETRY_SAME_IDEMPOTENCY`、`READ_RESULT` 和
`NO_ACTION`；该投影不替代后端状态机或 preview 前置条件，也不单独授权写入。

## 持久化

沿用现有 `pms_ai_operation`，新增四张表：

- `pms_ai_conversation`：`id`、`user_id`、`resource_type`、`resource_id`、`status`、`created_at`、`updated_at`。
- `pms_ai_conversation_turn`：`id`、`conversation_id`、`role`、`message_text`、`draft_json`、`request_id`、`created_at`。
- `pms_ai_plan`：`id`、`conversation_id`、`turn_id`、`intent`、`resource_type`、`resource_id`、`node_id`、`context_version`、`context_snapshot_json`、`draft_json`、`preview_operation_id`、`idempotency_key`、`status`、`verification_json`、`error_code`、`error_message`、时间字段。
- `pms_ai_operation_event`：`id`、`conversation_id`、`plan_id`、`operation_id`、`sequence_no`、`event_type`、`detail_json`、`created_at`；唯一键为 `(conversation_id, sequence_no)`。

第一期不单独建 ContextSnapshot 表；快照作为计划不可变输入保存，便于审计和复现。

## 安全、失败和一致性

1. 读取、预览和执行都经过现有 scope/policy 检查；没有权限时不返回资源正文或可推断的敏感字段。
2. `contextVersion` 是需求/故事兼容的关联字段和快照标签；project/topic 上只作快照标签，不能拿它代替节点并发版本。真正的并发保护由 preview 捕获的版本轴决定：project 的实体版本、节点 `nodeContract.version`、组件/字段版本（若声明）；topic 的流程/节点版本以及后端实际声明的其他版本轴。节点写入的 `nodeVersion` 必须来自当前 `nodeContract`。
3. 计划保存的版本快照必须进入 provenance/digest 绑定；任何版本轴变化返回 `CONFLICT`，不会产生第二次业务写入。
4. 写入成功但回读无法证明时，计划进入 `WRITE_RESULT_UNKNOWN`（事件 `READBACK_UNKNOWN`），不能伪报成功；该状态下只能使用原幂等键重新读取并验证，不得以新幂等键再次写入。回读证明受保护字段被改变或 patch 未生效时同样算未能证明，交由用户处置或明确失败，不把执行响应直接当成功。
5. 事件写入和状态推进在同一事务内完成；外部命令执行在独立的 REQUIRES_NEW 事务中落盘 operation 结果（保证崩溃后可从持久化事实恢复），幂等键预留与业务写入回滚在一起。
6. 模型配置不进入 PMS Backend 或生产 CLI；外部 Agent 只提交结构化 Draft，后端只验证和编排。

## 验收标准

- 一条有效 Draft 能从 turn 走到 `WAITING_CONFIRMATION`，预览展示当前节点、版本轴和变更；project 的 `project-basic-info` 仍走 `project.update`，不伪装成节点字段写入。
- 用户确认后执行成功，回读内容包含目标 patch，当前节点和受保护字段保持不变。
- 相同幂等键重放不产生第二次业务效果。
- 使用旧版本和新幂等键返回 `409 CONFLICT`，不产生第二次业务效果。
- 未知来源引用、受保护字段、越权资源、缺少必需字段均在执行前拒绝。
- 事件按序可查询；刷新或轮询不会丢失阶段状态。
- 现有 Connector CLI 和 PMS 业务页面行为保持兼容。

## 后续扩展条件

只有上述垂直切片通过真实隔离验收，并完成 10—20 条自然语言影子用例和人工质量基线，才进入前端聊天工作台、SSE、跨对象计划和持续跟踪。权限批量加载、UI DTO 与领域读模型解耦、旧上下文清理属于后续独立任务，不混入本次闭环。
