# Story Workbench 写入闭环实施计划

## 目标

让外部 AI 生成的故事节点草稿，经过真实上下文校验、预览、显式确认、执行和回读验证后，安全写入 `story-node-workbench`。

首个切片覆盖已有的 `writing`、`iteration`、`development`、`acceptance`、`release`、`launch` 六种工作台变体，不新增业务写入规则；所有字段和版本以 PMS 返回的 `saveContract` 为准。

## 任务边界

1. 固化 `story-workbench/v1` 输入和结果契约，并覆盖非法字段、版本不一致和空草稿。
2. 在 Connector 内实现 `readContext → validate → preview → approve → execute → readback` 场景门面。
3. 给 pms-cli 增加 `scenario story-workbench draft|plan|execute`，显式 `execute` 作为用户确认。
4. 用 fake client 做完整单测，再用独立故事做一次真实环境只读/预览验收；真实写入需要单独确认。

## 不做的事

- 不在 Connector 内调用模型。
- 不绕过 PMS operation、版本校验或幂等键。
- 不修改已完成节点；真实验收优先使用临时故事或用户明确指定的可写节点。
- 不在本切片新增前端聊天页面。

## Review 门槛

- 草稿字段不在 `saveContract.writableFields` 中时，preview 不得被调用。
- `saveContract` 的 `itemId/nodeId/version/variant` 与请求不一致时，preview 不得被调用。
- 执行前必须已有 preview；拒绝确认不得调用 execute。
- 成功执行后必须回读 focused context，并验证写入字段和 protected resource fields。
- 回读失败时保留原始操作结果，但返回警告，不能伪装成已验证成功。

## 本轮实施记录

- [x] `story-workbench/v1` 输入、结果和回读验证契约。
- [x] `saveContract` 驱动的字段白名单、节点版本和组件路径校验。
- [x] `draft → plan → approve → execute → readback` Connector 场景门面。
- [x] `pms-cli scenario story-workbench draft|plan|execute` 命令。
- [x] 单元/契约测试、typecheck、CLI 构建和真实 PMS draft/plan 验收。
- [x] 使用独立临时故事做真实 `execute` 写入验收；删除未执行，因为当前能力目录没有 `story.delete`，临时数据保留唯一 E2E 标记并记录在验收报告中。
