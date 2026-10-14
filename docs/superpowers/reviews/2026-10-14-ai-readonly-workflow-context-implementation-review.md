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

## Task 2 评审：合同、加载状态、选择与流程状态解析

提交：064e8dc feat: define workflow context read contract（2026-10-14）。

### 测试证据

- 计划 Step 2 命令：AiWorkflowContextDTOTest(6) + AiContextNodeSelectorTest(7) + AiContextWorkflowStateResolverTest(16) = 29 项全绿。
- Step 4 回归：AiConnectorQueryServiceTest、AiConnectorControllerTest 通过；旧 DTO 构造器与序列化字段稳定（record 新增字段仅追加，旧 accessor 名称记录 JSON 输出不变）。
- 修复过程：resolver 补"未知非空 terminalStatus → INCONSISTENT（保留事实）"；测试中"终态+全部完成无 active"按 Spec 属无冲突，修正期望为 REJECTED。

### 对照 Spec 核对

| 要求 | 结果 |
| --- | --- |
| 旧顶层6/Node8/Component4 构造器保留，新字段 optional | 通过：record 追加 auxiliary 构造器，测试断言新字段 null |
| 唯一 active→current=selected=301；历史显式 selected；无 active 不取首条；多 active 均 null+AMBIGUOUS | 通过：AiContextNodeSelectorTest 覆盖，含显式 nodeId 不存在→404 |
| 状态表参数化 | 通过：NOT_CONFIGURED（含项目已终止/完成无实例保留 entity 终态+INCONSISTENT 警告）、研发有实例无节点、全0/全2、唯一 active、部分完成无 active、status3 无终态、终态+active、REJECTED（实例/需求）、TERMINATED、项目完成冲突、未知状态，全部命中 |
| 正常终态不报 NO_ACTIVE_NODE | 通过：completed/terminated/rejected warnings 为空 |
| 专题/story DONE/NOT_STARTED 不当流程终态 | 通过：type 语义仅 requirement/project 参与终态判定 |
| ContextMeta/Warning/Relation 命名与字段 | 通过：按 Spec 原名（permissionsScope、referencesScope、fieldValuesScope、componentContentScope 等） |
| ContextReadOptions 归一化 legacy；非法 view/nodeId/componentKey 400 | 通过：compact 构造器校验；legacy+componentKey 拒绝 |

### 评审发现的偏差与处理

1. taskSummary 类型复用 NodeTaskSummaryDTO（三个 long，形状与 Spec 的 TaskSummary 一致，避免重复 record）；CLI schema 对应字段不受影响。
2. selector 的 NO_ACTIVE_NODE 警告在零 active 时总是产生，正常终态下的过滤由 reader（Task 5）联合 resolver 结果执行——与"正常终态不产生警告"的最终合同一致，此过滤在 Task 5 实现并测试。
3. 计划文件曾在 Task 1 勾选时被脚本截断（Task 2-9 内容丢失并随 8903d71 提交），已在 9147bb0 用会话保存的原始全文恢复并核对 231 行完整、勾选仅为 Task 1/2。
4. Task 2 计划文件勾选与状态行更新完成。

### 遗留观察（转入后续 task）

- warning 的 reader 侧过滤（正常终态去掉 NO_ACTIVE_NODE）在 Task 5 的 AiWorkflowContextReadService 中落地。
- 状态表输入的项目整数 status→ACTIVE/COMPLETED/TERMINATED 映射将在 Task 5 reader 调用 resolver 前完成（本 task 已用字符串枚举直接测试）。

## Task 3 评审：持久化快照基础与第一批项目组件

提交：90d2dc1 feat: expose persisted workbench read snapshots（2026-10-14）。

### 测试证据

- AiProjectComponentReaderTest(7) + NodeRequirementScopeServiceTest(11，含 3 个新快照用例) 全绿；
  红灯阶段证明：getReadSnapshot 未实现时编译失败（预期红灯）。

### 对照 Spec 核对

| 要求 | 结果 |
| --- | --- |
| PersistedContentVersion/WorkbenchReadSnapshot 精确形状 | 通过：record (persisted, version)/(display, hasPersistedContent, parts)，无 ensure/save 行为 |
| 未保存 EMPTY/content=null/versions.content=null；version=0 → LOADED/0 | 通过：baselineVersion 0 保持真实值，测试断言 PersistedContentVersion(true,0) |
| 子内容存在但无根记录 → LOADED（R04 前移） | 通过：requirementMapper 有行且 baseline=null → hasPersistedContent=true、content 版本 absent(false,null) |
| requireNodeComponentForContext（Task 1 严格规则） | 通过：snapshot 校验走 ForContext；verify 从未调用旧 requireNodeComponent |
| 服务 DB 错误不得转为 EMPTY | 通过：reader 抛 BusinessException("数据库异常") 原样传播 |
| 20,000 字符/多行中文/emoji 原样保留 | 通过：requirement 描述 1200 段重复中文 + ✅🚀 完整出现在 content 字符串中 |
| project-basic-info 白名单 + entityVersion | 通过：content 仅白名单键（无 createdByAvatar/workflowTemplateVersionId），versions.content=项目真实 version |
| componentKey 精确；null 全组件；非附着组件 404 | 通过；未支持组件 UNSUPPORTED + config 可读 |
| 领域快照不直接读 mapper（复用既有纯查询） | 通过：getReadSnapshot 复用 findBaseline/toDTO，无新 mapper 依赖 |

###偏差与处理

1. content 中剥离了 canEdit 与需求行的 taskCount/completedTaskCount（展示聚合/UI 标志），正文事实原样保留。
2. 生产 ObjectMapper 由 Boot 自动注册 JavaTimeModule；测试 mapper 手动注册以对齐序列化行为。
3. versions 值允许 null（内容已保存但版本未知），使用 HashMap 构造，避免 Map.of 拒绝 null。

### 遗留观察

- 其余 7 个领域服务的 getReadSnapshot 属 Task 8；AiProjectComponentReader 当前对未知组件返回 UNSUPPORTED（占位由 Task 8 替换）。
- referenced 内容的权限投影在 Task 4/5 接入。
