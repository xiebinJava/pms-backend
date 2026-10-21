# PMS CLI Context and Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让外部 AI 经 PMS CLI 准确读取业务上下文，安全创建项目，并将分析及带来源的调研正文追加到现有需求澄清节点。

**Architecture:** 后端领域服务负责权限、只读查询、受限正文更新、版本和事务，既有命令链负责幂等与操作审计。连接器负责能力发现、严格解析、认证、命令路由及结果回读；不直接访问数据库，不增加 AI 成果表。按三个相互依赖的阶段推进，每阶段都有独立验收关口。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、JUnit 5/Mockito；Node.js >=22、TypeScript、pnpm 10.18.2、Vitest、Zod、keytar；现有 Vue 页面仅作验收。

**Spec:** `pms-backend/docs/superpowers/specs/2026-10-10-pms-cli-context-and-analysis-design.md`，以包含第 9 节自审修订和本次用户评审补充的版本为准。本次仅修订计划与设计，所有实现任务仍未执行。

## Global Constraints

- 不直接操作数据库，不清空业务数据，不修改默认流程模板和历史绑定模板。
- 不增加模型供应商管理、Agent 调度器、MCP 平台或独立分析结果表。
- 不安装全局 Skill、不覆盖用户凭据、不推送或发布软件包，除非后续另获用户授权。
- 保留用户已有的无关改动；后端未跟踪的 `opencode.json` 不读取、不修改、不提交。
- “分析并保存”不包含批准需求、选择接收结论、完成节点、改变执行对象或删除业务数据的授权。
- 第一版不新增 AI 页面或重做工作台布局；不自动推进节点来制造可写目标。
- 现有顶层 `version` 意义不变；缺少节点写版本时拒绝写入，不默认填 v1/0。
- 真实写入默认关闭；Mock、真实 E2E、页面检查分别记录，不能相互替代。

## Review Focus

- 新接口未加入委托令牌路由规则：允许必要只读 scope，但不能变成 `/integration/ai/v1/**` 全放行；Task 2 测试。
- 旧服务器、嵌套 Zod schema 和规范化器丢失元数据：旧读响应仍可解析，新增写能力缺失时明确失败；Task 4 测试。
- 两个 CLI 进程同时刷新同一凭据：不丢失轮换后的 Token，不覆盖其他实例账号；Task 5 测试。
- 历史非功能需求仍有关联系统、空组件或已有未知嵌套键：追加正文不修复关联、不删除其他数据；Task 8 测试。
- 服务端已成功但响应丢失、随后节点版本变化：完整原请求恢复得到原结果且正文只追加一次；Task 9、10 测试。

---

## 工作目录、文件职责及实施约定

本文路径前缀：`B` = `pms-backend`；`C` = `pms-cli`；`F` = `pms-front`。下文 `B/src/...` 等表示这些绝对目录，不是新目录。Maven 命令在 B、pnpm 命令在 C 执行。

核心职责：

| 文件/模块 | 职责 |
| --- | --- |
| B 的 DevelopmentItemWorkflowService | 非初始化读取；仅更新分析正文的领域事务 |
| B 的 DevelopmentItemService | 增加专题/故事精确只读详情，复用业务投影，不通过分页查找 ID |
| B 的 NodeService / WorkflowTemplateService | 增加无节点激活/默认负责人写入的节点读取、按绑定 ID 读取模板版本号 |
| B 的 AiConnectorResourceReadService（新增） | 七类资源的精确读取、权限感知业务投影、旧 id 查询匹配；context 复用相同投影 |
| B 的 AiConnectorContextAssembler（新增） | 组装实例绑定节点、组件状态与有限关联，不实现业务写入 |
| B 的 AiWorkflowComponentContractResolver（新增） | 发布真实保存契约；未知组件明确只读 |
| B 的 RequirementAnalysisFields（新增） | 澄清表单识别、正文白名单、追加与长度限制的唯一规则来源 |
| B 的 SaveRequirementAnalysisCommand（新增） | 受限命令预览及执行，复用既有审计/幂等链 |
| C 的 contracts/client | 保留新增元数据；精确 get；能力缺失时拒绝不准确降级 |
| C 的 cli/parseArguments、cli/runCli（新增） | 校验在认证请求之前；统一请求 ID、输出和命令路由 |
| C 的 AuthStore/RefreshCoordinator | 地址+账号凭据隔离；刷新协调；测试命名空间 |
| C 的需求快捷命令、Skill 和测试 | 正文保存薄入口、来源与授权约定、闭环验收 |

执行前先重新检查两个仓库状态和适用 AGENTS.md；不要重置已有改动。使用 worktree 技能决定隔离方式，不自行切换或覆盖用户分支。C 当前不在已列出的可写根目录中，实际编辑前通过正常权限流程申请访问，或在获准的隔离 checkout 中工作；不能借临时目录搬回文件绕过限制。

每项任务按“失败测试 → 确认预期失败 → 最小实现 → 测试通过 → 本地提交”执行。编译失败可作为缺少新接口的首个失败，但随后补齐行为断言，不能用编译通过冒充验收。提交仅逐文件暂存本任务清单及配套测试；不用 `git add .`，不提交用户文件。本文 commit 步骤是后续执行动作，不在写计划时执行。

本计划补充的工程常量：新增分析入口的每个完整文本字段上限为 **20,000 个 UTF-16 code units**，按 Java `String.length()` / JS `string.length` 统一计数；若现有字段已有更严格限制，采用其更小上限并返回真实值。追加分隔符为 `\n\n—— AI 分析补充 ——\n`，空字段直接保存本次正文。仅约束新增分析入口，不修改既有模板或人工表单规则。

## 阶段一：可安全使用的读取与 CLI

### Task 1: 增加不初始化流程的领域读取

**Files:** Modify `B/src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`；Create/Test `B/src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceReadTest.java`。

**Interfaces:** Produces `DevelopmentItemWorkflowDetailDTO detailIfInitialized(DevelopmentItemType itemType, Long itemId)`；沿用现有读取权限和 DTO。无流程返回 `workflowConfigured=false`、空 nodes；绝不调用 createIfDefaultExists/requireWorkflowForUpdate。

现有 DTO 已包含 title、owner、来源项目/专题、developmentProgress、iterationPlanName 等业务摘要，并非只有流程节点；但它不等于 Task 2 的完整业务投影，例如不提供专题 storyCount。Task 1 负责实例流程读取，Task 2 另行定义业务详情来源和字段。

- [ ] **Step 1 — 写失败用例。** `missingWorkflowDoesNotInitialize` 断言 `assertThat(result.getWorkflowConfigured()).isFalse()`，验证流程、节点 mapper 没有 insert/update/delete；`pinnedWorkflowDoesNotReadCurrentDefault` 返回绑定模板版本 2，即使默认版本为 6；`unreadableProjectFailsBeforeNodeRead` 验证不可读项目拒绝且不读取节点。另保留现有页面 detail 的初始化行为回归用例。
- [ ] **Step 2 — 验证失败。** `mvn -o -Dtest=DevelopmentItemWorkflowServiceReadTest test`；应因新方法缺失或上述行为不满足失败，不应因连接真实数据库失败。
- [ ] **Step 3 — 实现只读方法。** 用现有 loadContext、workflowMapper.selectByItem 和无写入的 DTO 装配；审查 toDetail 调用链，必要时分离其惰性初始化分支，不能改变原 detail 的语义。
- [ ] **Step 4 — 验证通过。** `mvn -o -Dtest=DevelopmentItemWorkflowServiceReadTest,DevelopmentItemWorkflowServiceNodeEditTest,DevelopmentItemWorkflowServiceFieldTest test`；全部通过，mapper 写入断言成立。
- [ ] **Step 5 — 本地提交。** 逐文件暂存 Files 清单；`git commit -m "feat: add non-initializing development workflow reads"`。

### Task 2: 精确读取七类对象及安全路由

**Files:** Create `B/src/main/java/com/brad/pms/ai/connector/AiConnectorResourceReadService.java`、`B/src/main/java/com/brad/pms/integration/ai/api/AiResourceDetailDTO.java`；Modify `B/src/main/java/com/brad/pms/service/DevelopmentItemService.java`、`B/src/main/java/com/brad/pms/controller/AiConnectorController.java`、`B/src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java`、`B/src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java`；Read-only reference `B/src/main/java/com/brad/pms/service/{DevelopmentTopicManagementService,DevelopmentStoryManagementService}.java`、`B/src/main/java/com/brad/pms/security/AuthInterceptor.java` 及 `B/src/main/java/com/brad/pms/dto/response/{DevelopmentTopicListDTO,DevelopmentStoryListDTO,ProjectDTO}.java`；Test `B/src/test/java/com/brad/pms/ai/connector/AiConnectorResourceReadServiceTest.java`（新增）、`B/src/test/java/com/brad/pms/service/DevelopmentItemServiceTest.java`、`B/src/test/java/com/brad/pms/ai/connector/AiConnectorQueryServiceTest.java`、`B/src/test/java/com/brad/pms/controller/AiConnectorControllerTest.java`、`B/src/test/java/com/brad/pms/security/{AiDelegationRoutePolicyTest,AuthInterceptorTest}.java`。

**Interfaces:** Produces `AiResourceDetailDTO read(String resourceType, Long resourceId)`；DTO 为 `resourceType: String, resourceId: Long, entityVersion: Integer|null, details: Map<String,Object>`。新增 GET `/integration/ai/v1/resources/{resourceType}/{resourceId}`。details 显式投影业务 DTO，不序列化 mapper 实体或任意用户信息。

DevelopmentItemService 新增 `DevelopmentTopicListDTO detailTopic(Long topicId)`、`DevelopmentStoryListDTO detailStory(Long storyId)`，返回精确 ID 的权限感知业务投影；复用 toTopic/toStory 的字段语义而非调用 pageTopics/pageStories。管理服务当前没有可直接调用的同名详情接口；不能把 requireWritableStory 当作只读入口。Task 1 的 detailIfInitialized 用于权限感知的实际流程状态读取，不作为所有业务字段的唯一来源。

**唯一读取权限路径：** detailTopic/detailStory 先分别调用 Task 1 `detailIfInitialized(TOPIC, topicId)` / `detailIfInitialized(STORY, storyId)`，复用其既有 loadContext 中的项目可读、删除和来源一致性校验；通过后再组装目标业务投影。DevelopmentItemService 不自行重写角色/项目权限判定，不新增 requireReadableStory 或另一套 gate，也不在 Task 1 与 requireReadableTopic 之间任选。管理服务仅作现有规则的只读参考；关联摘要仍按各关联对象现有读取权限过滤。

**删除读取边界：** 精确 get/context 不开放回收站视角。已删除专题返回 notFound；故事不存在或所属专题已删除/不存在时，沿用 Task 1 领域 gate 返回 notFound。故事当前没有独立 deleted 字段，不增加此字段或虚构过滤条件；独立故事仍按现有来源与项目权限规则读取。旧 query.id 复用同一 gate，将 notFound 映射为空结果，但不吞掉 forbidden；已有回收站分页和管理权限行为不变。

**Additional test file:** 扩展 Task 1 创建的 `B/src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceReadTest.java`，加入上述删除 gate 回归；本任务提交清单包含该测试修改。

**details 投影契约（Task 3 resourceDetails 原样复用）：**

- topic：`id,title,projectId,projectCode,projectName,nodeId,nodeKey,nodeName,ownerId,ownerName,status,progress,developmentProgress,workflowConfigured,workflowStatus,storyCount,completedStoryCount,blockedStoryCount,latestBuildVersion,testStatus,blocker,sourceRequirements`；沿用 DTO 的 `id`，不另造 topicId 别名。
- story：`id,title,projectId,projectCode,projectName,nodeId,nodeKey,nodeName,topicId,topicTitle,topicWorkflowNodeId,topicWorkflowNodeName,ownerId,ownerName,status,progress,developmentProgress,workflowConfigured,workflowStatus,iterationPlanName,storyPoints,startDate,dueDate,blocker,sourceRequirements`。progress 为实例流程进度，developmentProgress 为开发进度，不能互相替代；关联名称只返回有权限读取的内容。
- project：来源为 ProjectService.detail 的 ProjectDTO；`id,version,code,name,description,status,priority,projectLevel,projectTypeId,ownerId,ownerName,orgUnitId,orgUnitName,orgUnitPath,projectManagerId,projectManagerName,startDate,endDate,progress,taskCount,doneTaskCount,memberCount,currentNodeKey,currentNodeName,workflowTemplateVersionId,createdAt,updatedAt,sourceRequirements`。`entityVersion=ProjectDTO.version`；workflowTemplateVersionId 只是绑定模板 ID，不能充当实体版本。其他类型沿用既有领域 DTO 的允许字段，不借此扩展任意实体序列化。

以上字段名稳定，空值显式为 null、集合为空数组；topic/story 没有业务实体版本时 entityVersion=null，不能用流程模板版本填充。

- [ ] **Step 1 — 写失败用例。** 对 requirement/project/topic/story/task/subtask/iteration_plan 参数化验证指定 ID；`missingIdNeverReturnsFirstListItem` 断言 not found；`taskIsNotSubtask` 拒绝无 parentId 的 task；`queryIdAlsoHonorsOtherFilters` 断言指定 id 但 status 不符返回空。`topicAndStoryDetailsIncludeBusinessProjection` 验证上述字段及故事数/所属专题/迭代名，独立专题/故事的 projectId 可为 null；`defaultTemplateDoesNotMeanInitializedWorkflow` 验证有默认模板但无实例时 configured=false、无写入。Policy 测试 `delegationResourceReadRequiresQueryScope`：DSH 委托 GET 需要 `pms:query:read`，错误方法/额外路径段/legacy AI delegation token 拒绝；预览路由需要 `pms:command:preview`。AuthInterceptorTest 增加 `ordinaryCliBearerUsesSessionAuthNotDelegationPolicy`：有效普通 CLI Bearer、活跃用户/会话通过登录分支，即使未授予 delegation scope 也不调用其 scope 校验；另测 Bearer/X-PMS-AI-Delegation 两种委托入口均按 policy 拒绝缺 scope，普通 Token 的撤销/过期/停用仍拒绝，领域不可读对象仍 forbidden。
- [ ] **Step 1a — 固定删除与权限路径用例。** DevelopmentItemServiceTest 的 `exactDetailsUseWorkflowReadGateBeforeProjection` 验证 TOPIC/STORY 与正确 ID 传入 Task 1，gate 拒绝时不继续加载投影、不调用任何写权限门；AiConnectorResourceReadServiceTest 的 `deletedTopicAndStoryWithDeletedParentAreNotFound` 验证 get/context 使用的精确来源不会返回回收站专题及其故事，独立故事仍可按既有规则读取；AiConnectorQueryServiceTest 的 `queryIdMapsDeletedNotFoundToEmptyButPreservesForbidden` 验证旧 id 查询为空而不可读仍拒绝。Task 1 ReadTest 补 deleted topic / deleted parent story 的真实 loadContext gate 回归，防止仅 Mock 出 notFound 而未覆盖领域行为；不创建新的故事 deleted 状态。
- [ ] **Step 2 — 验证失败。** `mvn -o -Dtest=AiConnectorResourceReadServiceTest,DevelopmentItemServiceTest,AiConnectorQueryServiceTest,AiConnectorControllerTest,AiDelegationRoutePolicyTest,AuthInterceptorTest test`。普通 CLI 分支用例是既有行为回归，可以先通过；新详情及路由用例应因缺实现失败。
- [ ] **Step 3 — 实现精确读取及 id 兼容。** 项目用 ProjectService.detail、需求用经现有权限路径校验后的 RequirementManagementService.detail、专题/故事用新增 DevelopmentItemService.detailTopic/detailStory、任务用 TaskService.getDetail、迭代用 IterationPlanService.detail。专题/故事按 ID 精确加载，经 Task 1 只读权限路径校验，仅加载目标所需来源/子故事/人员/迭代摘要并复用业务投影；FlowSummary 从实际实例结果构造，不能照搬现有 workflowSummaries 的 defaultConfigured 兜底；不可读关联省略，不因读取要求写权限，不改变现有分页语义。不扫描列表找 ID。旧 query.id 复用精确读取并匹配该资源已支持的筛选/keyword；不存在返回空，forbidden 不伪装成成功。新增委托 GET 匹配和缺失的 AI preview 映射，保留所有其他路由的拒绝默认值。AuthInterceptor 已仅在 aiDelegation=true 时调用 policy，应保留而非放宽；普通 CLI Token 不走 delegation policy，但仍经过登录、用户/会话、注解与领域权限校验。
- [ ] **Step 4 — 验证通过。** 重跑 Step 2，再跑 `mvn -o -Dtest=AiConnectorScopePolicyTest,TaskServiceTest,DevelopmentItemServiceTest,DevelopmentItemWorkflowServiceReadTest test`；不新增 API 直连 mapper 查询。
- [ ] **Step 5 — 本地提交。** 暂存本任务文件；`git commit -m "feat: expose permission-aware exact connector reads"`。

### Task 3: 完整实例上下文、真实组件契约及能力声明

**Files:** Create `B/src/main/java/com/brad/pms/ai/connector/AiConnectorContextAssembler.java`、`B/src/main/java/com/brad/pms/ai/connector/AiWorkflowComponentContractResolver.java`、`B/src/main/java/com/brad/pms/workflow/RequirementAnalysisFields.java`；Modify `B/src/main/java/com/brad/pms/service/{NodeService,WorkflowTemplateService}.java`、`B/src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java`、`B/src/main/java/com/brad/pms/ai/connector/AiConnectorCapabilityService.java`、`B/src/main/java/com/brad/pms/integration/ai/api/AiWorkflowContextDTO.java`、`B/src/main/java/com/brad/pms/integration/ai/api/AiCapabilityDTO.java`、`B/src/main/java/com/brad/pms/ai/node/NodeWorkbenchRegistry.java`；Test `B/src/test/java/com/brad/pms/service/{NodeServiceReadTest,WorkflowTemplateServiceReadTest}.java`、`B/src/test/java/com/brad/pms/ai/connector/AiConnectorContextAssemblerTest.java`、`B/src/test/java/com/brad/pms/ai/connector/AiWorkflowComponentContractResolverTest.java`（新增）及既有 AiConnectorQueryServiceTest/AiConnectorCapabilityServiceTest。

**Interfaces:** Consumes Tasks 1–2。Produces `AiWorkflowContextDTO assemble(String type, Long id)`，`ComponentContract resolve(WorkflowNodeDefinition definition, String runtimeNodeName, String componentKey)`；ComponentContract 是 resolver 内部 record：`statePath, fields, saveOperation, parameters, mergeMode, supported, reason`。RequirementAnalysisFields 提供 `supports(List<String> runtimeComponents, String nodeName)` 和不可变的 FIELD_NAMES/MAX_TEXT_LENGTH；Task 8 增加追加校验方法。

`resourceDetails=Task 2 read(type,id).details`，`entityVersion=Task 2 read(type,id).entityVersion`，不从列表 Item.summary 或 workflow.nodes 重建业务基础字段。尤其 project 必须包含 Task 2 明确的 owner、projectLevel、orgUnit、projectManager、日期等字段，entityVersion 直接取 ProjectDTO.version；节点读取不能代替 ProjectService.detail。

新增 `NodeService.listReadOnly(Long projectId): List<ProjectNodeDTO>` 和 `WorkflowTemplateService.getVersionNo(Long versionId): Integer`。listReadOnly 沿用 requireProjectReadable、实际绑定定义/组件绑定及 DTO 投影，禁止 applyDefaultOwners/findNodeToActivate 后的写入、成员补建和状态推进；原 list 页面行为不变。getVersionNo 按确切非空 ID 只读现有版本记录，不调用 resolveDefaultForProcessType 或要求历史版本仍为当前已发布版本；null ID 返回 null，不存在遵循 getDefinition 的错误策略。它不改变历史绑定或归档版本。

- [ ] **Step 1 — 写失败用例。** `returnsPinnedProjectNodesWithRealVersions` 用 project.version=9、templateVersionNo=2、node.version=7、workbench.version=4、fieldVersion=5 分别断言，currentNode.id 非 null。`projectResourceDetailsMatchExactGet` 深度比较 context.resourceDetails 与 get.details，断言 ownerId/projectLevel/orgUnitId/projectManagerId/startDate/endDate 存在且 entityVersion=9，与模板版本 2 不混淆；topic/story 同样验证业务字段而非只返回 nodes。`onlyActualDefaultVersionIsMarkedDefault` 对同模板的两个已发布版本，仅类型绑定的 defaultTemplateVersionId 返回 true。`receivingAnalysisUsesDedicatedReadContract` 断言接收分析不声明普通字段保存。`unreadableRelationIsOmitted` 验证关联投影不泄露不可读对象名称；`unknownComponentIsReadOnly` 断言 supported=false。`clarificationDetectionMatchesExistingUi` 验证仅有组件 key、但节点不展示澄清表单时不宣称支持。
- [ ] **Step 1a — 写项目只读失败用例。** `projectContextDoesNotDefaultOwnersOrActivateNodes` 对存在待补负责人/待激活节点的项目断言 listReadOnly/context 保留当前 owner/status/version，相关节点/成员/项目 mapper 无 insert/update/delete；原 list 的默认负责人/节点激活回归仍通过。`versionNoComesFromPinnedIdIncludingArchivedVersion` 断言指定绑定 ID 返回其真实版本号，不因默认变化或归档改读新版本；null ID 不产生兼容模板假版本，不存在返回错误。
- [ ] **Step 2 — 验证失败。** `mvn -o -Dtest=NodeServiceReadTest,WorkflowTemplateServiceReadTest,AiConnectorContextAssemblerTest,AiWorkflowComponentContractResolverTest,AiConnectorQueryServiceTest,AiConnectorCapabilityServiceTest test`。
- [ ] **Step 3 — 实现增量 DTO 和 resolver。** 保留旧 Java 构造签名作为转发构造器；新增 resourceDetails、entityVersion、workflowConfigured、relations。relations 只取一跳、最多 20 项，返回 truncated 标志；每项经过既有权限校验。Node 增加 version/ownerId/startDate/endDate/description/deliverable/editable/fieldValueVersions；Component 增加 config/state/workbenchVersion/saveContract/analysisSaveSupported/reason。字段 schema 区分 TEXT/枚举/决策/关联，绑定字段值单列，不冒充自定义字段。
- [ ] **Step 4 — 补齐能力与只读安全检查。** capabilities.features 精确键为 `resourceGet`、`richWorkflowContext`、`requirementAnalysisSave`；前两者此阶段为 true，最后一项仅注册 Task 9 命令后为 true。目录 defaultTemplate 以 ProjectTypeDTO.defaultTemplateVersionId 与实际版本 ID 比对，不能因 summary.defaultTemplate=true 将该模板全部发布版本标默认；只读修正目录，不修改实际默认绑定。项目使用新增 NodeService.listReadOnly、getVersionNo 与实际绑定定义及安全的 NodeWorkbench reader；当前 NodeService.list 会补默认负责人并激活节点，不能从 AI 只读路径调用。读取有副作用的 reader 本阶段标只读不可读取及原因，不调用它。editable/actions 取现有服务权限和节点状态；不能只依据 scope 返回全部命令，也不能仅用 status=1 猜测编辑权限。
- [ ] **Step 5 — 验证通过。** 重跑 Step 2，回归 Task 1 无业务写入断言及 `mvn -o -Dtest=NodeServiceOwnerDefaultsTest,NodeServiceCompleteTest,WorkflowTemplateServiceSelectionTest test`；对无权限、已完成、无模板和多个澄清候选用例确认原因可解释、目标不自动选择。
- [ ] **Step 6 — 本地提交。** 暂存本任务文件；`git commit -m "feat: publish accurate workflow context and component contracts"`。

### Task 4: 连接器 schema、精确 get、CLI 解析及错误闭环

**Files:** Modify `C/packages/pms-contracts/src/{query,capability,capability-normalizer,index}.ts`、`C/packages/pms-client/src/PmsClient.ts`、`C/apps/pms-cli/src/main.ts`、`C/apps/pms-cli/src/commands/core/{get,workflow,operation}.ts`；Create `C/apps/pms-cli/src/cli/parseArguments.ts`、`C/apps/pms-cli/src/cli/runCli.ts`；Test `C/tests/unit/cli-runner.test.ts`（新增）、`C/tests/unit/pms-client.test.ts`、`C/tests/contract/{schema,capability-normalizer,pms-client,harness-output}.test.ts`；Modify `C/docs/cli-command-contract.md`、`C/docs/compatibility-matrix.md`。

**Read-only reference:** `C/packages/pms-contracts/src/operation.ts`。当前 operationResultSchema.data 使用 `z.record(z.string(), z.unknown()).default({})`，已可透传 data.workflow；不为快照单独把 data 改成封闭 schema。跨 schema/client/CLI 的快照保留契约测试归属 Task 6，Task 4 保持该通道兼容。

**Interfaces:** Produces `PmsClient.get(resource: ResourceRef): Promise<ResourceDetail>`，ResourceDetail 对应 Task 2 DTO；扩展 WorkflowContext 对应 Task 3。产生 `parseCliArguments(argv: string[]): CliInvocation` 和 `runCli(argv: string[], deps: CliDependencies): Promise<void>`。CliInvocation 为 `{group:string;command?:string;positionals:string[];options:Record<string,string>;filters:Record<string,string>}`。CliDependencies 内含 `createRuntime(requestId:string): Promise<{client:PmsClient}>`、`runAuth(command:string,args:string[],requestId:string):Promise<unknown>`、`writeStdout(text:string):void`、`writeStderr(text:string):void`、`setExitCode(code:number):void`、`requestIdFactory():string`；不导入即执行 main。workflowAction 签名改为 `workflowAction(client:PmsClient,resourceType:ResourceRef['type'],resourceId:number,action:string,idempotencyKey:string,options:OperationOptions={}):Promise<OperationResult>`，不从 requestId 推导幂等键。

- [ ] **Step 1 — 写失败用例。** `keepsNestedContextMetadata` 深度相等断言 Task 3 所有新字段；`oldServerCannotFakeExactGet` 验证缺 features 时无 query fallback；`filterFormsAreEquivalent` 比较 `--filter status=ACTIVE` 和 `--filter=status=ACTIVE`，多个不同筛选键均保留。同名 filter 重复、缺值、非法 JSON、unsafe integer ID、未成对 context/contract flags、缺幂等键均在 createRuntime 前失败。`errorKeepsRequestIdentity` 断言 401/403/409 分类和请求 ID 保留、stderr JSON/非零退出、stdout 无错误日志、Token 脱敏；help/version 不启动认证。
- [ ] **Step 2 — 验证失败。** `pnpm exec vitest run tests/unit/cli-runner.test.ts tests/unit/pms-client.test.ts tests/contract --no-cache`。
- [ ] **Step 3 — 实现 schema、client 和薄 runner。** 所有新字段在旧响应中 optional；features 缺失等价于未支持，不伪造。规范化器显式保留嵌套 contract/config/state。get 检查 resourceGet 后调用新增 endpoint；context 保留元数据。解析先于认证；未知 flags 拒绝，同名重复 filter 拒绝，值允许包含 `=`。一次命令生成一个 requestId，通过 client requestIdFactory 及操作选项贯穿所有请求和输出；服务器有自己的 requestId 时保留关联信息，不另随机生成替代 ID。保留 PmsClientError.kind/status、脱敏信息；未知异常才 CLI_ERROR。写操作不自动重试；workflow 恢复必须使用显式原 context/contract 和版本，禁止重新取最新上下文重建原写请求。
- [ ] **Step 4 — 验证通过。** 重跑 Step 2；`pnpm run typecheck`；`pnpm run build:cli`；`node dist/pms-cli.js --help`，应有新 flags 说明且不连接 PMS/Keyring。
- [ ] **Step 5 — 本地提交。** 暂存本任务文件；`git commit -m "fix: preserve connector context and strict CLI request semantics"`。

**阶段一关口：** Tasks 1–4 的自动化检查通过，读取不初始化流程、精确 ID 不串对象、旧服务器不误导写入、版本类型不混用。此时新增分析保存能力必须仍为 false，不能先发布一个不可执行的命令。

## 阶段二：创建项目闭环

### Task 5: 隔离账号凭据与浏览器授权/刷新

**Files:** Modify `C/apps/pms-cli/src/auth/{AuthStore,BrowserAuth}.ts`、`C/apps/pms-cli/src/cli/runCli.ts`；Create `C/apps/pms-cli/src/auth/RefreshCoordinator.ts`；Test `C/tests/unit/auth.test.ts`、`C/tests/unit/auth-refresh.test.ts`（新增）；Modify `B/src/main/java/com/brad/pms/controller/CliAuthController.java`（注入 Clock，默认 systemUTC 以可测过期）及 `B/src/test/java/com/brad/pms/controller/CliAuthControllerTest.java`。

**Interfaces:** AuthStore 保留 `new AuthStore(keyring?)`，增量第二参数 options `{service?:string}`，新增 `getAccount(baseUrl:string,accountId:string):Promise<Account|null>`；正式默认 service 仍 pms-cli，测试使用 `pms-cli-e2e-<runId>`。凭据 identity 是去 query/hash/末尾 slash 后的完整 baseUrl（保留部署路径）+accountId；key 使用 identity 的 SHA-256，不把 URL 中的秘密写入键。Produces `withRefreshLock<T>(identity: string, action: () => Promise<T>): Promise<T>`；锁 identity 同时包含 namespace，BrowserAuth.refresh 在锁内调用 getAccount 读取最新凭据再轮换。

- [ ] **Step 1 — 写失败用例。** `sameUserIdDifferentPmsNeverOverwrites` 对两个地址+账号 7 分别读取正确凭据；`e2eStoreDoesNotChangeDefaultActiveAccount` 断言默认 active-account 未变；`legacyReadDoesNotDeleteOrMigrateCredentials` 保留旧键只读兼容。`concurrentRefreshUsesRotatedCredential` 让第二请求读到第一次的新 refreshToken，最终 store 保留最新值；失败刷新不得删凭据/切账号。后端覆盖错误 PKCE、过期/重复 code，客户端覆盖错误 state 不保存凭据及失败回调关闭监听器。
- [ ] **Step 2 — 验证失败。** C：`pnpm exec vitest run tests/unit/auth.test.ts tests/unit/auth-refresh.test.ts --no-cache`；B：`mvn -o -Dtest=CliAuthControllerTest test`。
- [ ] **Step 3 — 实现安全存储与刷新协调。** Keytar adapter service 可注入；真实 E2E 的 runtime 与 auth 使用同一测试 namespace。地址 URL 禁止 userinfo。跨进程锁用当前用户专属、权限 0700 的临时锁目录及排他创建，文件只记 PID/时间，不存 Token；等待最多 5 秒后明确冲突退出，不无限重试。仅在确认锁持有进程已不存在时回收其确切锁文件；不按时间直接夺取活跃锁。refresh 不缓存过期 account 参数，不改变后端单次轮换规则。
- [ ] **Step 4 — 验证通过。** 重跑 Step 2、`pnpm run typecheck`；加入两个子进程争用锁的测试并验证一方等待后使用新凭据，或明确忙碌失败而不破坏会话。撤销后一次请求明确 unauthorized；不循环重登或输出 Token。
- [ ] **Step 5 — 本地提交。** 两仓分别逐文件暂存并提交 `fix: isolate CLI credentials and coordinate refresh` / `test: cover CLI authorization expiry and replay`。

### Task 6: 创建项目入口及自动化闭环

**Files:** Modify `C/apps/pms-cli/src/cli/{parseArguments,runCli}.ts`、`C/apps/pms-cli/src/commands/shortcuts/project.ts`、`C/packages/pms-capabilities/src/CapabilityResolver.ts`；Test `C/tests/unit/project-create-flow.test.ts`（新增）、`C/tests/unit/cli-runner.test.ts`、`C/tests/contract/capability-resolver.test.ts`；Test/Modify `B/src/main/java/com/brad/pms/ai/command/project/CreateProjectCommand.java`、`B/src/test/java/com/brad/pms/ai/command/project/ProjectCommandTest.java`、`B/src/test/java/com/brad/pms/ai/command/AiOperationAutomaticExecutionTest.java`。

**Additional test file:** Create/Test `C/tests/contract/project-create-readback.test.ts`；只读参考 Task 4 的 operation.ts，不要求改变当前透传 schema。

**Interfaces:** CLI 注册 `pms project create --arguments-json <json> --idempotency-key <key>`，复用既有 projectCreate/operation execute；项目类型及模板版本仍用既有参数名 projectTypeId/workflowTemplateVersionId。project.ts 新增 `createProjectWithReadback(client:PmsClient,options:WriteOptions):Promise<ProjectCreateReadback>`，其返回类型为 `{result:OperationResult;resource:ResourceDetail|null;context:WorkflowContext|null;warnings:string[]}`；普通 projectCreate 保留原薄写入行为，CLI 新入口用新闭环函数。DynamicCapabilityResolver 增加 `templateCandidates(processType:string,name:string):WorkflowCapability[]` 返回全部精确同名候选；既有 template 未给 ID 时只返回唯一明确默认，否则 undefined，不回退 templates[0]。CreateProjectCommand 保留 `data.project` 的 ProjectDTO 原字段和语义（包括 version、workflowTemplateVersionId）；仅新增同级 `data.workflow: {templateVersionId:Long|null;templateVersionNo:Integer|null;nodeIds:Long[]}`，无绑定时为 null/null/空数组。这样不在 project 下制造第二个模板 ID 别名，也不改变 ProjectDTO.version 的实体版本语义。快照来自本次成功创建实例实际绑定的模板和初始化节点，不取当前默认版本；Task 4 OperationResult schema 及规范化器须保留此增量数据。

- [ ] **Step 1 — 写失败用例。** `createsWithExactPublishedTemplateAndReadsBack` 断言 preview→execute→get→context 顺序，参数不擅自改写，result.data.project.id 与精确详情 ID 相同，result.data.workflow.templateVersionId 等于 data.project.workflowTemplateVersionId，模板版本号/节点 ID 与实例 context 一致。`createdWorkflowSnapshotDoesNotFollowChangedDefault` 用 project.version=9、绑定模板版本号 2、默认版本号 6 断言实体版本仍为 9、结果模板仍为 2；更改默认或幂等重放不重算历史创建快照。`readbackFailureKeepsKnownSuccessfulCreation` 断言创建成功但回读失败时仍返回原 operation/project ID、warnings，不再次创建。`ambiguousTemplateNameDoesNotSelectFirst` 在模板解析契约测试中验证多个同名发布版本不能自动选取；`defaultIsResolvedByBackend` 不在 CLI 写死默认 ID。后端拒绝草稿、错误流程类型、无权限组织、反向日期；`sameKeyAndPayloadReturnsSameProject` 断言 execute 仅一次；同 key 改参数/version/contract 明确 conflict；预览不创建项目。
- [ ] **Step 1a — 补快照透传契约断言。** 在 project-create-readback.test.ts 增加 `workflowSnapshotRoundTripsIntoCliReadback`：真实 operationResultSchema/PmsClient 配合 Mock HTTP 响应，将 `data.workflow={templateVersionId:42,templateVersionNo:2,nodeIds:[101,102]}` 从响应解析传递到 createProjectWithReadback，深度断言 ProjectCreateReadback.result.data.workflow 原样保留，CLI runner 的成功 JSON 输出同样保留；data.project.version=9 不变。无绑定的 null/null/空 nodeIds 也原样保留；旧响应缺 data.workflow 时仍可解析，不伪造快照。回读失败仍保留原 result 快照，幂等返回经同一通道不丢字段。该测试验证协议传递，不冒充真实授权/创建验收。
- [ ] **Step 2 — 验证失败。** C：`pnpm exec vitest run tests/unit/project-create-flow.test.ts tests/unit/cli-runner.test.ts tests/contract/capability-resolver.test.ts tests/contract/project-create-readback.test.ts --no-cache`；B：`mvn -o -Dtest=ProjectCommandTest,AiOperationAutomaticExecutionTest test`。schema 现有透传部分可以先通过；完整 CLI 闭环部分应因新入口缺失失败。
- [ ] **Step 3 — 实现创建闭环。** 新闭环函数先只读 preview，再调用原 projectCreate，获得 data.project.id 后精确 get/context；回读失败保留已知成功结果并给 warning，不能把它描述为“创建失败，请换键再试”。CLI 快捷命令沿用用户给定 ID；模板名称候选解析交给能力目录/Skill，不增加第二套模板选择器。领域模板校验复用 ProjectService；preview 若需校验模板，使用同一只读校验方法。CreateProjectCommand.execute 在创建成功的同一命令事务内，按返回 ProjectDTO.workflowTemplateVersionId 调用 Task 3 getVersionNo、用 listReadOnly(project.id) 读取实际初始化节点 ID，组装 data.workflow；不按默认模板重新解析，不把模板版本写入 data.project.version，保留既有 result.data.project 字段。重复请求直接返回存储的既有结果，不再次初始化节点或重建快照。
- [ ] **Step 4 — 验证通过。** 重跑 Step 2、connector typecheck；覆盖能力仅可读而无 create 操作时快捷入口不发执行请求、直接调用后端也拒绝。
- [ ] **Step 5 — 本地提交。** 两仓分别提交 `feat: complete CLI project creation readback` / `test: pin project creation template and idempotency rules`。

### Task 7: 隔离环境的真实创建项目验收

**Files:** Create `C/tests/e2e/project-create.e2e.test.ts`、`C/tests/e2e/support/isolatedSession.ts`、`C/tests/unit/e2e-safety.test.ts`；Modify `C/package.json`、`C/docs/e2e-report.md`、`C/docs/installation.md`。

**Interfaces:** E2E 必需 flags：`PMS_E2E_WRITE=true`、`PMS_E2E_ISOLATED=true`；必需配置 `PMS_E2E_BASE_URL`、`PMS_E2E_AUTH_ORIGIN`、`PMS_E2E_ACCOUNT_ID`、`PMS_E2E_RUN_ID`。Account ID 只是校验浏览器实际批准身份的期望值，不是免密登录；不使用管理员 Token 替代授权。新增 script `verify:real-pms:project-create` 仅运行本测试，不自动启用 flags。

- [ ] **Step 1 — 写失败用例。** support 单元断言缺任一条件时在认证/写入前拒绝，namespace 仅允许安全 runId；`cleanupOnlyUsesRecordedProjectIds` 断言测试清单外 ID 永不删除，删除失败记录 ID 且不扩大清理。测试身份与 capability.viewer.id 不符立即停止，不创建项目。
- [ ] **Step 2 — 验证失败与关闭默认。** 自动化 support 测试应先 FAIL；真实文件无 flags 时只显示 skipped，不能出现创建/删除请求。为 support 新增 `C/tests/unit/e2e-safety.test.ts`，Run `pnpm exec vitest run tests/unit/e2e-safety.test.ts --no-cache`。
- [ ] **Step 3 — 实现真实流程。** 用 BrowserAuth+隔离 Keytar service 请求浏览器批准；Task 6 创建唯一名 `AI-E2E-<runId>` 的最小项目；重放同一完整请求并核对 operationId/projectId；按精确 get/context 查验，无 SQL。finally 仅通过 project.delete 清理本次成功创建的项目 ID，并撤销/移除本次测试会话。若账号无删除权限，列出残留 ID，不为清理申请管理员权限。
- [ ] **Step 4 — 验证安全测试与真实场景。** 先通过 Step 2；只有获得上述隔离环境/普通身份且用户允许浏览器批准时运行 `pnpm run verify:real-pms:project-create`。页面核对标题、日期、模板、节点。缺配置则记录“真实验收未运行”，不中断可安全进行的离线开发，但阶段二不得标为完整通过。
- [ ] **Step 5 — 本地提交。** 暂存上述文件及 e2e-safety.test.ts；`git commit -m "test: add isolated project creation acceptance flow"`。

**阶段二关口：** 自动化创建、模板、授权和幂等检查通过；真实验收单独记录是否完成。Task 7 的权限或环境缺失不授权修改现有项目、替换登录账号或读取生产凭据。

## 阶段三：需求分析 → 正文保存 → 来源沉淀

### Task 8: 仅追加分析正文的领域更新

**Files:** Modify `B/src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`、`B/src/main/java/com/brad/pms/workflow/RequirementAnalysisFields.java`；Create `B/src/main/java/com/brad/pms/dto/request/RequirementAnalysisSaveCmd.java`；Test `B/src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceAnalysisTest.java`、`B/src/test/java/com/brad/pms/workflow/RequirementAnalysisFieldsTest.java`（新增）。

**Interfaces:** Produces `DevelopmentItemWorkflowDetailDTO appendRequirementAnalysis(Long requirementId, Long nodeId, RequirementAnalysisSaveCmd cmd)`；cmd 为 `version: Integer, patch: Map<String,JsonNode>`。RequirementAnalysisFields 增加 `Map<String,JsonNode> append(Map<String,JsonNode> currentFields, Map<String,JsonNode> patch)`，只在 `__components.requirement-node-workbench` 下修改 background/acceptanceCriteria；所有其他层/键保留。

- [ ] **Step 1 — 写失败用例。** `appendPreservesHumanTextAndProtectedState` 断言 background 为原文+确切分隔符+新文，conclusion/systemId/targetType/targetId/owner/schedule/status/绑定字段深度不变；verify 系统/执行对象同步服务 never。`nonFunctionalHistoryDoesNotClearSystem` 保留已有领域 systemId；`unknownNestedKeysArePreserved` 保留其他组件及未知键；`nullComponentCanBeInitializedWithoutLosingSiblings` 只允许缺失/null 的目标容器转对象，遇到其他错误类型拒绝。
- [ ] **Step 1a — 写多段追加失败用例。** `multiPassageAppendPreservesAllFourParagraphs`：原文 `背景第一段\n\n背景第二段`，追加 `分析第一段\n\n分析第二段`；期望严格等于原文+`\n\n—— AI 分析补充 ——\n`+新文，四段顺序/换行/文本完整，分隔标记仅一次；未提供 acceptanceCriteria 时其已有多段正文深度不变。不能将其解析成单段、覆盖原两段或顺带追加其他字段。
- [ ] **Step 2 — 补边界失败用例并运行。** 未授权、错误归属、完成/锁定不可编辑节点、无既有流程、改名后不展示澄清、空 patch、未知键、null/空白/非文本、已有值非文本均拒绝；20,000 恰好通过、20,001 拒绝，emoji 用 UTF-16 计数；版本冲突或 updateById=0 不保存。Run `mvn -o -Dtest=DevelopmentItemWorkflowServiceAnalysisTest,RequirementAnalysisFieldsTest test`，应 FAIL。
- [ ] **Step 3 — 实现正文规则。** 按本文工程常量追加，在合并前后校验长度；返回新的嵌套 JSON 树，不修改输入对象，失败校验不留下半合并结果。保留用户原有文本内容，不 trim 原文或截断正文。对输入空白只做拒绝判断，不以清理空白为由改写历史数据。
- [ ] **Step 4 — 实现受限领域事务。** 权限沿用 requireWritableItem；按流程→节点稳定顺序锁定，使用现有 workflowMapper.selectForUpdate/lockWorkflowNodes/requireLockedNode，不通过会初始化流程的 requireWorkflowForUpdate。重复确认节点归属、实际绑定组件、编辑策略及 version。只调用正文规则并更新节点数据/版本，不调用通用 updateNode 或绑定/系统/目标同步；返回 Task 1 只读详情。一个事务内校验和写入，避免在命令层提前拼装旧内容。
- [ ] **Step 5 — 验证通过。** 重跑 Step 2，以及 `mvn -o -Dtest=DevelopmentItemWorkflowServiceNodeEditTest,DevelopmentItemWorkflowServiceFieldTest,DevelopmentItemWorkflowServiceReadTest test`；验证既有人工作台保存行为未变。
- [ ] **Step 6 — 本地提交。** 暂存本任务文件；`git commit -m "feat: append requirement analysis without workflow side effects"`。

### Task 9: 注册 analysis.save 命令、契约、幂等与审计

**Files:** Create `B/src/main/java/com/brad/pms/ai/command/requirement/SaveRequirementAnalysisCommand.java`；Modify `B/src/main/java/com/brad/pms/ai/command/{CommandName,PmsCommandMetadata}.java`、`B/src/main/java/com/brad/pms/ai/connector/{AiWorkflowComponentContractResolver,AiConnectorCapabilityService}.java`；Test `B/src/test/java/com/brad/pms/ai/command/requirement/SaveRequirementAnalysisCommandTest.java`、`B/src/test/java/com/brad/pms/ai/command/RequirementAnalysisTransactionIntegrationTest.java`（新增）、`B/src/test/java/com/brad/pms/ai/command/{PmsCommandRegistryTest,AiOperationAutomaticExecutionTest,PmsCommandScopeGuardTest}.java`、`B/src/test/java/com/brad/pms/ai/connector/AiConnectorCapabilityServiceTest.java`。

**Interfaces:** `CommandName.REQUIREMENT_ANALYSIS_SAVE` → `requirement.analysis.save`；参数白名单 requirementId/nodeId/version/patch，patch schema 仅两字段；scopes 含 `pms:requirement:write`、既有 command preview/execute。risk=medium，mergeMode=append，refreshScopes=`requirement-detail,development-detail`。preview(CommandPreviewRequest)→CommandPreview；execute(AiOperationDO)→CommandResult，data 含 requirementId/nodeId/nodeVersion/workflow。

- [ ] **Step 1 — 写失败用例。** preview 只读且展示追加前后长度/影响字段、不触发写入；execute 仅调用 Task 8 方法，不调用 updateNode/completeNode。参数系统归属/结论/状态被拒绝。catalog 新命令只在注册后出现，组件字段契约与领域上限一致；readonly 及缺 requirement-write 的委托 Token 不可 preview/execute。
- [ ] **Step 2 — 写幂等失败用例并运行。** `lostResponseReplaysOriginalAnalysisResult` 模拟第一次成功、调用方未收到，然后节点已变化；同 key+完整原请求返回原 operationId，仅一次领域追加，新的 requestId 可不同。相同 key 换 version/patch/contextVersion/contractVersion 均 conflict；事务失败不产生 SUCCEEDED 审计/残留正文。Run `mvn -o -Dtest=SaveRequirementAnalysisCommandTest,PmsCommandRegistryTest,AiOperationAutomaticExecutionTest,PmsCommandScopeGuardTest,AiConnectorCapabilityServiceTest test`。
- [ ] **Step 3 — 实现薄命令。** 用既有参数 reader/支持类校验预览；执行调用领域方法，既有 AiOperationService 保持完整请求比对，幂等重放在重新执行正文校验前返回原结果。新命令通过 registry 自动注册，更新 exhaustive metadata switch；不修改既有 Agent YAML 来扩权。带旧 contract 的请求仍按原授权列表拒绝，无 contract 的正常 CLI 请求沿用既有机制。
- [ ] **Step 4 — 验证通过与事务回滚。** 重跑 Step 2，附加 `mvn -o -Dtest=AiCommandContractTest,PmsAgentContractSetTest,AiConnectorControllerTest,AutomaticCommandExecutionServiceTest test`；确认操作 ID、用户名/来源 client、requestId 和新版本可追溯，不在日志打印完整正文。新增隔离 MySQL Testcontainers 集成测试，通过业务服务创建 fixture，不能照搬既有测试的手工 SQL，也不使用可能连接用户数据库的默认 profile；现有 application-test.yml 使用 jdbc:tc:mysql，新测试须明确拥有独立容器。fixture 创建后先用 Task 1 detailIfInitialized(REQUIREMENT, fixtureId) 断言 workflowConfigured=true、目标 nodeId 属于该实例且分析字段可写，证明测试使用真实初始化结果而非靠只读入口补建流程。随后模拟正文更新后操作记录更新抛异常，经 Spring 代理事务执行，再通过业务读取断言正文/version/操作状态整体回滚；并发同 key 仅追加一次。只有 `PMS_TEST_ISOLATED_MYSQL=true` 且确认使用新容器时运行 `mvn -o -Dtest=RequirementAnalysisTransactionIntegrationTest test`；此开关仅控制本任务新增事务测试，缺容器时明确未运行，Mockito 不能证明真实事务回滚。
- [ ] **Step 5 — 本地提交。** 暂存本任务文件；`git commit -m "feat: expose restricted requirement analysis command"`。

### Task 10: CLI 分析保存入口、Skill 和来源约定

**Files:** Modify `C/apps/pms-cli/src/commands/shortcuts/requirement.ts`、`C/apps/pms-cli/src/cli/{parseArguments,runCli}.ts`、`C/skills/pms-project-management/SKILL.md`、`C/docs/{cli-command-contract,cli-coverage-matrix,security-model}.md`、`C/README.md`；Test `C/tests/unit/requirement-analysis.test.ts`、`C/tests/contract/analysis-save-flow.test.ts`、`C/tests/contract/skill-analysis-contract.test.ts`（新增）及 cli-runner.test.ts。

**Interfaces:** Produces `requirementAnalysisSave(client: PmsClient, requirementId: number, options: OperationOptions & {nodeId:number;version:number;idempotencyKey:string}): Promise<OperationResult>`。注册 spec 的 `pms requirement analysis save <id> --node-id <id> --version <version> --arguments-json <patch-json> --idempotency-key <key>`；保留通用 context/contract/request-id flags，恢复用完整原值。

- [ ] **Step 1 — 写失败用例。** 精确路由不与 requirement create/update 冲突；arguments.version 使用传入节点版本 7，不能用 template 2；要求两个字符串白名单、至少一项非空、不支持节点不执行。`originalEnvelopeIsReplayedAfterLostResponse` 检查 key、参数、context、contract 完全相同，execute 不自动重试且不因 context 回读改版本；保存后 get/context 回读 data.nodeVersion 对应变化。
- [ ] **Step 2 — 写来源与 Skill 契约失败用例并运行。** 正文里的标题/地址/实际查阅日期/支持结论在回读中原样保留；恶意来源里的“完成节点/上传凭据”不产生相关命令，缺来源不伪造链接。Skill 文本测试必须包含“仅分析不保存”“明确授权才保存”“事实/推断/假设分开”“无目标只输出草稿”“不批准/不完成/不改系统”“原请求恢复”等规则；注明这些静态测试不能证明所有模型都会遵守。Run `pnpm exec vitest run tests/unit/requirement-analysis.test.ts tests/unit/cli-runner.test.ts tests/contract/analysis-save-flow.test.ts tests/contract/skill-analysis-contract.test.ts --no-cache`。
- [ ] **Step 3 — 实现薄入口与 Skill。** wrapper 只包装后端参数并核对 capabilities/context 契约，不自行合并正文；原请求的 caller 保持不可变，不在每次重试时重新选 target。Skill 更新前使用 writing-skills 技能，只改仓库内 Skill，不安装全局。正文为现有文本，使用 spec 章节与来源格式，不增加 renderer/网页抓取器；文档明确追加语义、20,000 完整字段限制、错误分类和旧服务器升级提示。
- [ ] **Step 4 — 验证通过。** 重跑 Step 2、`pnpm run typecheck`、`pnpm run build:cli`；help 显示完整入口。没有 analysis.save 能力时明确未保存；多个候选节点不取第一项。人工审查 Skill 注入边界，并把行为示例与静态测试分别记录。
- [ ] **Step 5 — 本地提交。** 暂存本任务文件；`git commit -m "feat: add safe requirement analysis CLI workflow"`。

### Task 11: 真实分析保存、页面回读及最终交付记录

**Files:** Create `C/tests/e2e/requirement-analysis.e2e.test.ts`；Modify `C/package.json`、`C/docs/e2e-report.md`、`C/docs/compatibility-matrix.md`、`C/CHANGELOG.md`；F 现有 RequirementNodeWorkbenchComponent.vue 只读参考，无源码修改。

**Interfaces:** 复用 Task 7 隔离 session/config，额外要求用户指定 `PMS_E2E_REQUIREMENT_ID`、`PMS_E2E_NODE_ID`（可写隔离测试需求/节点），script=`verify:real-pms:analysis-save`。不从列表选择任意需求，不自动创建/完成前置节点，也不自动“清理”用户需求的历史正文。

- [ ] **Step 1 — 写失败用例。** 测试开关关闭/缺 fixture 时无写入；fixture 的背景已有原文，保存唯一 runId 报告并验证原文保留、来源可回读、结论/system/target/owner/dates/status 深度不变；原请求重放报告 runId 仅出现一次。测试仅断言源信息被保存，不宣称后端验证外部资料真实性。
- [ ] **Step 2 — 验证默认关闭。** `pnpm exec vitest run tests/e2e/requirement-analysis.e2e.test.ts --no-cache`，无 flags 应 skipped，网络/数据库零业务写入。边界行为在 unit/contract 有同等断言，不能以 skipped 作为通过。
- [ ] **Step 3 — 获准后执行真实验收。** `pnpm run verify:real-pms:analysis-save`；浏览器检查现有需求澄清页面显示追加正文和验收标准，无新弹窗、未自动完成节点。保留本次 fixture 内容供用户检查，报告确切需求/节点/operation ID；不私自恢复或删除正文。缺指定 fixture 标记未运行。
- [ ] **Step 4 — 全量安全回归。** B：`mvn -o test`（先核对测试默认使用 mocks/隔离 Testcontainers，不连接用户数据库；不具备隔离依赖则记录未运行部分并运行本计划选定测试）。C：`pnpm exec vitest run tests/unit tests/contract --no-cache`、`pnpm run typecheck`、`pnpm run build:cli`；`git diff --check` 分别在两个仓库执行。真实 E2E 不与全量默认测试混跑，安装/global Skill E2E 不启用。
- [ ] **Step 4a — 分类记录环境限制。** 现有 application-test.yml 依赖 Testcontainers MySQL，部分 SpringBootTest 未显式激活 test profile，不能假定整套测试默认安全隔离。全量测试前核对有效 profile/数据源和容器可用性；无法保证隔离则不运行全量，不改成用户本地数据库来绕过限制。Docker/Testcontainers 或离线 Maven 依赖缺失时记录具体未运行/环境失败测试及原因，只将实际成功的指定测试记为“局部回归通过”。`PMS_TEST_ISOLATED_MYSQL=true` 仅用于 Task 9 的新增事务用例，不代表其他集成测试通过。只有完整安全测试集实际执行且退出成功，才能称“全量回归通过”；局部 Mock、Task 9 容器测试、真实 CLI E2E 和页面核对分别记录，互不替代。
- [ ] **Step 5 — 记录交付并本地提交。** e2e-report 分列自动化、真实授权、真实创建、真实分析、页面核对及未运行原因；不把阶段一新增 feature 等同于真实场景验收完成。记录两个仓库 commit ID、不修改发布版本号、不推送。逐文件暂存；`git commit -m "docs: record CLI lifecycle acceptance results"`。

## 计划自审与执行交接

### 用户评审核实及修订（2026-10-10）

| 意见 | 核实结论及落点 |
| --- | --- |
| 1. 专题/故事来源不明确（高） | 契约缺口成立；“流程 DTO 没有业务字段”不准确，管理服务也无现成 detail。Task 2 明确新增 DevelopmentItemService 精确投影、字段、读取权限和真实流程配置状态；Task 1 不承担全部业务详情。 |
| 2. 项目基础字段及实体版本（中） | 成立。Task 2 明确 ProjectDTO 投影，Task 3 复用同一 details，补 get/context 一致性及实体/模板版本区别测试；不把 projectItem 列表 summary 当 context。 |
| 3. CLI 与委托授权边界（中） | 补充成立；普通 Token 已经不会走 policy，并非需要修复当前拦截器。Task 2 将委托规则测试放 PolicyTest，普通登录分支及双委托入口回归放 AuthInterceptorTest；不扩大任何路由权限。 |
| 4. 创建结果实例模板与版本（低） | 成立。Task 6 保留 data.project，增量 data.workflow 区分模板快照；真实绑定读取、默认变化、实体版本及幂等历史快照均有断言。 |
| 5. 全量 Maven 测试前提（低） | 成立。Task 9/11 区分独立容器事务测试、选定回归和全量；环境限制不记为验收通过，也不回退连接用户数据库。 |
| 多段追加、fixture 可读性 | Task 8 增加四段原样追加；Task 9 增加非初始化读取确认真实 fixture。Task 10 精确三词路由与 Node.js/pnpm 版本约定保留。 |
| 额外发现：项目节点读取副作用 | 当前 NodeService.list 会补默认负责人、激活节点。Task 3 新增 listReadOnly 并验证无写入，Task 6 使用该方法生成快照；按绑定 ID 读取模板版本号，原页面行为不变。 |
| 第二轮补充：快照透传通道 | Task 4 将 operation.ts 列为只读参考，Task 6 明确契约测试文件/运行命令，验证快照到达 ProjectCreateReadback 和 CLI JSON，兼容无绑定及旧响应。 |
| 第二轮补充：删除读取边界 | Task 2 声明 get/context 不开放回收站；已删除专题及其故事沿用领域 gate 返回 notFound，旧 query.id 映射为空；不新增故事 deleted 字段。 |
| 第二轮补充：唯一权限路径 | Task 2 固定调用 Task 1 detailIfInitialized，不新建另一套权限 gate、不调用写门；投影服务只装配业务字段，关联权限沿用既有服务。 |

上述核实基于当前源码；没有在本轮重新运行业务测试或验证本机 Docker 状态，不据此确认最近全量测试失败的具体原因。

- [x] Spec coverage：设计 §§4→Tasks 1–4；§5→Tasks 5–7；§6→Tasks 8–11；§7 的安全、兼容、来源、版本及幂等用例均有归属。
- [x] Step scan：每任务有具体文件、接口、失败断言、运行命令、最小实现和本地提交；真实测试条件单列，无 SQL/全局安装步骤。
- [x] Type consistency：DTO 名称、features 键、CLI flags、命令名、追加路径和 UTF-16 长度规则在任务间一致；version 不复用模板版本。
- [x] Review Focus：五项输入风险分别落入 Tasks 2、4、5、8、9–10；正文副作用、凭据隔离和原请求恢复不靠泛泛的“回归测试”兜底。
- [x] Proportion：只固定接口、工程限制和测试断言，不预写函数体；后端/CLI 两端依赖同一协议，不拆成独立重复设计。

本计划是待审阅的实施清单，不是实施完成证明。计划审阅后再开始业务改动。建议本会话逐任务执行（Native），因为跨仓库协议与版本规则连续依赖，维持同一实现上下文更便于核对；执行结束请独立 reviewer 检查整组改动。也可选择逐任务实现及独立审查（Subagent-driven），相应使用技能，不把该选择解释为另建用户任务。
