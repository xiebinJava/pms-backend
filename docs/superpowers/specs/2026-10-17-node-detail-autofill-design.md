# 节点详情自动补全设计

## 目标

让 Claude、Codex 或 DeepSeek 根据用户对话和 PMS 上下文，生成当前流程节点的结构化详情草稿；由 PMS 的字段契约、权限、版本和工作流规则负责校验，再自动保存详情并按用户意图推进节点。

第一期覆盖研发事项的需求、专题、故事。项目节点保留在第二期接入，因为项目节点目前使用独立的 project-node workbench 和命令边界，需要先补齐原子“保存详情并完成”能力。

## 边界

模型属于外部 Agent。PMS 后端和 `pms-cli` 不保存模型 API Key，不调用模型 endpoint，也不把聊天记录转发给模型。外部 Agent 只把结构化草稿交给 `pms-cli`。

PMS 是字段和流程规则的唯一事实来源。上下文读取、草稿校验、预览、执行、回读都必须经过 PMS 合约；禁止通过数据库直写或猜测命令名。

## 用户流程

```text
用户自然语言
  → 外部 Agent 读取自己的聊天上下文
  → pms context 读取当前资源和节点
  → 外部 Agent 生成 NodeDetailDraft
  → pms-cli draft 校验字段、来源、版本和安全边界
  → pms-cli plan 生成字段差异、缺失项、前置条件和风险
  → 必填信息缺失：向用户一次性询问缺失字段，不产生 preview/写入
  → 信息完整：低/中风险按用户明确意图自动 execute
  → PMS 原子保存详情并推进节点
  → 读取资源和上下文回读，报告实际变更
```

## 外部 Agent 草稿

在现有 `AgentProgressionDraft` 上增加可选字段，保持旧客户端兼容：

```json
{
  "facts": [{"text": "测试结果为通过", "sourceRefs": ["conversation:latest"], "confidence": "high"}],
  "inferences": [],
  "assumptions": [],
  "unresolvedDecisions": [],
  "patch": {
    "testResult": "PASS",
    "buildVersion": "1.0.0",
    "testReportLink": "内部测试记录",
    "remainingIssues": "无"
  },
  "fieldSources": {
    "testResult": {"sourceType": "conversation", "sourceRefs": ["conversation:latest"], "confidence": "high"},
    "buildVersion": {"sourceType": "conversation", "sourceRefs": ["conversation:latest"], "confidence": "high"}
  },
  "requestedCompletion": true
}
```

约束：

- `patch` 只能包含当前节点契约声明的 writable 字段。
- 每个写入字段必须有 `fieldSources`，来源只能是 `pms_context`、`conversation` 或 `inference`。
- `inference` 只能作为建议值；不能自动写入高风险或业务结论字段。
- 不允许出现 token、password、secret、cookie、API key 等凭据形态的数据。
- 未提供的字段保留 PMS 原值；草稿不是全量替换。

## PMS 节点详情契约

现有 context 的 `node.fields`、`component.fields` 和 `saveContract` 是基础。第一期把 `saveContract` 增强为可被 Agent 直接消费的结构：

```json
{
  "contractVersion": "node-detail/v1",
  "command": "development-item.node.field.update",
  "itemType": "REQUIREMENT",
  "itemId": 24,
  "nodeId": 101,
  "version": 3,
  "variant": "testing",
  "writableFields": ["testResult", "buildVersion", "testReportLink", "remainingIssues"],
  "fieldContracts": [
    {"key": "testResult", "label": "测试结果", "type": "enum", "required": true, "nullable": false, "options": ["PASS", "FAIL"]},
    {"key": "buildVersion", "label": "构建版本", "type": "string", "required": true, "nullable": false},
    {"key": "testReportLink", "label": "测试报告", "type": "string", "required": false, "nullable": true}
  ],
  "protectedFields": ["status", "ownerId", "startDate", "endDate"],
  "completionSupported": true,
  "completionCommand": "development-item.node.complete-with-data",
  "completionRequiredMetadata": ["ownerId", "startDate", "endDate", "fieldValues"]
}
```

字段契约由后端真实工作流模板和组件生成，不由 Agent 自己定义。`fieldContracts` 只描述可写字段；流程状态、资源标题、节点版本等保护字段必须由 PMS 管理。

## 计划和执行规则

- `draft`：只读最新 context 并验证草稿，不创建操作预览。
- `plan`：校验版本、字段、必填项、负责人、排期、任务前置条件，生成变更差异。
- `execute`：只接受同一份草稿和有效 preview；使用幂等键；执行后必须回读。
- 低/中风险且用户已经表达明确写入意图时使用 `executeMode=auto`，不重复询问确认。
- 高风险操作、推断字段、涉及负责人/排期的未明确值返回 `WAITING_CONFIRMATION` 或 `NEEDS_INPUT`。
- 版本冲突必须重新读取 context、重新生成草稿和 plan，不能复用旧 preview。

## 失败结果

统一返回以下可处理状态：

- `NEEDS_INPUT`：列出缺失字段、字段标签和提问模板，不执行写入。
- `CAPABILITY_UNAVAILABLE`：当前节点没有可写契约或完成能力，不猜测替代命令。
- `CONFLICT`：版本或字段版本过期，返回最新上下文引用。
- `WRITE_RESULT_UNKNOWN`：操作可能已执行但回读失败，保留 operationId，禁止盲目重试。
- `SUCCEEDED`：返回字段差异、节点状态、操作记录和回读验证。

## 非目标

- 本期不建设完整 Orchestrator 会话层。
- 不在 CLI 或 PMS 中增加模型供应商配置。
- 不自动猜测测试结果、验收结论、负责人、排期或外部链接。
- 不改变现有工作流模板和业务校验规则。
