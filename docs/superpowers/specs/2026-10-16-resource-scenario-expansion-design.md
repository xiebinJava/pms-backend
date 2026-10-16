# 需求、专题、项目 Agent 场景扩展设计

## 状态

Ready for implementation。本文只扩展 Claude/Codex 通过 `pms-cli` 操作需求、专题、项目的无会话场景，不建设完整 Orchestrator 会话层。

## 目标

让外部 Agent 可以对需求、专题、项目使用一致的安全闭环：

```text
自然语言
→ Agent 读取资源上下文
→ 生成结构化草稿
→ PMS/CLI 校验能力、版本、字段和权限
→ plan 生成预览
→ 用户明确确认
→ execute 使用预览和幂等键写入
→ 资源与流程上下文回读
→ 返回验证结果和刷新范围
```

第一版不要求 PMS 内部聊天，也不要求服务端保存 Conversation、Turn 或长期会话状态。每次调用都必须携带完整资源定位、上下文版本和草稿/参数；Agent 可以通过保存的 `previewOperationId` 和 `idempotencyKey` 在新进程中继续执行。

## 当前基础

- Backend 已有能力目录、资源上下文、操作预览、幂等执行、资源详情和流程上下文接口。
- Connector 已有 `project-create`、`requirement-analysis`、`resource-write` 和故事节点工作台场景。
- 需求已有分析保存和执行对象命令；专题已有创建、更新、项目关联命令；项目已有创建、更新以及节点工作台命令。
- 故事节点 Orchestrator 的字段契约和安全写入链路作为本次扩展的参考，但不复制其会话层。

## 范围

### 本次交付

1. 定义统一的 `resource-scenario/v1` 无会话协议。
2. 增加可注册的资源场景适配器，统一读取 context、校验草稿、生成 preview、执行和 readback。
3. 支持需求核心场景：需求基本信息更新、需求接收/分析保存、执行对象关联变更、需求节点工作台字段更新。
4. 支持专题核心场景：专题创建、基本信息更新、项目关联、专题节点工作台字段更新。
5. 支持项目核心场景：项目创建、基本信息更新、项目节点工作台字段更新；项目归档、删除、流程回退和完成动作留到后续动作扩展。
6. 为每个场景提供 CLI 的 `plan` / `execute` JSON 输出和契约测试。
7. 为三类资源增加隔离真实 PMS E2E：plan 不写入，execute 必须绑定 preview 和幂等键，成功后必须回读资源和 context。

### 明确不做

- 不新增或扩展 `Conversation`、`Turn`、`Plan`、事件流和 session 恢复 API。
- 不实现 PMS 内部 AI 聊天工作台。
- 不把模型调用放入 Backend；Claude、Codex 或 Connector 外部 Provider 仍负责生成草稿。
- 不绕过已有 capability、权限、saveContract、preview、confirm/execute、版本校验和幂等链路。
- 不在本次支持项目删除、项目归档、流程回退、节点完成等高风险动作的自然语言编排；这些动作先保留底层命令，后续单独评估。
- 不修改前端业务流程；前端只需继续消费现有资源和刷新范围。

## 统一协议

### 场景输入

```json
{
  "contractVersion": "resource-scenario/v1",
  "resourceType": "requirement|topic|project",
  "resourceId": 33,
  "operation": "requirement.analysis.save",
  "nodeId": 301,
  "contextVersion": "7",
  "arguments": {"patch": {"userScenarios": "..."}},
  "draft": {
    "facts": [{"text": "...", "sourceRefs": ["requirement:33"]}],
    "inferences": [],
    "assumptions": [],
    "unresolvedDecisions": [],
    "patch": {"userScenarios": "..."}
  }
}
```

`arguments` 是已确定的命令参数；`draft` 是 Agent 产出的可解释草稿。CLI 可以只接受其中一种，但进入 preview 前必须得到同一份规范化参数。服务端能力目录和 context 是字段、动作、版本和权限的权威来源，Agent 不能自行声明可写字段。

### plan 响应

```json
{
  "scenario": "resource-scenario",
  "phase": "plan",
  "contractVersion": "resource-scenario/v1",
  "resourceType": "topic",
  "resourceId": 12,
  "operation": "topic.update",
  "context": {
    "id": "topic:12",
    "version": "4",
    "entityVersion": 9,
    "selectedNodeId": null
  },
  "normalizedArguments": {"topicId": 12, "title": "新的专题名称"},
  "capability": {"name": "topic.update", "inputSchema": {}},
  "preview": {"operationId": "op-123", "changes": [], "expiresAt": "..."},
  "requiresConfirmation": true,
  "warnings": []
}
```

### execute 响应

```json
{
  "scenario": "resource-scenario",
  "phase": "execute",
  "contractVersion": "resource-scenario/v1",
  "resourceType": "topic",
  "resourceId": 12,
  "previewOperationId": "op-123",
  "operation": {"operationId": "op-123", "status": "SUCCEEDED"},
  "readback": {"resource": {}, "context": {}},
  "verification": {
    "resourceReadback": true,
    "contextReadback": true,
    "patchApplied": true,
    "protectedFieldsUnchanged": true
  },
  "status": "SUCCEEDED",
  "warnings": [],
  "refreshScopes": ["topic-detail"]
}
```

### 安全不变量

- `plan` 只读并创建可追踪的预览，不执行写入。
- `execute` 必须同时提供已持久化的 `previewOperationId` 和非空 `idempotencyKey`；不允许在 execute 阶段重新接受任意 arguments。
- 预览中的资源、节点、上下文版本和命令必须与执行请求一致；新幂等键遇到过期上下文必须返回冲突。
- execute 成功后必须回读资源和 workflow context；任一回读失败返回 `WRITE_RESULT_UNKNOWN`，不能报告为成功。
- 重放相同幂等键只能返回原 operation 结果；不得创建第二次写入。
- 任何字段白名单、受保护字段和引用资源可读性都由 Backend capability/context 决定。

## 场景矩阵

| 资源 | v1 支持 | 主要上下文 | 写入命令 |
| --- | --- | --- | --- |
| 需求 | 基本信息、接收分析、执行对象、节点工作台字段 | `requirement` focused/legacy context、需求详情 | `requirement.update`、`requirement.analysis.save`、`requirement.execution-target.*`、`development-item.node.field.update` |
| 专题 | 创建、基本信息、项目关联、节点工作台字段 | `topic` context、专题详情、项目关系 | `topic.create`、`topic.update`、`topic.project.link`、`development-item.node.field.update` |
| 项目 | 创建、基本信息、节点工作台字段 | `project` context、项目详情、流程节点 | `project.create`、`project.update`、`node.field.update` |

项目归档/删除、节点完成/回退和跨资源批量写入不属于 v1 的自然语言场景，但其底层 capability 不删除。

## 兼容性

- 保留现有 `project-create`、`requirement-analysis`、`resource-write` 和 `story-workbench` CLI 命令；新协议先以新命令或显式路由接入，不破坏旧脚本。
- Connector 先发布 `resource-scenario/v1` schema 和 CLI 解析，再发布 Backend 端需要的上下文/能力字段。
- 不新增数据库 migration；场景状态不持久化。若现有 preview/operation 字段不足以表达验证结果，优先在 Connector 结果 envelope 中补充，不把临时场景状态塞入业务表。

## 验收标准

- 三类资源各有至少一个真实可运行的 plan → execute 场景。
- 缺失资源、无权限资源、未知字段、受保护字段、旧 contextVersion、缺 preview 或缺幂等键均在写入前失败。
- plan 阶段数据库没有业务写入；execute 成功后资源和 context 回读通过。
- 相同幂等键重放不产生第二次业务变更。
- 机器可读 JSON 包含 contractVersion、previewOperationId、operation、verification、warnings 和 refreshScopes。
- 隔离真实 PMS E2E 覆盖 requirement、topic、project 各一条主流程；配置缺失时测试失败或明确未执行，不以全量 skip 冒充通过。
