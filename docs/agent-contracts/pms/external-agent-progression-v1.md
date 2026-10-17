# External Agent 节点推进合约 v1

这份合约定义 Claude、Codex、DeepSeek 等外部 Agent 通过 `pms-cli` 补齐需求、专题和故事节点详情的边界。模型、自然语言和子 Agent 协作留在外部；PMS 只提供上下文、字段契约、权限、版本和写入结果。

## 统一流程

```text
pms context <resource> <id> --view focused --format agent
  → 外部 Agent 生成 AgentProgressionDraft
  → scenario progression draft（只读校验）
  → scenario progression plan（字段差异 + 缺失项 + PMS preview）
  → 用户意图明确且 writeDecision=READY
  → scenario progression execute（previewOperationId + 幂等键）
  → get/context 回读与 verification
```

`pms-cli` 不调用模型，也不保存模型 API Key。它只消费外部 Agent 已经生成的结构化草稿，并把 PMS 返回的规则作为最终裁决。

## 草稿结构

```json
{
  "facts": [{"text": "测试报告来自内部记录", "sourceRefs": ["conversation:latest"], "confidence": "high"}],
  "inferences": [],
  "assumptions": [],
  "unresolvedDecisions": [],
  "patch": {
    "testResult": "PASS",
    "buildVersion": "1.0.0",
    "reportUrl": "内部测试记录",
    "residualIssues": []
  },
  "fieldSources": {
    "testResult": {"sourceType": "conversation", "sourceRefs": ["conversation:latest"], "confidence": "high"}
  },
  "requestedCompletion": true,
  "ownerId": 1,
  "startDate": "2026-10-10",
  "endDate": "2026-10-20"
}
```

`patch` 只能包含当前 focused 节点 `saveContract.writableFields` 中的键。状态、标题、负责人、排期和版本属于受保护信息；负责人和排期通过原子完成命令的顶层参数传递。每个拟写入字段都必须有 `fieldSources`，推断来源的高风险结论若置信度不足会返回 `WAITING_CONFIRMATION`。

## 计划结果

`scenario progression plan` 返回：

- `fieldChanges`：字段标签、旧值、新值、来源类型和置信度；
- `missingFields`：完成节点前仍需补齐的必填字段、负责人或排期；
- `preconditions`：PMS preview 返回的任务、版本或权限前置条件；
- `writeDecision`：`READY`、`NEEDS_INPUT`、`WAITING_CONFIRMATION` 或 `CAPABILITY_UNAVAILABLE`。

缺少必填信息时 `preview` 必须为 `null`，Agent 应一次性向用户询问 `missingFields`。只有 `READY` 才能把预览编号交给 execute。

## 写入命令

- `UPDATE_NODE_FIELDS` 只使用 `development-item.node.field.update`，不会推进流程；
- `COMPLETE_NODE` 对需求、专题、故事使用 `development-item.node.complete-with-data`，字段详情、负责人、排期和节点推进在 PMS 一个事务中完成；
- `project` 节点暂不开放该协议，项目节点属于第二期，等待其独立 workbench 的 `saveContract` 和原子完成能力。

execute 必须提供同一个 `previewOperationId`、唯一 `idempotencyKey` 和最新上下文版本。成功结果必须包含操作结果、资源/上下文回读以及 `verification`；版本冲突返回 `CONFLICT`，写入后无法证明结果返回 `WRITE_RESULT_UNKNOWN`，两者都不得盲目重试。

## 最小手工验证

```bash
pms capabilities --format json
pms context requirement 24 --view focused --node-id 387 --format agent
pms scenario progression draft requirement 24 --node-id 387 \
  --intent COMPLETE_NODE --draft-json '<AgentProgressionDraft JSON>' --format agent
pms scenario progression plan requirement 24 --node-id 387 \
  --intent COMPLETE_NODE --execute-mode auto \
  --draft-json '<AgentProgressionDraft JSON>' --format agent
```

看到 `writeDecision=NEEDS_INPUT` 时不要执行；看到 `READY` 后，外部 Agent 应保存 `preview.operationId`，再使用相同预览编号和幂等键执行，并重新读取上下文核验节点是否已推进。
