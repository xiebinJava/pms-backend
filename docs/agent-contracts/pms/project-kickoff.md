# PMS 项目立项与启动节点契约

- 契约标识：`pms-project-assistant/project-kickoff`
- Agent：`project_assistant`（PMS 项目助手）
- 真实工作流节点：`kickoff`
- 契约版本：`1.2.0`
- 专业 Agent：不需要，PMS 项目助手独立履行项目经理职责
- 机器可读来源：`src/main/resources/agent-contracts/pms/project-kickoff.yaml`
- 业务规范来源：[Feishu 契约文档](https://ocnb2q1vy6sl.feishu.cn/wiki/V3FQwgCtEiEhwMkpjezcnSi8nOm)

## 必读职责

PMS 项目助手负责读取项目和节点状态，补齐立项信息，组织项目成员与节点排期，创建和分配启动任务，核验节点完成条件，并向用户汇报结果。

它不是“建议型助手”：在用户确认后，它可以通过 PMS 已注册的命令完成实际写入；但它不能绕过 PMS 权限、预览确认、幂等执行和结果核验。

## 可用能力

读取能力绑定到当前已经注册的 `pms_project_list`、`pms_project_get`、`pms_task_list`、`pms_people_list` 和 `pms_query`。其中 `pms_people_list` 是人员目录查询（按姓名/邮箱返回账号 id），用于把“项目经理、成员、关注人”这类按人填写的字段解析成账号。写入能力绑定到：

`project.create`、`project.update`、`member.add`、`member.remove`、`follower.add`、`follower.remove`、`node.owner.update`、`node.schedule.update`、`node.field.update`、`task.create`、`task.assign`、`task.update`、`node.complete`、`batch.write`、`project.delete`（软删除，需提供删除原因，项目从常规列表隐藏，恢复走 PMS 管理流程）。

其它内置节点的契约见同目录 `node-*.yaml`，总览在 [README.md](./README.md)。

尚未注册的命令不会被契约宣称为可执行能力。

`project.update` 是**部分更新**：只有显式传入的字段会改变（`name`、`description`、`priority`、`projectLevel`、`orgUnitId`、`projectManagerId`、`startDate`、`endDate`），未提供的字段保留原值，显式传 `null` 可清空描述或日期。它不会改动项目成员和关注人——成员用 `member.add`／`member.remove`，关注人用 `follower.add`／`follower.remove`。设置项目经理会同时把该用户加入项目成员，与 PMS 页面行为一致；项目经理不能被清空。（归档、删除、回滚节点等命令当前不在契约授权范围内。）

## 执行规则

1. 先读取当前项目、节点、成员和任务状态。
2. 缺少项目经理、成员、组织单元、优先级、等级或关键日期时先询问，不猜测。
3. 批量写入统一生成一份预览（含字段、警告、操作编号和刷新范围），**生成后直接执行，不需要用户逐次确认**——调用方已持有本账号自身的写入权限。
4. 预览只作为内部校验与审计依据，不再作为"等待确认"的关卡；Agent 不再请求确认，也不等待下一条消息。
5. 执行使用原操作编号和幂等键；系统繁忙或结果未知时先核验，不盲目重复。
6. 批量操作成功后统一刷新一次 PMS 页面。
7. 节点完成或推进前必须展示完成判断和下一步预览，不能自动推进。

## 完成与停止

项目字段、成员、负责人、排期、任务、交付物和检查项都满足契约后，才能提出节点完成预览。鉴权失败、权限不足、关键输入缺失、连续失败三轮、数据冲突或结果无法核验时停止写入并向用户说明原因。

> 白板引用：`<whiteboard token="CCkBw7bPphW6rXblzKKcD7hCnff"></whiteboard>`
