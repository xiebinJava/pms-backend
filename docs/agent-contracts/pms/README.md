# PMS 项目助手节点契约

每个内置工作流节点都有自己的 Agent 契约（数据文件，位于
`src/main/resources/agent-contracts/pms/`）。契约决定"这个节点上助手可以做什么"，
所以给节点扩能力只需要改 YAML，不需要改 DSH 插件。

| 节点 | 契约文件 | 可写工作台 |
| --- | --- | --- |
| 项目立项与启动（kickoff） | `project-kickoff.yaml` | 项目字段、成员、关注人、节点负责人与排期、任务、自定义字段 |
| 需求澄清与范围基线（requirement） | `node-requirement.yaml` | `requirement-scope` |
| 方案设计、评审与决策（design） | `node-design.yaml` | `solution-design` |
| 计划、资源与风险基线（plan） | `node-plan.yaml` | `plan-resource-risk` |
| 开发测试与项目控制（develop） | `node-develop.yaml` | `development-control` |
| 业务验收与缺陷闭环（acceptance） | `node-acceptance.yaml` | `business-acceptance` |
| 发布决策与运营交接（release） | `node-release.yaml` | `release-handover` |
| 价值验证与项目复盘（review） | `node-review.yaml` | `value-review` |
| 知识沉淀与标准改进（knowledge） | `node-knowledge.yaml` | `knowledge-standard` |

## 通用能力

- `node.field.update`：写任意节点工作台的字段。参数是 `workbench` + `fields`（只传要改的字段），
  后端会把补丁合并到当前文档上再保存，未提供的字段保留原值；只能写在当前节点真正运行的工作台上。
- `batch.write`：一次预览、一次执行多条写入（批量创建任务、批量加成员、批量改字段），最多 20 条，
  任一条失败整批回滚。
- `pms_people_list`（读）：按姓名/用户名/邮箱查账号，用于把"项目经理、成员、关注人、节点负责人"解析成 id。

工作台字段的中文名在 `src/main/resources/agent-labels/pms/node-field-labels.yaml`（同样是数据文件，
新增字段时补一行；`NodeWorkbenchLabelsTest` 会强制这条规则）。

## 契约约束（由 `PmsAgentContractLoader` 校验）

- `writeCommands` 里的命令必须是 PMS 已注册命令；`confirmationPolicies` 必须覆盖每条命令，取值只允许
  `preview-and-confirm` 或 `preview-then-execute`。
- `readToolBindings` 只能绑定 DSH 已发布并受 PMS capabilities 授权的读取工具。
- `workflowNodeKeys` 必须覆盖节点真实 key；`PmsAgentContractSetTest` 会检查每个内置节点都有且只有一个契约。
