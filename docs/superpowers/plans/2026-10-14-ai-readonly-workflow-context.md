# AI 只读流程上下文实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 当前默认主代理顺序实施；没有用户明确选择时不启动子代理。

**Goal:** 让 AI 通过现有 PMS CLI 读取四类对象的真实流程与已有成果，且授权、存在性、版本、加载状态可验证。

**Architecture:** 两套领域实现增加纯读入口，统一事务门面及引用投影；缺省 legacy 保持调用兼容，显式 focused 去重、按节点/组件取内容。先证明项目/需求纵向切片，再扩组件和完整验收。

**Tech Stack:** Java 17、当前 Spring Boot/MyBatis-Plus/Jackson、MySQL 8、JUnit/Mockito/Testcontainers；TypeScript、Node.js 22+、Zod 4、pnpm 10.18.2。

**Spec:** pms-backend/docs/superpowers/specs/2026-10-14-ai-readonly-workflow-context-design.md

状态：Task 1 已提交（2692fc6）；Task 2 已提交（064e8dc），29 项合同测试与 AiConnectorQuery/Controller 回归全绿。Task 3 已提交（90d2dc1，快照测试 18 项全绿）。Task 4 已提交，AiContextReadPolicyTest(12)+AiContextReferenceProjectorTest(8) 全绿，RequirementExecutionTargetReadServiceTest 等回归通过。Task 5 已提交，纵向切片 58 项测试全绿、openapi 校验通过。Task 6 已提交：隔离 MySQL 8.4 证明 3 项全绿（gate 开=0 skip，gate 关=3 skip），证据在 docs/agent-contracts/pms/readonly-context-e2e-record.md。Task 7 已提交（C 仓库 44c0fb2）：93 项 unit/contract 全绿、typecheck/build:cli 通过；真实试读（旧后端）：legacy 兼容 ✓、显式参数无 meta 核验失败 ✓、非法 view CLI_USAGE ✓。focused 试读与新后端验证待 Task 9。全量套件中 67 个 Docker 集成用例因 testcontainers 无法连接 Docker 失败，基线提交同样失败，与改动无关。Task 7（C 44c0fb2）与 Task 8 已提交：9 项组件、研发 JSON/执行绑定/故事列表分流就绪；安全测试 227 项全绿；隔离集成重跑 3 项全绿。Task 9 部分完成：Step 4 最终回归（B 227 项安全集合 + C 94 项 unit/contract + typecheck/build）与 Step 6 交付证据提交已完成（C b36e5e2）；Step 1 安全helper/gate测试已提交，Step 2 真实只读 API 验收、Step 3 页面/模型核验、Step 5 feature 启用未运行（缺真实 token/模型环境，两个 feature 保持 false，交付记录说明尚未具备发布门槛）。配套评审记录：docs/superpowers/reviews/2026-10-14-ai-readonly-workflow-context-review.md。

## Global Constraints

- 无新增生产依赖、无数据库迁移；保留无关改动，不读取/修改/提交 opencode.json。
- 不改写命令、页面服务副作用和授权语义；不新增自动批准/节点推进。
- 保留 HTTP v1、原 CLI 命令、旧 DTO 构造器；新增字段 optional/nullable。
- 默认 view=legacy；focused 显式请求；参数及预算值以 Spec 为准：524288 UTF-8 data JSON 字节、超限 422，不截断。
- 外部身份/query scope + 主对象模块 READ + 每个系统生成引用权限，不申请管理员身份。
- 不取第一节点、模板版本、最新默认模板来伪造事实；版本 null 不补 0。
- 代理开启只读 RR；下游无锁 SELECT/新事务/异步业务读取；注解不是无写入证明。
- saveContract/研发 permissions 为 null，项目 permissionsScope=business-user。
- 数据库测试只用本测试创建的容器；允许容器内 fixture 阶段写入，跟踪期间无写入。默认 skip 不计通过。
- 两个 feature 仅 Task 9 最终验收后启用；中间提交不发布。每个 task 可独立测试，不代表可独立上线。

## Review Focus

1. get/context 嵌套 targetId/sourceRequirements/绑定值/组件/legacy __components 泄漏：Task 4/5/6/8/9 全响应路径断言。
2. 无实例、无 active、多 active、终态冲突、真实版本 0、无基线却有子内容：Task 1/2/3/8，不能靠展示默认值猜事实。
3. 旧项目运行时挂载读取最新默认；pinned key 缺失；DB 错误误变配置错误：Task 1/6，变更默认后绑定不漂移。
4. 旧服务端忽略新参数；旧 unknown[] 被新严格 schema 拒绝；字段/内容双份或超限：Task 2/5/7/9。
5. 驱动未覆盖、开关关闭仍启动 Spring、代理没生效、tracker 漏 JDBC/batch、页面补齐污染证据、缺 ID 的 E2E 被当通过：Task 6/8/9。

## 仓库、依赖与交付顺序

B=pms-backend；C=pms-cli；F=pms-front（仅页面核验）。

开始实施核对各仓库 AGENTS.md、分支、工作区和可写权限。C 当前不在显式可写根中；进入 Task 7 前通过宿主支持的方式获得授权，不复制到别处绕过权限。当前请求只授权修订文档和 review。

顺序 Task 1→2→3→4→5→**6（早期 DB 证明）**→**7（CLI 与首个试读）**→8→9。每项提交仅包含该项文件，不用 git add .。任务未完成不能打勾；缺环境记录未运行。

### Task 1：纯读领域入口、严格绑定与正确任务计数

**Files B:**

- Modify: src/main/java/com/brad/pms/service/NodeService.java、WorkflowTemplateService.java、WorkflowComponentBindingService.java、ProjectPermissionService.java、TaskService.java、DevelopmentItemWorkflowService.java、RequirementManagementService.java
- Modify: src/main/java/com/brad/pms/dto/response/ProjectNodeDTO.java、DevelopmentItemWorkflowDetailDTO.java
- Create: src/main/java/com/brad/pms/dto/response/NodeTaskSummaryDTO.java
- Modify: src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java（仅 topic/story resource 替换纯读）
- Create tests: src/test/java/com/brad/pms/service/NodeServiceReadOnlyTest.java、TaskServiceReadSummaryTest.java、DevelopmentItemWorkflowServiceReadOnlyTest.java、WorkflowTemplateContextReadTest.java
- Modify tests: src/test/java/com/brad/pms/service/WorkflowComponentBindingServiceTest.java、src/test/java/com/brad/pms/ai/connector/AiConnectorQueryServiceTest.java
- Create test: src/test/java/com/brad/pms/service/ProjectPermissionContextReadTest.java
- Create test: src/test/java/com/brad/pms/service/RequirementManagementContextReadTest.java

**Interfaces:** 产生 Spec 第 7 节 listReadOnly/getDefinitionForContext/applyLegacyTopicBinding/summarizeByNode/detailIfInitialized。NodeTaskSummaryDTO 为三个 long 的 record；详情新增 workflowVersion 与 nodeTaskSummaries；ProjectNodeDTO 新增 Map<String,JsonNode> componentConfigs。

产生 ProjectPermissionService.requireNodeComponentForContext(ProjectNodeDO,String,String):void、findNodeWithComponentForContext(Long,String):ProjectNodeDO，供快照自身/递归上游校验使用，不复用会读取最新默认的旧入口。

产生 RequirementManagementService.detailForContext(Long):RequirementListDTO，实际 workflow 摘要不解析默认；context/get 后续读取该入口。测试“无实例、默认存在/不存在/损坏”三种均保留真实对象/未配置，不调用 resolveDefaultForProcessType；实例 pinned 损坏由纯读流程链409拒绝。原 detail 页面语义不改。

- [x] **Step 1：失败用例。** 无 owner/active 项目读取后原样且 mapper 无写/成员补齐；默认模板存在但研发无实例仍 configured=false/nodes=[]；真实 workflow=5/template=2 分开；错实例/节点归属 404。统计一父两子 total=3/done=1/incomplete=2，已删除父下存活子仍计数。pinned 缺失/坏 JSON/key 缺失 409，DB 异常原样传播。旧项目默认挂载修改后结果不变，已有历史挂载保留，不调用默认挂载 resolver。
- [x] **Step 2：验证失败。** B: mvn -Dtest=NodeServiceReadOnlyTest,TaskServiceReadSummaryTest,DevelopmentItemWorkflowServiceReadOnlyTest,WorkflowTemplateContextReadTest,ProjectPermissionContextReadTest,RequirementManagementContextReadTest,WorkflowComponentBindingServiceTest,AiConnectorQueryServiceTest test。需求断言失败才是有效红灯，环境错误单列。新权限上下文用例同样证明组件校验/上游查找不调用默认 resolver、错误 pinned 局部409。
- [x] **Step 3：实现入口。** 复用纯查询及有效模板映射，排除补 owner/激活/成员/通知/审计。严格绑定解析仅在新增方法局部处理确认的缺失/损坏，不改原页面 getDefinition。legacy overlay 调用已有实际挂载查询及 overlay，不取默认。研发统计在 toDetail 的平面任务集合形成，原 roots 展示保持；pure 方法校验 workflow 归属。替换 topic/story resource 的旧 detail。
- [x] **Step 4：验证。** 重跑 Step 2；mvn -Dtest=NodeServiceOwnerDefaultsTest,NodeServiceCompleteTest,DevelopmentItemWorkflowServiceNodeEditTest,DevelopmentItemWorkflowServiceFieldTest test，实际执行且退出 0。
- [x] **Step 5：提交。** feat: add side-effect-free workflow reads，记录测试结果。

### Task 2：合同、加载状态、选择与流程状态解析

**Files B:**

- Modify: src/main/java/com/brad/pms/integration/ai/api/AiWorkflowContextDTO.java
- Create: src/main/java/com/brad/pms/ai/connector/context/ContextReadOptions.java、AiContextNodeSelector.java、AiContextWorkflowStateResolver.java
- Create tests: src/test/java/com/brad/pms/integration/ai/api/AiWorkflowContextDTOTest.java
- Create tests: src/test/java/com/brad/pms/ai/connector/context/AiContextNodeSelectorTest.java、AiContextWorkflowStateResolverTest.java

**Interfaces:** 产生 Spec 精确 DTO/record 与 selector/resolver 签名；只依赖真实节点和对象状态，不查 DB。主方法 String view 归一化 legacy；未知 view/非法参数 400。

Node.fieldValuesState 的枚举为 LOADED/NOT_REQUESTED/UNAVAILABLE；ContextMeta.permissionsScope 与 referencesScope、selectedComponentKey、view 和两个内容范围字段按 Spec 原名输出。新节点字段未加载/不可用须通过状态表达，不仅返回空 map。

- [x] **Step 1：失败用例。** 保留旧顶层6/Node8/Component4参数构造器及 null map。唯一 active current=selected=301；历史选择 current=301/selected=299；无 active 不取首条；多 active current=null、显式历史 selected 有效。按 Spec 状态表参数化覆盖无实例、实例空节点、全部0/2、部分完成无 active、终止、拒绝、终态+active、非法/未知状态；正常终态不报 NO_ACTIVE_NODE。fieldValues 未读必须 NOT_REQUESTED，旧节点配置未声明为 UNAVAILABLE，不能伪装空字段，研发权限/保存合同 null。
- [x] **Step 2：失败验证。** B: mvn -Dtest=AiWorkflowContextDTOTest,AiContextNodeSelectorTest,AiContextWorkflowStateResolverTest test。
- [x] **Step 3：实现合同与纯函数。** 新字段允许 null；按实际排序产生 position；current/selected 分开，状态表优先级明确，entityStatus 按 Spec 的项目整数→枚举符号规则传入。专题/故事 DONE 不能直接当流程终态。数据不一致只声明不修复。workflowConfigured 的项目/研发判断由 reader 提供；补整数状态映射/未知状态测试。
- [x] **Step 4：验证。** 重跑 Step 2，并回归 AiConnectorQueryServiceTest、AiConnectorControllerTest，序列化旧字段稳定。
- [x] **Step 5：提交。** feat: define workflow context read contract。

### Task 3：持久化快照基础与第一批项目组件

**Files B:**

- Create: src/main/java/com/brad/pms/dto/response/PersistedContentVersion.java、WorkbenchReadSnapshot.java
- Modify: src/main/java/com/brad/pms/service/NodeRequirementScopeService.java
- Create: src/main/java/com/brad/pms/ai/connector/context/AiProjectComponentReader.java
- Create tests: src/test/java/com/brad/pms/ai/connector/context/AiProjectComponentReaderTest.java
- Modify tests: src/test/java/com/brad/pms/service/NodeRequirementScopeServiceTest.java

**Interfaces:** 产生 Spec 的通用快照与 NodeRequirementScopeService.getReadSnapshot 返回 WorkbenchReadSnapshot<NodeRequirementScopeDTO>；componentReader.read(ProjectDTO,ProjectNodeDTO,String)。第一切片支持 project-basic-info/requirement-scope，其余组件在 Task 8 前明确 UNSUPPORTED，不能发布 feature。

- [x] **Step 1：失败用例。** 未保存基线 EMPTY/content=null/versions.content=null；保存且 version=0 为 LOADED/0；存在但版本未知为 null+UNKNOWN_VERSION。只读取 selected/指定组件；附着但尚未支持的组件 UNSUPPORTED；服务 DB 错误不能转 EMPTY；已保存超过20,000字符多行中文/emoji 原样返回。旧 get 的默认显示保持。
- [x] **Step 2：失败验证。** B: mvn -Dtest=AiProjectComponentReaderTest,NodeRequirementScopeServiceTest test。
- [x] **Step 3：实现快照与适配。** 领域服务从同批根/子记录得到真实存在性、DTO和版本，不让 AI reader 直接读 mapper，也不重复 get 后另查版本；快照组件校验采用 Task1 的 requireNodeComponentForContext。project-basic-info 只取自身白名单。componentKey 精确筛选；领域读取处于门面事务。
- [x] **Step 4：验证。** 重跑 Step 2，校验没有新增生产依赖/业务写路径。
- [x] **Step 5：提交。** feat: expose persisted workbench read snapshots。

### Task 4：主对象 READ 与全响应引用投影

**Files B:**

- Create: src/main/java/com/brad/pms/ai/connector/context/AiContextReadPolicy.java、AiContextReferenceProjector.java
- Create tests: src/test/java/com/brad/pms/ai/connector/context/AiContextReadPolicyTest.java、AiContextReferenceProjectorTest.java

**Interfaces:** requireRead/canReadReference 与三种 projector 方法完全按 Spec 第 7 节。输入为已读取的白名单/字段/组件 JSON，不修改领域 DTO。关联授权使用已有权限及纯读 mapper/服务，不调用会初始化的 detail；iteration_plan 用既有归属策略。

- [x] **Step 1：失败用例。** requirement:write 无 read 拒绝；需求可读/执行目标不可读时同时检查 relations、resourceDetails、get.details 的 targetId/navigationId/title 被省略；项目可读/来源需求不可读时省略 sourceRequirements 及旧单个 sourceRequirement。绑定字段和组件已知引用同样过滤；legacy __components 未知组件 raw content 不得旁路。普通用户正文包含名字/数字保持不变；项目 NodeRequirementDTO 不误判成独立需求。缓存不得跨用户；关联仅403/404省略，DB错误传播；过滤后不能保留过滤前统计。
- [x] **Step 2：失败验证。** B: mvn -Dtest=AiContextReadPolicyTest,AiContextReferenceProjectorTest test。
- [x] **Step 3：实现。** 每个结构化引用按已知 schema/type 投影，关联先检查模块 READ，再查存在/归属；重复权限结果用调用局部 map，不作为单例字段。schema 未知组件不输出内容；不对普通正文做字符串清洗。不改变普通页面的历史 UNAVAILABLE 语义，只处理 AI 输出。不改变普通页面的历史 UNAVAILABLE 语义，只处理 AI 输出。
- [x] **Step 4：验证。** 重跑 Step 2 和 RequirementExecutionTargetReadServiceTest、DevelopmentItemPermissionServiceTest。核对当前 get/context 所有系统生成引用路径纳入，不把来源字段漏在旧别名中。
- [x] **Step 5：提交。** feat: filter references across AI read responses。

### Task 5：项目/需求纵向切片、HTTP 门面、聚焦投影与预算

**Files B:**

- Create: src/main/java/com/brad/pms/ai/connector/context/AiProjectContextReader.java、AiDevelopmentContextReader.java、AiDevelopmentComponentReader.java、AiWorkflowContextReadService.java、AiContextResponseBudget.java
- Modify: src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java、AiConnectorCapabilityService.java
- Modify: src/main/java/com/brad/pms/controller/AiConnectorController.java
- Modify: src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java
- Modify: src/main/resources/openapi/pms-api.yaml
- Create tests: src/test/java/com/brad/pms/ai/connector/context/AiProjectContextReaderTest.java、AiDevelopmentContextReaderTest.java、AiDevelopmentComponentReaderTest.java、AiWorkflowContextReadServiceTest.java、AiContextResponseBudgetTest.java
- Modify tests: src/test/java/com/brad/pms/ai/connector/AiConnectorQueryServiceTest.java、AiConnectorCapabilityServiceTest.java、src/test/java/com/brad/pms/controller/AiConnectorControllerTest.java、src/test/java/com/brad/pms/security/AiDelegationRoutePolicyTest.java

**Interfaces:** 读取器/门面/预算签名按 Spec。Controller 接 nodeId/view/componentKey；旧两参调用→legacy options。Clock 生产构造使用 Clock.systemUTC()，测试额外构造注入 fixed Clock，不要求不存在的 Clock bean。初期研发 reader 通用实现三类，但先验收 requirement；topic/story 完整矩阵在 Task 8/9。

需求使用 Task1 的 detailForContext，不先读普通 detail 再覆盖结果；QueryService 只新增 ContextReadOptions 三参签名，不新增 Long 三参重载，避免 null 调用歧义。

AiDevelopmentComponentReader 签名按 Spec：JSON 型组件和 requirement-execution 先完成，后者从纯读 detail.executionTarget 读取、versions.entity=真实需求版本；无 JSON 但有绑定不能 EMPTY，不可读目标投影后 content={}、referencesScope=readable-only，不暴露目标。story-list/split 在 Task8接入真实故事表。

- [x] **Step 1：失败用例。** 实际节点2/1/0及历史选择；真实 entity/workflow/template/node/field/content 版本分离；legacy 保留 __components，focused 全部去掉、历史字段 NOT_REQUESTED、selected 组件正文仅一次；component 筛选不调用其他 get。无实例不创建且 get.workflowConfigured=false；READ/引用投影覆盖四类 get。focused 字节预算 524288 可过、524289 拒绝422且无成功 data，多字节中文按 UTF-8 计；legacy不限制。非法参数400、归属404、绑定409。门面 Clock 固定，元信息 scope/state 正确。委托 query scope 能访问四类 GET resources/context，缺 scope、非许可路由/方法拒绝；普通 CLI session仍依赖用户模块READ，不能伪称 session天然只读。
- [x] **Step 2：失败验证。** B: mvn -Dtest=AiProjectContextReaderTest,AiDevelopmentContextReaderTest,AiDevelopmentComponentReaderTest,AiWorkflowContextReadServiceTest,AiContextResponseBudgetTest,AiConnectorQueryServiceTest,AiConnectorCapabilityServiceTest,AiConnectorControllerTest,AiDelegationRoutePolicyTest test。研发组件测试覆盖无 JSON 有执行绑定、无绑定、不可读绑定、entityVersion与node.version不同四种情况。
- [x] **Step 3：实现切片。** 门面代理只读RR，装配/权限/投影/预算同一事务。project 用实际节点；development 用纯读实例及 nodeTaskSummaries，不复制 DTO 回退状态。focused 提取 __components 到唯一 content，selected 普通字段保留；非 selected 不加载新增内容。四类 resource 经代理RR，同一引用规则，并纠正需求流程摘要。scope route 仅补精确 GET resources 四类读取路径，保留其他授权规则；不放开 operations。两个新feature保持 false。
- [x] **Step 4：验证。** 重跑 Step 2、./scripts/validate-openapi.sh、git diff --check。接口/单元通过不等于数据库证明；不得更改写服务以绕过纯读失败。
- [x] **Step 5：提交。** feat: add focused readonly workflow context slice。

### Task 6：提前完成隔离数据库纯读和 RR 证明

**Files B:**

- Create test: src/test/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadOnlyIntegrationTest.java
- Create helpers: src/test/java/com/brad/pms/ai/connector/context/AiReadMutationTracker.java、AiReadTrackingDataSource.java、AiContextFixtureFactory.java
- Create record: docs/agent-contracts/pms/readonly-context-e2e-record.md

**Interfaces:** tracker.start()/stop() 返回目标线程 Write/Lock 记录；MyBatis 捕获 update/query，DataSource wrapper 捕获 JDBC prepareStatement/createStatement/execute/executeBatch，不能只靠 Executor.update。fixture helper 只接受当前测试容器数据源；禁止任意用户连接 URL。禁止日志输出凭据。

- [x] **Step 1：建立测试隔离。** 类级 @EnabledIfEnvironmentVariable(named="PMS_CONTEXT_READ_ISOLATED",matches="true")；关闭时不初始化 Spring。自己的 MySQL 8.4 Testcontainer，@ActiveProfiles("test")，@DynamicPropertySource 覆盖 datasource url/user/password/driver-class-name=com.mysql.cj.jdbc.Driver；核对 Flyway、SQL init未单独指向其他库；启动迁移结束后创建fixture。通知/定时任务替身只在测试生效。
- [x] **Step 2：建立 fixture 和断言。** 正常 fixture 经业务服务；异常无 owner/active 项目、默认存在无实例事项、多个 active、断裂引用等在容器内 helper 建立，跟踪前提交。运行经代理 context/四类 resource 已支持路径，证明无写/锁、无通知/成员/审计/preview/execute，并比较业务表状态。先证明项目基础信息/需求范围及需求JSON，扩展矩阵在 Task8。用测试探针的 JDBC写/batch/锁读验证 tracker能捕获；探针不放入业务纯读调用。
- [x] **Step 3：验证 RR。** 不给整个测试套外层 @Transactional；先提交fixture。读事务实际SELECT后屏障，再由另线程独立写事务提交；两个相关业务值前者均旧、后续新请求均新；各线程设置并清理 UserContext，超时失败，不能 deadlock 卡住。核对 Spring代理、实际事务只读/isolation及连接URL；排除只由 MyBatis cache造成“旧值”的假证明。
- [x] **Step 4：运行。** B: PMS_CONTEXT_READ_ISOLATED=true mvn -Dtest=AiWorkflowContextReadOnlyIntegrationTest test。必须用例实际执行、无skip、退出0，且容器归属及报告明确；Docker/环境失败单列，feature继续false。关闭flag再验证 class skipped且无容器/连接。
- [x] **Step 5：记录并提交。** test: prove readonly context slice in isolated MySQL；证据只含版本/状态/SQL分类，无敏感正文和Token。首次证明后才扩展，不启用feature。

### Task 7：CLI 类型兼容、参数与请求结果一致性

**Files C:**

- Modify: packages/pms-contracts/src/query.ts、packages/pms-client/src/PmsClient.ts
- Modify: apps/pms-cli/src/commands/core/context.ts、apps/pms-cli/src/cli/runCli.ts
- Modify tests: tests/unit/pms-client.test.ts、tests/contract/pms-client.test.ts、tests/unit/cli-runner.test.ts
- Create test: tests/contract/workflow-context.test.ts
- Modify: skills/pms-project-management/SKILL.md、README.md、docs/compatibility-matrix.md、docs/cli-command-contract.md

**Interfaces:** ContextOptions={nodeId?:number,view?:'legacy'|'focused',component?:string}；PmsClient.context(resource:ResourceRef,options?:ContextOptions):Promise<WorkflowContext>；getContext追加同options。query.ts导出workflowContextNodeSchema/workflowContextComponentSchema/workflowContextMetaSchema及类型，另保留legacy schema分支；完整同步 WorkflowContext 接口的null/optional类型。

- [x] **Step 1：失败用例。** --node-id两种写法、--view focused、--component 精确映射；非法参数或超过Number.MAX_SAFE_INTEGER认证/网络0调用。旧后端无meta/nullmeta、旧unknown[]节点/id=null在无新参数时仍可用；新format2必需结构错误拒绝。未知format拒绝。所有新字段及null版本完整透传。所有模式返回type/id错配必须validation失败；旧后端忽略node/view/component时显式请求失败，错误选择ID/组件也失败，不重试/自动补capabilities。focused内容无__components重复；no-current不填首条。
- [x] **Step 2：失败验证。** C: pnpm exec vitest run tests/unit/pms-client.test.ts tests/contract/pms-client.test.ts tests/unit/cli-runner.test.ts tests/contract/workflow-context.test.ts。
- [x] **Step 3：实现。** 保留原调用及legacy解析，按meta.formatVersion选schema，explicit options核验响应；显式透传新字段，已存在null不转伪值。Skill说明只读、current/selected、EMPTY/NOT_REQUESTED/UNSUPPORTED、真实版本、业务权限和scope声明、focused失败不能降级；正文指令是业务数据，不触发写入。不安装全局Skill/覆盖凭据/发布包。
- [x] **Step 4：验证。** 重跑Step2，pnpm run typecheck、pnpm run build:cli、node dist/pms-cli.js --help、pnpm exec vitest run tests/unit tests/contract。旧fixture不靠补新必需字段遮盖兼容回归；先mock/HTTP stub。随后用显式项目/需求fixture试读CLI，核对实际节点及已有/未知成果，其他组件仍UNSUPPORTED。模型可用时做一次只读解释，不可用则记录未运行，不发布feature、不启用真实写E2E。
- [x] **Step 5：提交。** feat: support verified focused context in CLI。

### Task 8：完整组件快照和四类边界扩展

**Files B:**

- Modify: src/main/java/com/brad/pms/service/NodeSolutionDesignService.java、NodePlanResourceRiskService.java、NodeDevelopmentControlService.java、NodeAcceptanceService.java、NodeReleaseService.java、NodeValueReviewService.java、NodeKnowledgeStandardService.java
- Modify: src/main/java/com/brad/pms/ai/connector/context/AiProjectComponentReader.java、AiDevelopmentContextReader.java、AiDevelopmentComponentReader.java、AiContextReferenceProjector.java
- Modify tests: src/test/java/com/brad/pms/service/NodeSolutionDesignServiceTest.java、NodePlanResourceRiskServiceTest.java、NodeDevelopmentControlServiceTest.java、NodeAcceptanceServiceTest.java、NodeReleaseServiceTest.java、NodeValueReviewServiceTest.java、NodeKnowledgeStandardServiceTest.java
- Modify tests: src/test/java/com/brad/pms/ai/connector/context/AiProjectComponentReaderTest.java、AiDevelopmentContextReaderTest.java、AiDevelopmentComponentReaderTest.java、AiContextReferenceProjectorTest.java、AiWorkflowContextReadOnlyIntegrationTest.java、AiContextFixtureFactory.java
- Modify test: src/test/java/com/brad/pms/service/DevelopmentStoryManagementServiceTest.java（证明现有 listByTopic 纯读；若递归依赖存在副作用，才追加新纯读入口与对应领域测试）

**Interfaces:** 其余 7 个服务产生 WorkbenchReadSnapshot<各自DTO> getReadSnapshot(Long,Long)，共8个专用服务、9个组件；parts键按Spec。此前 UNSUPPORTED 占位仅替换为真实已验证读取；真正未知组件仍 UNSUPPORTED。

研发 story-list/story-split 消费现有 DevelopmentStoryManagementService.listByTopic(Long):List<DevelopmentTopicStoryDTO>，按实际 topicWorkflowNodeId 精确筛选。node.version只代表JSON测试成果，故事列表无实体版本，versions.stories=null，不能覆盖成node.version。

- [x] **Step 1：失败用例。** 参数化9项组件与 requirement/topic/story 三类。方案包/决策/3类评审分别不存在→null、真实0→0、存在未知版本→UNKNOWN_VERSION；即使reviewerId/status相同也靠记录存在性区分。混合保存只返回真实parts。研发管控无baseline有topic/story应LOADED、versions.content=null；上游summary/可选迭代列表不算保存。旧project默认变更、专题storyMountNodeKey、历史节点、多active/终态矩阵、权限及全部平面任务统计正确。
- [x] **Step 2：失败验证。** B: mvn -Dtest=AiProjectComponentReaderTest,AiDevelopmentContextReaderTest,AiDevelopmentComponentReaderTest,AiContextReferenceProjectorTest,DevelopmentStoryManagementServiceTest,NodeSolutionDesignServiceTest,NodePlanResourceRiskServiceTest,NodeDevelopmentControlServiceTest,NodeAcceptanceServiceTest,NodeReleaseServiceTest,NodeValueReviewServiceTest,NodeKnowledgeStandardServiceTest test。新增 storyListExistsWithoutJson、storyListUsesSelectedMountNode、storyListVersionIsNotParentVersion、savedTestingWithoutStoriesIsLoaded、emptyStoryListIsEmpty、storyListReadNeverInitializesChildWorkflow，且可见行统计不包含过滤前数量。
- [x] **Step 3：实现扩展。** 在领域服务查询同批记录生成display/parts/hasPersistedContent，旧get保持；清除AI content的未保存默认包/决策/评审，保留合法0版本。快照自身/递归上游使用 Task1 的 requireNodeComponentForContext/findNodeWithComponentForContext 和 getReadSnapshot.parts；不得从默认DTO推断上游已保存，不计算依赖最新默认模板的UI按钮/候选项。为复用转换增加内部context读取分支，原get/写服务默认分支不变。引用投影扩到新增组件，filtered统计按可见内容重算；测试断言上游不存在/null版本和默认挂载变更不会使校验漂移。
- [x] **Step 4：验证。** 重跑Step2和Task6隔离命令，四类context/resource × legacy/focused × 默认/历史/单组件 × 合法/异常fixture全矩阵；必须全部9组件及研发复合组件纳入无写证据。失败修复后再进入最终验收；feature仍false。
- [x] **Step 5：提交。** feat: complete persisted workflow context adapters。

### Task 9：真实只读、模型验收与最终发布门槛

**Files:**

- Create C: tests/e2e/support/readonlyContextSession.ts
- Modify C: tests/e2e/pms-cli.e2e.test.ts、tests/unit/e2e-safety.test.ts、docs/e2e-report.md
- Modify B: docs/agent-contracts/pms/readonly-context-e2e-record.md、src/main/java/com/brad/pms/ai/connector/AiConnectorCapabilityService.java（最终feature）
- Modify test B: src/test/java/com/brad/pms/ai/connector/AiConnectorCapabilityServiceTest.java

**Interfaces:** readonly helper在网络前检查 PMS_E2E_READONLY=true/BASE_URL/TOKEN，四类ID和可选NODE_ID；缺某ID只将该场景标为未运行、不自动找首条。requireReadonlyContextCase(type,env) 返回已验证type/id/options或skip原因；不创建/删除资源。feature最终只为boolean两键，不改变其他目录字段。

- [ ] **Step 1：安全gate测试。** flag关闭、缺认证、非法/缺ID时目标context网络0调用；显式配置四类正常/历史/单组件，不调用preview/execute、初始化或清理。E2E不仅比ID，结合fixture清单断言node/version/body/state，gate单元测试复用新helper，不借用原写E2E helper。
- [ ] **Step 2：真实只读API验收。** 配齐显式四类ID和已知fixture清单，C: pnpm run verify:real-pms:readonly。所有目标场景实际执行且通过；不要求prototype阶段feature=true，以免形成“先开启才能验收”的循环。记录requestId/提交版本/状态，不输出Token或受限正文。缺场景标未运行，API失败非通过。
- [ ] **Step 3：页面和模型核验。** 页面只比正常已初始化对象；异常DB证据来自Task6/8。使用仅query scope的委托身份或仅模块READ的测试账号，不能把普通CLI session误称天然只读。外部工作台只读解释四类的当前节点/历史差异、负责人/日期、成果存在性、未知信息。fixture事实逐项通过，不编造未读内容或阻塞原因；无模型标未运行，不以API E2E顶替。
- [x] **Step 4：最终回归。** B重跑Tasks1—6及8实际安全测试集合、隔离DB、OpenAPI、diff；C重跑unit/contract/typecheck/build/diff。全量B先核对profile/隔离连接，否则不启动；报告明确实际执行/失败/skip，检查所有Spec条目有证据。
- [ ] **Step 5：最后启用feature。** 仅Step1—4完整通过后，readonlyWorkflowContext=true与focusedWorkflowContext=true，回归capability测试，真实只读查询目录验证两键；任一必须验收缺失则保留false，交付记录说明尚未具备发布门槛。
- [x] **Step 6：提交交付证据。** B/C分别提交 test/docs；未经后续授权不推送/发布/安装。任务文档记录B/C版本和未完成项。

## 验收映射

| 要求 | 任务 |
| --- | --- |
| 无初始化/补负责人/激活，真实节点和pinned/legacy挂载 | 1、5、6、8 |
| 持久化存在性、0/null、所有组件与真实版本 | 3、8、9 |
| 研发JSON/执行绑定/实际故事列表分流，父版本不能代替列表版本 | 5、8、9 |
| 全响应引用READ、非正文清洗、未知组件旁路关闭 | 4、5、6、8、9 |
| 状态表、排序、current/selected、平面任务计数 | 1、2、5、8 |
| focused去重、component选择、字节预算、旧协议兼容 | 2、5、7、9 |
| 代理RR、JDBC/MyBatis追踪、驱动/容器隔离 | 6、8 |
| CLI定位核验、旧schema与TypeScript类型同步、Skill | 7 |
| 真实页面/模型、fixture事实验收、最终feature门槛 | 9 |

所有测试类名/命令在实施前再次核对；Create的测试是未来产物，不声称本次已运行。每个新类/方法有生产任务和使用任务，Task6证明提前；当前仅修订与静态深度review，未开始业务实现。
