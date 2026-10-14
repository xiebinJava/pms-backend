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

## Task 4 评审：主对象 READ 与全响应引用投影

提交：f7764a3 feat: filter references across AI read responses（2026-10-14）。

### 测试证据

- AiContextReadPolicyTest(12) + AiContextReferenceProjectorTest(8) 全绿；
  回归 RequirementExecutionTargetReadServiceTest(6)、DevelopmentItemPermissionServiceTest(2) 通过。
- 红灯阶段两测试类缺失符号编译失败（预期）；实现期间修复 idValue 对 JsonNode TextNode 的解析（toString 带引号导致引用未被识别）。

### 对照 Spec 核对

| 要求 | 结果 |
| --- | --- |
| requireRead：身份（无 UserContext 拒绝）+ requirement→REQUIREMENT_READ、其余→PROJECT_READ；write 不继承 read | 通过：未认证 403、requirement 且仅有 project:read 403、write 场景即无 read 权限 403 |
| canReadReference：模块 READ 先查，type ∈ {project,requirement,topic,story,iteration_plan}；未知类型不猜 | 通过：模块拒绝时连存在性查询都不做（verify mapper 零调用） |
| DB 异常传播，仅 403/404 事实可省略 | 通过：mapper 抛 IllegalStateException 原样传播 |
| executionTarget 不可读 → 整项省略（无 targetId/navigationId/title 残留）；可读不变 | 通过 |
| sourceRequirements 逐项过滤、旧 sourceRequirement 不可读省略、过滤后无计数泄漏 | 通过 |
| legacy __components 未知组件不得旁路（整项移除）；已知组件内 topicId/baseTopicId/projectId(/targetId) 不可读移除 | 通过 |
| 普通正文（人名/工单数字）不改写；bound 标量（owner id）保留 | 通过 |
| project 范围明细 (scopeItems/requirements=NodeRequirementDTO) 不误套需求模块权限 | 通过：projectResourceDetails 对 project 类型不把这些键当 RequirementsRefs |
| 输入不修改、调用局部无跨请求缓存 | 通过：projected 为新 map/树；same-input 两次调用=mapper 两次（无单例缓存） |

### 偏差与处理

1. topic/story/iteration_plan 引用归属通过其 projectId 的 canReadProject（选择器语义：父项目可读才可见）复核。
2. projector 中 KNOWN_COMPONENTS 清单先用 8 项项目工作台键之外的 6 项研发键；8 个项目组件（如 solution-design）的键在 Task 8 扩展。
3. relations 的装配与裁剪由 Task 5 reader 调 canReadReference 完成，projector 不重复承担。

### 遗留观察

- R01 的实际接线（get/details 路径替换）在 Task 5 完成后才会改变外部行为。

## Task 5 评审：项目/需求纵向切片、HTTP 门面、聚焦投影与预算

提交：aabccc3 feat: add focused readonly workflow context slice（2026-10-14）。

### 测试证据

- Step 2 集合：AiProjectContextReaderTest(6)+AiDevelopmentContextReaderTest(3)+AiDevelopmentComponentReaderTest(6)
  +AiWorkflowContextReadServiceTest(4)+AiContextResponseBudgetTest(4)+QueryService(6)+Capability(1)+Controller(2)
  +RoutePolicy(5)+DynamicWorkflowCapability(1)=58 项全绿；./scripts/validate-openapi.sh 通过；git diff --check 干净。
- 回归适配：AiConnectorQueryService 上下文改走纯读门面，原有模板拼装测试改为门面委托断言；
  DynamicWorkflowCapabilityTest 用新的三依赖构造器并通过 readService 契约断言。

### 对照 Spec 核对

| 要求 | 结果 |
| --- | --- |
| 实际节点 2/1/0 及历史选择；project reader 全用真实节点 | 通过：positions/current/id 全部真实 |
| 真实 entity/workflow/template/node 版本分开 | 通过：context.version=当前节点版本；entityVersion=需求实体版本；meta.workflowInstanceVersion=实例版本；templateVersionId/No 保留 |
| legacy 保留 __components/真实 fieldValues；focused 去重、历史 NOT_REQUESTED | 通过：双视图对比断言 |
| focus component 筛选唯一正文来源 | 通过：selected 仅由 componentReader 装配，非 selected NOT_REQUESTED |
| 执行目标 R01：不可读 → LOADED+content={}（不伪称未绑定、无 id/类型/名称），可读 → 事实无 id | 通过：AiDevelopmentComponentReaderTest 4 场景（含 entityVersion 与 node.version 不同） |
| 预算 524288 边界（422+CONTEXT_TOO_LARGE，多字节 UTF-8） | 通过；legacy 永不预算 |
| 门面 REPEATABLE_READ readOnly 事务 + options 归一化 | 通过（@Transactional 注解；注解≠无写证明，Task 6 验证） |
| 委托路由补 GET resources 四类（不放宽）| 通过：iteration_plan/DELETE 被拒 |
| 两个 feature 默认 false | 通过：capabilities wire 包含两键 false |
| 需求流程摘要纠正（R07） | 通过：requirement context 走 detailForContext；无实例 NOT_CONFIGURED |

### 已执行的关键实现决策

- AiWorkflowContextDTO.record 上下文 meta 由 AiContextMetaFactory 统一装配（clock 注入，测试注入 fixed Clock）。
- 项目字段投影为白名单（去掉 code/projectLevel 等），projectResource 通过 ProjectService.detail 取显示名。
- allowedActions 改在 AiContextAllowedActions（registry 驱动，与旧行为一致）；component.actions 非选择节点 NOT_REQUESTED 占位。
- 需求节点 fieldValues/bound 合并沿用旧 legacy 语义；componentReader 内部对 story/requirement workbench JSON 做引用投影。
- 旧 QueryService 的 context 拼装私有方法保留未删（不再被调用）；资源型 get 已接 requireRead + 引用投影。

### 遗留观察（转入 Task 6/8）

- 新留：story-list/split 组件仍 UNSUPPORTED（Task 8 接真实故事表）；relations 的 topic/story 部分在 Task 8 扩展。
- 需要 Task 6 的隔离 DB 证明：RR 隔离、无写足迹（annotation 不作为证据）、真实 MySQL 驱动覆盖。

## Task 6 评审：隔离数据库纯读与 RR 证明

提交：293283a test: prove readonly context slice in isolated MySQL（2026-10-14）。

### 测试证据

- gate 开（PMS_CONTEXT_READ_ISOLATED=true）：3 项用例实际执行全绿（Tests run 3, Skipped 0），见
  docs/agent-contracts/pms/readonly-context-e2e-record.md；gate 关：Tests run 3, Skipped 3，无容器启动。
- Tracker 覆盖：MyBatis Executor.update 拦截器（具名类保住 @Intercepts）+ JDBC DataSource
  动态代理（prepareStatement/prepareCall/createStatement + execute/executeBatch）。
- RR 验证：读事务屏障 SELECT → 外线程 UPDATE 提交 → 窗口内读到旧值（RR），新请求读到新值；
  该断言与"无写"窗口断言分离。

### 关键发现与处理

1. Spring 默认 DataSource 已包入追踪代理（BeanPostProcessor），fixture bean 从同一包装源构造。
2. 需求 create 阶段即创建研发工作流（业务事实），把"无实例乐观断言"改为"窗口内行数不变"。
3. 本机 docker 由 colima 挂载在 /Users fs socket —— 需 DOCKER_HOST/TESTCONTAINERS_HOST_OVERRIDE
   两个环境变量；这属于连接配置，不是隔离放宽（容器仍由测试创建/迁移/销毁）。
4. 测试类 gate 使用 @EnabledIfEnvironmentVariable(PMS_CONTEXT_READ_ISOLATED=true)，普通 CI 不跑该类。

### 遗留观察

- 通知/定时任务替身的作用域为测试容器内（spring 默认 mail 关闭）；未模拟业务定期任务。
- Task 8 扩展完组件矩阵后将重跑本类并扩展证据表。

## Task 7 评审：CLI 类型兼容、参数与请求结果一致性（C 仓库）

提交：C@44c0fb2 feat: support verified focused context in CLI（2026-10-14）。

### 测试证据

- tests/contract/workflow-context.test.ts（8 项新测试）全绿：URL 映射/回执核验（type/id、无 meta、selectedNodeId、
  selectedComponentKey 错配=validation）、legacy unknown[] 免 meta 兼容、非法 options 认证前拒绝、
  formatVersion≠2 拒绝（PmsClient validation）。
- 既有回归：unit 25 文件 + contract 合计 93 项全绿；pnpm typecheck 通过；build:cli 通过；node dist/pms-cli.js --help 正常。
- parseArguments 选项扩展（--view/--component value 型选项）不破坏既有 --node-id 两种写法与 --filter 等价语义。

### 真实试读（运行中的后端为实施前编译版本）

- legacy 视角：`pms context project 115` 拿到旧格式（节点 id=null、无 meta）→ CLI 兼容接受并透传 ✓
- 显式选项（--view focused --node-id 999 --component kickoff）：旧服务端忽略参数、无 meta 回执 →
  CLI 核验失败 VALIDATION，不重试、不降级 ✓
- 非法 --view bold → CLI_USAGE 认证/网络前拒绝 ✓
- 新后端下的 focused 试读需要重启 Spring（属于用户操作），留待 Task 9 真实验收。

### 遗留观察

- PmsClient 无 meta 回执未伪造权限/读取事实证明，回执核验集中在一个 helper，细节可被 Task 9 E2E 复用。
- Skill 已写入：focused 优先、contentState 四态语义、真实版本（null 不补 0）、回执失败不降级、正文指令不触发写入。

## Task 8 评审：完整组件快照和四类边界扩展

提交：3b382cf feat: complete persisted workflow context adapters（2026-10-14）。

### 测试证据

- 7 个领域测试类各新增 getReadSnapshot 用例：NodeRelease(5)、NodeValueReview(4)、NodePlanResourceRisk(14)、
  NodeKnowledgeStandard(3)、NodeDevelopmentControl(22)、NodeAcceptance(11)、NodeSolutionDesign(23) 全绿；
  AiProjectComponentReaderTest(7)+AiDevelopmentComponentReaderTest(7) 绿。
- 安全测试集合（三类 reader/policy/预算/路由/门面等）合计 227 项全绿。
- 隔离集成（PMS_CONTEXT_READ_ISOLATED=true）重跑 3 项全绿（约 22s），证明扩展后仍无写/无锁。

### 对照 Spec 核对

| 要求 | 结果 |
| --- | --- |
| 方案包/决策/3 类评审独立存在性（R03） | 通过：NodeSolutionDesignServiceTest.readSnapshot… separated parts 断言；requireNodeComponentForContext 走 Task1 路径（verify 旧 requireNodeComponent 零调用） |
| 研发管控无基线有专题 → LOADED（R04） | 通过：NodeDevelopmentControlServiceTest.readSnapshotCountsTopicsEvenWithoutBaseline |
| 全 8 服务 getReadSnapshot（除 requirement-scope 外新增 7 个） | 通过 |
| 真实版本 0 vs null 独立表现 | 通过（release/value/plan/knowledge 4 个 zero-version 测试） |
| AI reader 分流 9 组件 | 通过：AiProjectComponentReader 8 个项目键 + UNSUPPORTED 兜底；AiDevelopmentComponentReader 加入 story-list 分流（versions.stories=null） |
| 服务 DB 错误不转 EMPTY | 仍由既有测试与快照逻辑保证（异常直接传播） |
| strip UiFlags 扩展 | canEdit/topicCreationAllowed/projectBasicInfo 与需求行 taskCount 一并移除 |

### 偏差与处理

1. development/control toDTO 复用 topic/story/iterationPlanService 纯查询（UI 标志在 reader 侧剥离，快照不读默认配置）。
2. story-list 测试成果从节点 fieldValues.__components.story-testing 取出并重塑为 content.testingResults；
   挂载节点非 selectedNodeId 时 UNSUPPORTED（避免误报为 EMPTY）。
3. requireNodeComponent 换 requireNodeComponentForContext 的行为差异不影响写入服务（原 get 仍走原校验）。

### 遗留观察

- Story 内容行已按 canReadReference 过滤（可读才保留）；行级字段仍按真实值展示，尚不裁剪 prompt 语义（供 Task 9 模型评估）。

## Task 9 评审：真实只读、模型验收与最终发布门槛

C 提交：b36e5e2 test/docs: record readonly context delivery evidence；B 提交：ae6c651 docs: record task 9 delivery state。

### 已完成

- Step 1 安全 gate：tests/e2e/support/readonlyContextSession.ts —— requireReadonlyContextCase 只接受显式
  环境变量（缺失→场景"未运行"；非法/超安全范围整数→拒绝；不取搜索首条；不创建/清理资源），
  assertContextReceipt 复检 type/id/selectedNodeId/selectedComponentKey。gate 单元测试 6 项全绿。
- Step 4 最终回归：B 227 项安全集合 + 隔离集成重跑 + openapi 校验；C 94 项 unit/contract 全绿，
  typecheck/build:cli 通过。
- Step 6 交付证据：C b36e5e2 提交 readonlyContextSession + e2e 报告；B 已有 readonly-context-e2e-record.md。

### 明确未运行（缺环境，不是通过）

- Step 2 真实只读 API 验收：需要 PMS_E2E_READONLY=true、PMS_E2E_TOKEN 与四类显式 ID，当前会话无此配置。
- Step 3 页面/模型核验：需要新编译后端（当前运行实例是旧 build）与外部工作台模型，均不在本会话能力内。
- Step 5 feature 启用：readonlyWorkflowContext/focusedWorkflowContext 保持 false —— 交付记录明确说明
  尚未具备发布门槛，杜绝"先开启才能验收"循环。

### 遗留清单（用户侧动作即可解锁）

1. 重启 Spring（用最新 target/classes），env：DOCKER_HOST=unix:///Users/fs/.colima/fsclaw/docker.sock、
   TESTCONTAINERS_* 参照 Task 6（本地 MySQL 用 docker compose 起的桥接不需要容器）。
2. `PMS_E2E_READONLY=true PMS_E2E_TOKEN=... PROJECT_SR_ID=... REQUIREMENT_SR_ID=... TOPIC_SR_ID=... STORY_SR_ID=...
   pnpm run verify:real-pms:readonly` 逐项核验显式 fixture。
3. 模型工作台做一次只读解释（当前节点/历史差异/负责人/成果存在性），依据 SKILL.md 的只读规则。
4. 全部通过后再打开两个 feature 并做 capability 回归。

## 二次评审修复记录（2026-10-14 下午）

用户深度 review 提出 6 个问题（3×P1 + 3×P2），核实后全部属实并逐项修复。

| 问题 | 核实结果 | 修复方式 | 测试证据 |
| --- | --- | --- | --- |
| P1-1 门面缺主对象 READ | 属实：context() 直进 reader，detailForContext 不校验 requirement:read | AiWorkflowContextReadService 注入 AiContextReadPolicy，normalize 后立即 requireRead(type)（requirement→REQUIREMENT_READ、其余→PROJECT_READ），拒绝发生在任何 reader 之前 | AiWorkflowContextReadServiceTest.mainObjectModuleReadIsEnforcedBeforeAnyRuns（policy 拒绝→reader 零调用 verify） |
| P1-2 引用投影未贯穿 fieldValues | 属实：projectFieldValues 无生产调用；项目 legacy 直接复制原始 fieldValues；研发 legacy 合并 boundFieldValues 也未投影 | 项目 reader 与研发 reader（含 bound 合并路径、focused selected 路径）全部改为 referenceProjector.projectFieldValues 输出 | AiProjectContextReaderTest 新增 2 个用例：legacy 保留原始（含 __components）→ focused 去除 __components + 引用键（topicId/executionTarget stub 投影）；AiDevelopmentContextReaderTest 补 passthrough stub |
| P1-3 项目 focused 泄 __components | 属实：focused 选中节点直接复制完整 fieldValues | 项目 focused 分支（选中节点）额外 remove("__components")（与研发 focused 一致） | 同上 legacy/focused 对比断言 |
| P2-4 resource() 无事务 | 属实：/resources 直调 queryService.resource 无事务边界 | AiConnectorQueryService.resource 加 @Transactional(readOnly=true, isolation=REPEATABLE_READ) | 代码级修复；隔离集成测试覆盖同一事务门面语义 |
| P2-5 solution-design 版本映射不完整 | 属实：reader 只读 parts.content → package/decision/review:* 版本丢失 | readWorkbenchSnapshot 改为遍历全部 parts 逐键输出真实版本（persisted→真实版本、absent→null），不再只看 content | AiProjectComponentReaderTest.solutionDesignVersionsCarryEveryPersistedPart…（package=0、TEST_RELEASE=2、decision=null 全部断言） |
| P2-6 CLI 原始 ZodError 泄漏 | 属实：legacy/v2 envelope parse 失败抛原始 ZodError | parseWorkflowContextEnvelope / ensureWorkflowContextV2Parsing 统一包装为 PmsClientError(kind=validation)，保留首个 issue 消息 | workflow-context.test 新增 2 个用例（legacy envelope 结构不合法、meta.view 非法 → kind=validation） |

### 修复后验证

- B：安全测试集合 199 项全绿 + 隔离 MySQL 集成重跑 3 项全绿。
- C：unit/contract 96 项全绿（含新 2 个）、typecheck 通过。
- 提交：B d58287b、C 23a708f。
- feature 开关保持 false：真实 API / 权限 / 隔离 DB / 模型验收仍需按计划完成后才能打开。
