# AI 只读流程上下文设计方案

日期：2026-10-14（Asia/Shanghai）

修订：第二版，已纳入权限、持久化状态、聚焦读取和深度评审修正。本次只修订文档，不实施业务代码。配套计划：docs/superpowers/plans/2026-10-14-ai-readonly-workflow-context.md。

## 1. 目标与边界

让外部 AI 工作台通过 pms context，以当前 PMS 用户身份读取项目、需求、专题、故事的真实流程状态与已有成果。统一读取协议，保留两套领域流程实现。

交付纯读入口、实际节点与版本、组件配置与已保存内容、明确缺失状态、聚焦读取、CLI 类型化透传和分层验收。先证明一个项目和一个需求的纵向流程，再扩展全部组件及专题/故事验收。

不包含流程表合并、流程引擎重构、新聊天页面、模型调度、无人值守监控、自动推进、保存契约或写命令改造。任务首版只返回统计，不能声称支持定位具体阻塞任务或完整项目推进建议。

技术约束：Java 17、当前 Spring Boot/MyBatis-Plus/Jackson；MySQL 8；CLI 沿用 TypeScript、Node.js 22+、Zod 4、pnpm 10.18.2。无新增生产依赖、无数据库迁移。保留已有无关改动，不读取、修改或提交 opencode.json。

## 2. 已核实的代码事实

| 当前行为 | 修正 |
| --- | --- |
| projectContext 从模板拼节点，ID 为 null、字段值为空 | 从实际项目节点和工作台装配 |
| NodeService.list 补负责人、成员并激活节点 | 新增纯读入口，保留原页面方法 |
| DevelopmentItemWorkflowService.detail 可能初始化流程 | 新增 detailIfInitialized，AI get/context 改用纯读 |
| 无 active 取第一节点，缺节点版本取模板版本 | 删除回退，明确 null/警告 |
| RequirementManagementService.detail 的流程摘要会解析当前默认模板 | 新增 detailForContext，只取实际实例摘要，默认模板损坏不能阻止读取未配置需求 |
| fieldValues.__components 已含全部研发节点的工作台内容 | 默认兼容模式保留；focused 去重，不携带历史正文 |
| 不可读执行目标仍保留 targetId；来源需求读取未检查模块 READ | 所有 AI 响应中的系统生成引用统一投影 |
| 评审 DTO 无 ID，不存在记录也显示 version=0 | 领域读取提供真实持久化存在性与版本 |
| 研发管控无基线时也可能已有专题/故事 | 不能只按基线是否存在判断 EMPTY |
| 旧项目 applyTopicBinding 可能取当前默认模板的挂载设置 | 纯读旧项目仅按实际历史挂载覆盖 |
| 测试 profile 指定 ContainerDatabaseDriver | 独立 MySQL 容器同时覆盖连接及 JDBC 驱动 |
| story-list 从故事表读取，requirement-execution 从执行绑定读取，并非纯 JSON | 新增研发组件读取器，分开读取 JSON、故事列表、执行绑定 |
| AiDelegationRoutePolicy 尚未允许 GET resources | 补精确四类资源 GET 的 query scope 路由测试，不放宽其他路径 |

以上是静态代码事实，不表示真实权限、数据库无写入或模型效果已经验收。

## 3. 请求与流程

保留 GET /api/integration/ai/v1/context/{resourceType}/{resourceId}。可选参数：正整数 nodeId、枚举 view=legacy|focused、字符串 componentKey。缺省 view=legacy；componentKey 仅允许 focused，不按名称模糊匹配。

~~~text
pms context project 7 --format json
pms context requirement 23 --view focused --format json
pms context project 7 --view focused --node-id 301 --component solution-design --format json
~~~

~~~mermaid
flowchart TD
    A[CLI 校验对象与读取参数] --> B[身份及 query scope 校验]
    B --> C[只读事务门面：主对象 READ 和归属]
    C --> D{资源类型}
    D --> E[项目：真实节点及选中工作台]
    D --> F[研发事项：已存在流程及组件状态]
    E --> G[摘要、字段、组件、关联引用权限投影]
    F --> G
    G --> H[legacy 或 focused 投影，声明版本及加载状态]
    H --> I[focused 响应预算检查]
    I --> J[CLI 核验响应与请求定位一致]
    J --> K[AI 解释事实或显式读取其他节点、组件]
~~~

入口不调用 preview/execute，不自动 search/get/capabilities，不初始化、恢复、激活。context 只支持 project|requirement|topic|story；其他 get/query 类型保持原范围。

- 未指定 nodeId：唯一 status=1 时 selected=current；零个时均 null；多个时均 null，状态 INCONSISTENT，警告 AMBIGUOUS_ACTIVE_NODES。
- 显式 nodeId 必须属于主对象实际流程，错误归属/不存在为 404。即使没有唯一 current，仍可选择实际历史节点。
- currentNode 永远指唯一 status=1。终态与 active 冲突保留真实 current，状态 INCONSISTENT，不能清理/伪造状态。
- componentKey 必须属于 selected 的有效组件；不存在或未附着为 404。无 selected 却指定组件为 422，message 前缀 NO_SELECTED_NODE:。
- 非法类型、非正整数、非法 view、空白 componentKey、legacy 配合 componentKey 返回既有参数错误 400。CLI 在认证/网络前验证；服务端也必须验证。

## 4. 兼容与聚焦读取

保留 AiWorkflowContextDTO 旧字段和旧 Java 构造器。新增字段 optional/nullable；旧服务端缺失时新 CLI 不补“纯读”“已验证”或空内容。

| 内容 | legacy：缺省、兼容原调用 | focused：AI 推荐显式使用 |
| --- | --- | --- |
| 实际节点概览、字段定义、组件列表 | 全部实际节点 | 全部实际节点 |
| fieldValues | 保留旧语义，包括研发 __components；项目补真实值 | 仅 selected 普通字段及授权绑定值；所有节点删除 __components，非 selected 为 {} |
| 新增 component.content | 仅 selected，可能与旧 __components 重复，属于兼容开销 | 仅 selected；指定 componentKey 时只加载该组件，唯一工作台正文来源 |
| component.contentState | NOT_REQUESTED 不描述旧 fieldValues 的内容 | NOT_REQUESTED 确实不携带该组件正文 |
| taskSummary | 仅 selected | 仅 selected |
| meta.fieldValuesScope | all-nodes | selected-node |

节点新增 fieldValuesState=LOADED|NOT_REQUESTED|UNAVAILABLE：未知旧节点配置无法解码字段时为 UNAVAILABLE、fieldValues={}、fieldValueVersions=null，并告警；不能伪装已读空值。focused 非 selected 优先 NOT_REQUESTED。config、定义和概览保留；不承诺减少全部 DB 查询，现有领域 DTO 仍可能读取全部节点。resourceDetails 不复制组件正文。

focused data 对象以应用 HTTP Jackson 配置序列化为 UTF-8 JSON，最大 **524288 字节（512 KiB）**，不含 envelope。超限整体拒绝：HTTP/业务 code **422**，message 前缀 CONTEXT_TOO_LARGE:，提示指定节点/组件。不截断、不返回伪完整结果；legacy 不加此限制以免破坏旧调用。此限制不是模型 token 预算。单组件或主对象正文仍超限时明确失败，本阶段不提供分页。

CLI 核验：所有模式先核对 resourceType/resourceId 与请求完全一致；显式 nodeId 要求 meta.selectedNodeId 精确匹配；focused 要求 meta.view=focused、formatVersion=2；componentKey 要求 meta.selectedComponentKey 精确匹配。缺少证明或不匹配时为 client validation 错误，不重试、不降级、不输出成功。旧服务端可能忽略新参数；无新参数的旧调用继续兼容，不新增 capabilities 请求。新定位参数必须为 JS 安全正整数，超出 Number.MAX_SAFE_INTEGER 在认证/网络前拒绝。

兼容指结构可解析，不保留错误事实：项目节点 ID 由 null 改为实际 ID；研发无 current/version 不再回退。引用过滤属于响应收紧，必须写兼容说明。

两个 boolean feature：readonlyWorkflowContext、focusedWorkflowContext，最终验收后同时启用；保留原 catalog version/feature。中间提交不发布，不以 prototype 代替完整能力。

## 5. 响应合同

### 5.1 顶层及元信息

顶层新增 entityVersion、resourceDetails、relations、meta。version：项目版本；研发唯一 current 的节点版本，无 current/版本则 null。历史查询不改变顶层 version，模板版本不作为写入版本。

| 字段 | 规则 |
| --- | --- |
| entityVersion | 项目/需求真实版本；专题/故事无版本，null |
| resourceDetails | 主对象白名单：标题、正文、状态、优先级、负责人、日期、组织/系统、进度；故事 blocker/build/test 等真实值；关联引用经投影 |
| relations | 已授权且仍存在的直接引用；type/id/label/readable，readable 恒 true |
| meta.formatVersion / pureRead | 2 / true；声明纯读合同，发布必须有隔离验收证据；认证刷新可能写会话 |
| meta.loadedAt / businessTimezone | UTC Instant，门面开始装配时刻 / Asia/Shanghai；不是提交时间或全局快照 ID |
| meta.view / fieldValuesScope | legacy 或 focused / all-nodes 或 selected-node |
| meta.componentContentScope | selected-node 或 selected-component |
| meta.selectedNodeId / selectedComponentKey | 实际选择值或 null |
| meta.workflowConfigured | 项目由实际节点存在性、研发由实际实例存在性判断，默认模板不算 |
| meta.workflowStatus / terminalStatus | 依下表 / 实际终态或 null |
| meta.workflowInstanceVersion | 研发实例 version；项目无独立实例版本，null |
| meta.permissionsScope | 项目 business-user；研发 undeclared；不等于 token 可执行权限 |
| meta.referencesScope | 固定 readable-only；省略引用不表示业务上没有引用，不能据空列表编造“未关联” |
| meta.warnings | code+message；NO_ACTIVE_NODE、AMBIGUOUS_ACTIVE_NODES、UNKNOWN_VERSION、UNKNOWN_COMPONENT、UNRESOLVED_NODE_CONFIG、INCONSISTENT_WORKFLOW_STATE |

状态按以下优先级判定，不直接复制领域 DTO 的回退状态：

resolver 的 entityStatus 输入约定：项目按 ProjectStatus.normalize 后映射 1→ACTIVE、2→COMPLETED、3→TERMINATED；已删除在主对象读取时404，其他未知值→UNKNOWN。需求使用真实 ACTIVE/REJECTED；专题/故事使用真实 developmentStatus，不把 DONE/NOT_STARTED 直接当流程终态。仅项目生命周期和需求拒绝状态参与流程终态判定。

| 情况 | workflowStatus |
| --- | --- |
| 项目无实际节点/研发无实例 | NOT_CONFIGURED；workflow/current=null；显式 nodeId 为 404 |
| 研发有实例无节点；多个 active；非法/未知节点、项目生命周期或终态状态；终态仍有 active；项目 COMPLETED 但节点未全完成 | INCONSISTENT，保留事实，不修复 |
| 实际 terminalStatus=REJECTED 或需求已拒绝，无冲突 | REJECTED |
| 项目 status=TERMINATED，无 active 冲突 | TERMINATED |
| 非空且所有实际节点 status=2 | COMPLETED |
| 唯一 status=1 | IN_PROGRESS |
| 有 status=3 无对应终态；部分完成却无 active | INCONSISTENT |
| 全部 status=0 | NOT_STARTED |

未知非空 terminalStatus 为 INCONSISTENT。正常 completed/terminated/rejected 不产生 NO_ACTIVE_NODE；其他无 active 情况才告警。项目已完成/终止但无节点，保留 entity 终态，workflow=NOT_CONFIGURED，并加 INCONSISTENT_WORKFLOW_STATE。

### 5.2 节点

保留 id/key/label/position/status/components/fields/fieldValues，新增 version、description/deliverable、ownerId/ownerName、startDate/endDate、fieldValueVersions、fieldValuesState、permissions、taskSummary。按实际 sort（null 排后）、id 排序，position 从 0 起；不按模板重排。

项目独立字段版本真实读取；研发 fieldValueVersions=null，以 node.version 表达整体字段版本。所有未知版本保持 null。项目 permissions 复用 NodePermissionsDTO，只表达业务用户权限，不含委托 scope 或全部完成前置条件；研发为 null。UI 按钮布尔值不能充当 AI 写权限。

taskSummary={total,done,incomplete}：selected 的每条未删除父子任务计一次，DONE=2，incomplete=total-done；零任务 0/0/0，未加载 null。研发统计按平面任务行，不只数 roots，也不漏掉父记录已删除的存活子任务。任务明细和依赖暂留后续。

### 5.3 组件和持久化事实

保留 key/label/fields/actions；新增 config/content/contentState/versions/saveContract。fields 仅表示模板动态字段，不是专用工作台全部可写字段。saveContract 首版 null；actions/allowedActions 仅为 registry/scope 声明。

- config 来自 pinned 版本及合法运行时覆盖，不从最新默认模板补齐。
- contentState=LOADED|EMPTY|NOT_REQUESTED|UNSUPPORTED。EMPTY 是没有组件自身持久化内容；NOT_REQUESTED 是本次未加载。
- 8 个项目专用领域服务新增 getReadSnapshot，返回 display DTO、hasPersistedContent、parts 存在性/版本。原 get 的展示默认值和写入校验语义不变。
- 不按 DTO 非空、DRAFT/PENDING、version=0、reviewerId 判断存在性。已存在且 version=0 合法；已存在但 version=null 则 UNKNOWN_VERSION。
- 未保存方案包/决策在 AI content 中为 null；reviews 只保留实际记录；缺失评审对应 versions null。EMPTY 时 content=null，不把 UI 草稿默认值当成果。
- 单基线版本键 content；方案为 package/decision/review:BUSINESS_PRODUCT/review:TECHNICAL/review:TEST_RELEASE。不存在记录的版本 null。上游版本只作来源事实，不当作本组件版本。
- hasPersistedContent 按根记录或组件所属有效子记录判断；研发管控有专题/故事即 LOADED，即便基线不存在，versions.content=null。上游摘要和可选迭代列表不算已保存工作台内容。
- 研发 JSON 型组件：key 存在且值非 null 为 LOADED，包括已保存 {}；缺失/null 为 EMPTY。versions.node 取真实节点版本。未知组件为 UNSUPPORTED、content=null，保留可读 config 并告警。
- project-basic-info 是真实项目白名单，LOADED，versions.content=entityVersion。
- 正文完整保留文字、换行、Unicode 和空值；未知版本 map 禁用拒绝 null 的 Map.of/Map.copyOf。

研发组件必须按真实来源分流，不能用所有组件都读取 __components 的简化实现：

| 组件 | 内容来源与版本 |
| --- | --- |
| requirement-receiving-analysis、requirement-node-workbench、topic-research、topic-design-review、story-node-workbench、story-testing | 实际绑定的 __components[key]，versions.node；配置中的 variant 不变成新的资源类型/随意猜 key |
| requirement-execution | 纯读事项详情的当前 executionTarget，经过引用投影；版本来自需求 entityVersion，键 entity。没有绑定为 EMPTY；已有绑定但不可读，LOADED/content={}，不能伪称“未绑定”，也不输出目标 ID/类型/名称。referencesScope 明确这是可见引用投影。首版不展开执行历史或候选目标 |
| story-list、运行时 story-split | DevelopmentStoryManagementService.listByTopic(topicId)，按 topicWorkflowNodeId=selectedNodeId 过滤实际行；story-list 的测试成果仍来自自身 JSON。content={stories:[已授权行],testingResults:已保存JSON或null}；故事行或已保存测试状态任一存在即 LOADED，没有则 EMPTY。versions.node 只覆盖 JSON，versions.stories=null：故事实体无版本，不能把父节点版本当列表版本；有故事时明确 UNKNOWN_VERSION |

领域 listByTopic 必须核对直接/递归依赖纯读；不调用故事详情去补建 workflow。LOADED 只表示主对象组件有持久化事实，不保证全部关联内容可见；过滤后不能返回过滤前数量和聚合。JSON 中非 schema 声明的键不解释为系统生成引用；用户原文保持，未知整个组件不输出原始内容。

## 6. 权限、模板与一致性

### 6.1 全响应引用权限

既有身份与 pms:query:read scope 检查后，requirement 强制 REQUIREMENT_READ，其他三类 PROJECT_READ。write 不继承 read。项目范围和归属沿用现有读取策略，不宣称新增数据范围规则。内部 seed 的免身份逻辑不能成为外部免校验通路。

AiContextReadPolicy 和 AiContextReferenceProjector 供四类 AI get/context 共用，过滤所有**系统生成或绑定解析的结构化业务引用**：relations、get.details.executionTarget/sourceRequirements、resourceDetails 父/迭代引用、bound fieldValues、组件 DTO/已知组件状态引用。不能只过滤 relations。项目 NodeRequirementDTO 是项目范围明细，不等同独立 RequirementDO，不误套需求模块权限。

不可读/已删除引用整项省略；不能保留 targetId/navigationId/名称或过滤前数量、聚合。对应字段缺省/null，明确兼容收紧。主对象权限范围内已经保存的普通正文、备注不做字符串删改；保证不涵盖正文里人工写下的名字/ID，不猜任意数字的资源类型。未知组件不输出原始 content；legacy 的 __components 也需省略未知组件原始状态，不能保留该旁路。

直接引用：专题项目、故事专题/项目/迭代、需求执行目标、项目/专题/故事直接来源需求。父挂载节点只有父可读才输出，不伪装 context 类型。iteration_plan 仅沿用既有纯读引用策略。工作台已有专题/故事可保留授权记录，不递归加载其 workflow。

缓存只在单次请求局部，不以单例字段按 resourceId 缓存跨用户授权。只捕获相关对象明确 403/404；DB/解析/未知异常传播；主对象 403/404 拒绝。警告/日志/验收记录不额外暴露被过滤引用。

### 6.2 事务与模板

AiWorkflowContextReadService.context 经 Spring 代理开启 readOnly=true、isolation=REPEATABLE_READ。权限、归属、读取、投影、metadata 在同一调用。QueryService.resource 同样经代理只读 RR；四类 get 强制 READ/引用投影。需求 get 的 workflowConfigured/status 按实际纯读实例纠正，不能保留“默认模板=实例”的摘要。其他 query/get 不属于本次新增纯读保证。

需求主对象摘要使用新增 RequirementManagementService.detailForContext，跳过原 workflowSummary 对默认模板的解析，仅根据实际实例取摘要。默认不存在或损坏但需求无绑定实例时仍可读取自身事实；已有实例的 pinned 版本损坏才按绑定错误409拒绝。不能先调用原 detail 再覆盖摘要，否则仍依赖不相关的当前默认配置。

下游普通一致性 SELECT 参与同一事务。禁止 FOR UPDATE/共享锁、REQUIRES_NEW、异步或外部可变缓存拼业务快照。get/context 是独立请求；loadedAt 不代表共享快照；后续写入继续校验既有真实版本。

实际绑定版本为空、不存在、损坏或实际节点 key 在 pinned 定义中缺失为 409。getDefinitionForContext 限定模板解析边界，确认绑定错误才转 409，不能吞 DB 异常或任意 422。无绑定旧项目单独使用 BuiltIn compatibility，templateSource=legacy-compatibility，模板版本 null；未知旧节点保留事实，配置未声明，警告 UNRESOLVED_NODE_CONFIG。

旧项目新增 applyLegacyTopicBinding，仅按实际专题挂载覆盖，不能复用会读取最新默认挂载的分支；已绑定项目遵循 pinned，专题故事挂载使用实际实例 storyMountNodeKey。保留原页面/写服务语义。

该约束必须覆盖工作台递归依赖：现有 requireNodeComponent/findNodeWithComponent 也会执行旧 applyTopicBinding。新增上下文专用校验/查找方法，getReadSnapshot 及上游快照只能使用这些纯读 pinned/legacy 规则。快照展示 DTO 可以省略 UI 按钮和候选列表，不能为了计算 topicCreationAllowed 等界面标志去读取最新默认配置。方案/计划/验收的上游存在性和版本从对应 getReadSnapshot.parts 取得，不从默认展示 DTO 推断；原 get/写入路径仍用原规则。

## 7. 接口和代码边界

新增 AI 类位于 ai/connector/context；不改旧 PageContextAssembler。

| 类 | 接口 |
| --- | --- |
| NodeService | List<ProjectNodeDTO> listReadOnly(Long projectId)；DTO 追加 componentConfigs |
| WorkflowTemplateService | WorkflowTemplateDefinition getDefinitionForContext(Long versionId)，严格 pinned |
| WorkflowComponentBindingService | WorkflowTemplateDefinition applyLegacyTopicBinding(Long projectId,WorkflowTemplateDefinition definition,List<ProjectNodeDO> nodes) |
| ProjectPermissionService | void requireNodeComponentForContext(ProjectNodeDO node,String key,String message)；ProjectNodeDO findNodeWithComponentForContext(Long projectId,String key)，同一严格绑定/实际挂载规则 |
| TaskService | NodeTaskSummaryDTO summarizeByNode(Long projectId,Long nodeId)，校验归属 |
| DevelopmentItemWorkflowService | DevelopmentItemWorkflowDetailDTO detailIfInitialized(DevelopmentItemType,Long)；追加 workflowVersion/nodeTaskSummaries |
| RequirementManagementService | RequirementListDTO detailForContext(Long id)，读取真实对象与实际实例摘要，不解析默认模板 |
| 8 个专用工作台服务 | WorkbenchReadSnapshot<既有DTO> getReadSnapshot(Long projectId,Long nodeId) |
| AiWorkflowContextReadService | AiWorkflowContextDTO context(String type,Long id,ContextReadOptions options) |
| AiProjectContextReader | AiWorkflowContextDTO read(Long id,ContextReadOptions options) |
| AiDevelopmentContextReader | AiWorkflowContextDTO read(DevelopmentItemType type,Long id,ContextReadOptions options) |
| AiDevelopmentComponentReader | List<AiWorkflowContextDTO.Component> read(DevelopmentItemType type,DevelopmentItemWorkflowDetailDTO item,DevelopmentItemWorkflowNodeDTO node,Integer entityVersion,String componentKey)，JSON/执行绑定/故事列表分流 |
| AiProjectComponentReader | List<AiWorkflowContextDTO.Component> read(ProjectDTO project,ProjectNodeDTO node,String componentKey)，null 表示全部有效组件 |
| AiContextNodeSelector | Selection select(List<NodeRef>,Long requestedId) |
| AiContextWorkflowStateResolver | State resolve(String type,String entityStatus,String terminalStatus,boolean configured,List<NodeRef>) |
| AiContextReadPolicy | void requireRead(String type)；boolean canReadReference(String type,Long id)，主类型及 iteration_plan |
| AiContextReferenceProjector | projectResourceDetails(String type,Long id,Map<String,Object>)、projectFieldValues(String type,Long id,Map<String,Object>) 返回 Map；projectComponentContent(String type,Long id,String key,JsonNode) 返回 JsonNode；不修改输入 |
| AiContextResponseBudget | void requireWithinFocusedLimit(AiWorkflowContextDTO context)，实际 Jackson UTF-8 字节，仅 focused |
| AiConnectorQueryService | 保留既有两参 context；追加 context(String,Long,ContextReadOptions)，旧调用默认 legacy。不新增三参 Long 重载，避免传 null 时 Java 重载歧义 |

精确类型：

- ContextReadOptions(String view,Long nodeId,String componentKey)，缺省归一化 legacy。
- PersistedContentVersion(boolean persisted,Integer version)，false 必须 null，true 可为 0/null。
- WorkbenchReadSnapshot<T>(T display,boolean hasPersistedContent,Map<String,PersistedContentVersion> parts)，不提供确保存在/保存行为。
- NodeTaskSummaryDTO(long total,long done,long incomplete)；DevelopmentItemWorkflowDetailDTO 追加 Integer workflowVersion、Map<Long,NodeTaskSummaryDTO> nodeTaskSummaries，来自平面行。
- AiWorkflowContextDTO 追加 Integer entityVersion、Map<String,Object> resourceDetails、List<Relation> relations、ContextMeta meta。
- Relation(String resourceType,Long resourceId,String label,boolean readable)；Warning(String code,String message)。
- ContextMeta(int formatVersion,boolean pureRead,Instant loadedAt,String businessTimezone,String view,String fieldValuesScope,String componentContentScope,Long selectedNodeId,String selectedComponentKey,boolean workflowConfigured,String workflowStatus,String terminalStatus,Integer workflowInstanceVersion,String permissionsScope,String referencesScope,List<Warning> warnings)。
- Node 追加 Integer version、String description/deliverable、Long ownerId、String ownerName、LocalDate startDate/endDate、Map<String,Integer> fieldValueVersions、String fieldValuesState、NodePermissionsDTO permissions、TaskSummary taskSummary；TaskSummary(long total,long done,long incomplete)。
- Component 追加 JsonNode config/content、String contentState、Map<String,Integer> versions、Map<String,Object> saveContract。
- NodeRef(Long id,Integer status,int position)；Selection(Long currentNodeId,Long selectedNodeId,List<Warning> warnings)；State(String status,String terminalStatus,List<Warning> warnings)，分别为 selector/resolver 嵌套 record。

project-basic-info 从已授权 ProjectDTO 白名单读取；其余 8 项来自 NodeRequirementScope/NodeSolutionDesign/NodePlanResourceRisk/NodeDevelopmentControl/NodeAcceptance/NodeRelease/NodeValueReview/NodeKnowledgeStandardService.getReadSnapshot。快照复用原查询/映射；核对直接和递归依赖无写入后才纳入。

CLI context(resource,options?) 和 getContext 同步扩展，ContextOptions={nodeId?:number,view?:'legacy'|'focused',component?:string}，HTTP 映射 componentKey。显式 Zod schema 同时更新运行时和导出 TypeScript 类型。无 meta/null meta 走旧 schema，保留原 unknown[] 接受范围、旧 id=null；formatVersion=2 走严格节点/组件/schema。未知新格式明确不支持。不得靠修改旧 fixture 掩盖兼容收紧，不编造缺失值。

## 8. 验收与发布

1. 先完成纯读、权限和项目基础信息/需求范围、需求 JSON/执行绑定纵向读取。在自己的 MySQL 8.4 容器做早期证明，接入 CLI 并先试读一个项目/需求，再扩展其余 7 个项目工作台和专题/故事。早期演示明确其他组件仍 UNSUPPORTED，不冒充完整交付。
2. 正常 fixture 优先业务服务；异常旧数据通过**仅测试容器的 fixture helper**写入，仅在跟踪前。不能要求正常业务服务制造非法状态，也不能修改用户库。
3. 类级 PMS_CONTEXT_READ_ISOLATED=true 开关；关闭时不启动 Spring/容器。覆盖 URL/user/password/**com.mysql.cj.jdbc.Driver** 及 Flyway/SQL init 的有效连接；禁用通知发送和定时任务；迁移和 fixture 完成后开始跟踪。skip 不计通过。
4. 对经代理真实 bean 的四类 get/context：MyBatis 跟踪写/锁查询，测试 DataSource JDBC 跟踪直接 JDBC、batch、prepare/execute 写尝试；前后比较相关业务表数量、内容和版本；验证未通知、补成员、调用 preview/execute 或业务审计写。认证/请求日志明确排除。
5. RR 并发测试：barrier 前用实际 SELECT 建立快照，另一事务提交后，当前事务两相关值均旧、新请求均新。目标调用写入跟踪与其他线程修改分开；该测试不混入无写入前后快照断言。
6. 外部只读 E2E 必须显式四类 ID；缺失记录未运行，不回退列表第一条。不只比 resourceId，还比已知节点、版本、正文和选择参数。页面核验只用正常已初始化 fixture；异常以 DB 为准，防止页面 list 自动补齐污染证据。
7. 自然语言验收预置各类的当前/历史差异、负责人/日期、已有/缺失成果。逐项比对，不能把 NOT_REQUESTED 说成 EMPTY、null 说成 0，也不能凭任务计数编造阻塞原因。API E2E 不替代模型验收。
8. 四类、全部 9 个项目组件及研发 JSON/执行绑定/故事列表来源、兼容/聚焦、权限、纯读/RR、CLI 和真实只读/模型验收完成后才启用两个 feature。缺环境/模型明确未运行；早期 DB 通过不启用 capability、不宣称产品场景通过。

## 9. 后续边界

后续再做任务明细/阻塞依据、保存合同、动作前置条件、跨请求版本及大内容分页。正文中人工写下的指令属于业务数据，仓库 Skill 应说明不能覆盖工具授权、自动触发写入。
