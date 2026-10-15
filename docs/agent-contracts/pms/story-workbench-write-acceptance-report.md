# Story Workbench 写入闭环验收报告

日期：2026-10-15

## 验收对象

- 临时故事：`story:35`
- 标题：`AI-E2E-Story-20261015-151221`
- 流程模板：`story-management`，模板版本 `49 / 1`
- 当前节点：`390 / 故事写卡`
- 初始节点版本：`0`
- 工作台变体：`writing`

## 验收结果

1. `draft` 成功读取上下文并校验 `saveContract`，没有调用 preview 或 execute。
2. `plan` 成功生成 `development-item.node.field.update` 预览，预览中的字段路径为 `__components.story-node-workbench`。
3. 首次 `execute` 成功。
   - operation：`ed8c30bd-9078-48fc-9e6a-e99f4653eba5`
   - 回读 `resourceReadback=true`
   - 回读 `contextReadback=true`
   - `patchApplied=true`
   - `protectedFieldsUnchanged=true`
4. 使用同一幂等键和旧版本 `0` 重放，返回同一个 operation ID，状态为 `SUCCEEDED`，没有产生第二次业务效果。
5. 使用新幂等键和旧版本 `0` 提交，返回 `CONFLICT`，没有产生第二次业务写入。
6. 最终 focused context 显示节点版本为 `1`，三个目标字段均只保存了一份预期内容。

## 发现并修复的问题

Connector 场景门面原先在预览前强制要求当前版本等于请求版本，导致后端虽然支持幂等重放，场景层却无法到达执行端。现在只有显式幂等键存在时才允许进入旧版本重放路径；预览冲突只会尝试一次同幂等键执行，网络错误和其他异常不会自动重试。

## 清理说明

当前能力目录声明了 `story.create`、`story.update` 等操作，但没有 `story.delete`。按照 PMS skill 的约束，不猜测未声明的删除接口，也不通过数据库清理。因此临时故事保留，标题带有唯一 `AI-E2E` 标记，后续可以由用户在页面中手动删除或继续作为验收样本。
