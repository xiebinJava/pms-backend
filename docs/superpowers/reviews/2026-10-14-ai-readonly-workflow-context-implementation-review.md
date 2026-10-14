# AI 只读上下文：实施评审记录

本文件记录实施过程中每个 task 的自我评审（对照 plan 步骤与 spec 要求）。设计与静态评审见
2026-10-14-ai-readonly-workflow-context-review.md。

## Task 1 评审：纯读领域入口、严格绑定与正确任务计数

提交：2692fc6 feat: add side-effect-free workflow reads（2026-10-14）。

### 测试证据

- 计划 Step 2 命令（六个新测试类 + 两个既有测试类）：Tests run 38, Failures 0, Errors 0。
- 计划 Step 4 回归集合（NodeServiceOwnerDefaultsTest、NodeServiceCompleteTest、
  DevelopmentItemWorkflowServiceNodeEditTest、DevelopmentItemWorkflowServiceFieldTest、
  WorkflowComponentBindingServiceTest、AiConnectorQueryServiceTest，另含 Rollback/Receiving/DesignReview）：
  全部通过，BUILD SUCCESS。
- 全量 `mvn test`：1060 个用例中 67 个失败，全部为 "Failed to initialize pool: Previous attempts to
  find a Docker environment failed"（testcontainers 无法连接 Docker）。已在基线提交 c93102f 的
  独立 worktree 复现同样失败，证明与本次改动无关。隔离容器验收属 Task 6 范围。
- git diff --check 干净；提交仅包含 Task 1 文件（19 个生产/测试文件），未触碰 opencode.json 与无关改动。

### 对照 Spec 的逐项核对

| 要求 | 结果 |
| --- | --- |
| listReadOnly 无补 owner/激活/成员，真实节点装配 | 通过：NodeService.list 只保留原副作用；listReadOnly 仅 requireProjectReadable + selectList + 严格模板解析；测试断言 updateById/insert/ensureMember 零调用 |
| 旧项目仅按实际历史挂载覆盖（R06） | 通过：applyLegacyTopicBinding 传 configuredNodeKey=null，overlay 对 null 不追加默认宿主；测试验证未调用 resolveTopicSourceProjectNodeKey；默认挂载变更后结果不变 |
| 渲染定义路径严格（pinned） | 通过：listReadOnly 与 requireNodeComponentForContext/findNodeWithComponentForContext 走 getDefinitionForContext；verify 未调用 getNodeDefinition/getDefinition |
| getDefinitionForContext：null→compatibility、缺失/损坏→409、DB 异常传播 | 通过：WorkflowTemplateContextReadTest 断言 code=409；selectById 异常不被捕获 |
| detailIfInitialized 不创建、归属 404、真实 workflow=5/template=2 分开 | 通过：selectByItem 只读；mismatch projectId 抛 404；workflowVersion=5 与 templateVersionId=88 分断言 |
| 任务统计：平面行、一父两子 3/1/2、父删子存活仍计数 | 通过：TaskServiceReadSummaryTest + DevelopmentItemWorkflowServiceReadOnlyTest；summary 过滤 deleted 行，UI 树根节点不会把孤儿子任务当 root |
| detailForContext：实际实例摘要、不解析默认模板（R07） | 通过：verify 从未调用 resolveDefaultForProcessType；无实例时 NOT_CONFIGURED；REJECTED 需求保留真实状态 |
| 研发组件 KEY 校验 | 通过：detailIfInitialized 在 strictDefinition 下校验每个实际节点 key，缺失即 409（"流程绑定已损坏"） |

### 评审发现的偏差与处理

1. 三参 context 未新增 Long 重载（R13 前移检查）——Task 1 未涉及签名改动，符合。
2. NodeServiceTO DTO 追加 componentConfigs 按 spec 完成；page 路径 toDTO 一并填充（additive，不改 page 行为）。
3. 计划 Step 1 中 "错实例/节点归属 404" 通过 workflow.projectId 不匹配表达；sourceNodeId 归属校验同分支覆盖。
4. TaskService.summarizeByNode 使用 requireProjectReadable+requireNode 做归属校验；生产 mapper 的
   @TableLogic 自动过滤已删除行，故测试以"不返回已删除行"的方式模拟生产行为。
5. plan 文档 Task 1 五个步骤已勾选，状态行更新测试证据与当前剩余任务。

### 遗留观察（转入后续 task）

- requirementResource 仍走旧 detail()（计划内：Task 5 纠正）。
- NodeTaskSummaryDTO 目前仅统计；task 明细/依赖按 spec 推迟。
- 全量集成用例需要 Docker 环境；Task 6 将以 PMS_CONTEXT_READ_ISOLATED gate 隔离执行。
