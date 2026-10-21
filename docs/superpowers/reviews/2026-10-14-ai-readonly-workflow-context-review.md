# AI 只读上下文方案：深度评审记录

日期：2026-10-14。评审对象为第二版设计及实施计划；评审方式为逐项对照源码、追踪读取依赖、模拟协议组合与验收失败场景，并检查修订后任务依赖。没有独立子代理评审，也没有运行未来实施测试。

- [修订方案](pms-backend/docs/superpowers/specs/2026-10-14-ai-readonly-workflow-context-design.md)
- [修订计划](pms-backend/docs/superpowers/plans/2026-10-14-ai-readonly-workflow-context.md)

## 评审结论

保留两套领域实现、统一 AI 读取协议的方向成立。本轮确认的问题已落实到方案、接口和对应测试要求；可以按纵向切片开始实施。本次修复的是设计文档，不表示现有业务代码的问题已经修复，或数据库/权限/模型验收已经通过。

先完成项目/需求纯读切片，提前做隔离数据库证明，接 CLI 验证首次试读，再扩展所有组件。避免先铺满接口，最后才验证读取无副作用或 AI 是否读懂。

## 已修正的问题及源码依据

| 编号/优先级 | 原问题与实际影响 | 修订及验证任务 |
| --- | --- | --- |
| R01 / P1 | 只过滤 relations，旧 get.executionTarget 仍含不可读对象 targetId；名称清空不能满足“不暴露引用 ID” | 四类 get/context 共用全响应引用投影，覆盖旧别名、绑定字段、组件和 legacy JSON；未知组件不能保留 raw content 旁路。Tasks 4/5/6/8/9 |
| R02 / P1 | 专题故事列表和需求执行目标不全在 __components；统一按 JSON 缺失判断会把页面已有内容报告为 EMPTY | 新增研发组件读取器，分 JSON/执行绑定/实际故事行；按真实挂载节点筛选，绑定版本取需求实体，故事列表版本不冒充父节点版本。Tasks 5/8/9 |
| R03 / P2 | 展示 DTO 的默认 version=0 无法区分“未保存”和“真实版本0”；评审 DTO 没有 ID | 领域快照携带 persisted/parts/hasPersistedContent；包、决策、三个评审独立存在性；空草稿不当成果。Tasks 3/8 |
| R04 / P2 | 无基线不等于无子内容，研发管控可能已有专题/故事 | 聚合存在性包含所属有效子记录；只有候选列表/上游摘要不算保存。无基线有故事为 LOADED、根版本 null。Tasks 3/8 |
| R05 / P2 | 原“选中节点”输出仍包含所有研发历史 JSON，并复制 selected 正文，AI 输入没有聚焦 | legacy 兼容，focused 去除所有 __components、只保留 selected 普通字段及指定组件；声明 NOT_REQUESTED/UNAVAILABLE，512KiB 字节上限整体拒绝，禁止截断。Tasks 2/5/7/9 |
| R06 / P1 | 旧项目挂载 overlay 会解析最新默认，工作台 requireNodeComponent/findNodeWithComponent 的递归校验也可能再次漂移 | 新增 legacy 实际挂载及上下文专用组件校验/查找；快照上游同样纯读，不为 UI 候选项/按钮读取默认配置。Tasks 1/3/6/8 |
| R07 / P2 | RequirementManagementService.detail 会先读默认模板摘要，即使后续覆盖也已依赖无关配置；无实例需求可能被损坏默认配置阻断 | detailForContext 只读实际实例摘要；无实例时默认不存在/存在/损坏均不创建或阻断读取；实际 pinned 损坏才409。Tasks 1/5/6 |
| R08 / P2 | 只新增请求参数不能保证旧服务端执行；旧服务端会忽略参数，客户端仍可能当成功。新的严格节点 schema 还可能拒绝原 unknown[] | 无 meta 保留旧 schema，format2 严格分支；请求 type/id、nodeId、view、component 的响应证明必须匹配，失败不自动降级。JS 定位整数超安全范围先拒绝。Task 7 |
| R09 / P2 | 流程状态及版本来源含糊；正常完成被报无 active 警告；终态与 active 冲突可能掩盖异常；只数 roots 会漏子任务 | 明确状态优先级和项目整数状态映射，保留 current 事实；统计平面未删除行，字段/节点/实例/实体/工作台版本分离。Tasks 1/2/5/8 |
| R10 / P2 | 测试只覆盖 URL/user/password，原 profile JDBC 驱动仍为 ContainerDatabaseDriver；MyBatis update tracker 漏直接 JDBC/batch/锁查询；正常服务难以制造异常 fixture | 覆盖实际 MySQL 驱动与全部连接；容器内 fixture helper；MyBatis+JDBC 写尝试/锁跟踪、业务表快照及外部副作用验证；并发事务与无写入用例分开。Tasks 6/8 |
| R11 / P2 | 委托路由已有 context，但缺 GET resources，前置 get 与只读 token 闭环无法按计划验收 | 精确增加四类 GET resources 的 query scope 路由，保持其他方法/路径拒绝；普通 CLI session 不误称天然只读。Tasks 5/9 |
| R12 / P2 | 早期开 feature、skipped/E2E 只比 ID、页面自身会补数据，容易得到假交付；CLI/模型验证太晚 | DB 证明提前、CLI 试读提前；显式四类 fixture、已知事实核验；异常用 DB，正常用页面；全部必须验收后最后开两个 feature。Tasks 6/7/8/9 |
| R13 / P2 | 新增 Long 与 ContextReadOptions 同位置的三参重载，传 null 时会产生 Java 重载歧义 | 只保留真实存在的两参接口，新增 options 三参，不新增 Long 三参。Task 5 |

关键证据位置：

- R01：[不可读执行目标仍保留 targetId](pms-backend/src/main/java/com/brad/pms/service/RequirementExecutionTargetReadService.java:172)、[旧 AI get 直接复制 executionTarget](pms-backend/src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java:283)。
- R02：[领域故事列表从故事表读取](pms-backend/src/main/java/com/brad/pms/service/DevelopmentStoryManagementService.java:133)、[页面按实际挂载节点筛选](pms-front/src/views/development/detail/DevelopmentStoryListComponent.vue:36)、[执行组件使用独立 target](pms-front/src/views/development/detail/RequirementExecutionComponent.vue:24)。
- R03：[评审默认版本0](pms-backend/src/main/java/com/brad/pms/service/NodeSolutionDesignService.java:465)、[评审 DTO 缺少记录 ID](pms-backend/src/main/java/com/brad/pms/dto/response/NodeSolutionReviewDTO.java:8)。
- R04：[管控同时查询专题/故事与可空基线](pms-backend/src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java:407)。
- R05：[旧节点字段映射保留原 JSON](pms-backend/src/main/java/com/brad/pms/ai/connector/AiConnectorQueryService.java:349)。
- R06：[无绑定时解析默认挂载](pms-backend/src/main/java/com/brad/pms/service/WorkflowComponentBindingService.java:90)、[组件校验再次执行 overlay](pms-backend/src/main/java/com/brad/pms/service/ProjectPermissionService.java:150)。
- R07：[需求摘要先解析默认模板](pms-backend/src/main/java/com/brad/pms/service/RequirementManagementService.java:206)。
- R08：[旧 CLI 手工映射响应字段](pms-cli/packages/pms-client/src/PmsClient.ts:124)、[旧节点 schema 接受 unknown 数组](pms-cli/packages/pms-contracts/src/query.ts:105)。
- R09：[实际节点状态枚举](pms-backend/src/main/java/com/brad/pms/common/enums/NodeStatus.java:6)、[任务列表仅顶层](pms-backend/src/main/java/com/brad/pms/service/TaskService.java:79)。
- R10：[原测试驱动](pms-backend/src/test/resources/application-test.yml:6)。
- R11：[当前委托路由](pms-backend/src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java:70)。
- R12：[原 E2E 选择列表首条兜底](pms-cli/tests/e2e/pms-cli.e2e.test.ts:33)、[页面节点读取有写入](pms-backend/src/main/java/com/brad/pms/service/NodeService.java:193)。
- R13 是本轮接口设计检查发现的潜在编译问题，现有源码仅有两参 context，不属于已经发生的运行故障。

## 保留的取舍与实施前提

1. focused 控制 AI 输出规模，暂不保证减少全部数据库读取；512KiB 也不等于模型 token 预算。一个组件或主对象正文超限时明确失败，分页是后续独立工作。
2. legacy 为兼容仍可能重复内容；AI 仓库 Skill 推荐显式 focused。权限收紧、纠正回退事实明确记录在兼容文档，不能宣称全部旧行为不变。
3. 返回的是主对象授权事实及可读引用投影；省略引用不代表业务未关联。用户保存的普通正文原样保留，不扫描字符串中的人名/ID。
4. 项目权限布尔值仅是业务用户权限，研发资源级写权限及 saveContract 未声明；本阶段不适合自动推进。
5. 任务仅统计、故事实体无版本，不能据此回答具体阻塞任务或承诺列表版本可用于写入。
6. C 仓库尚不在当前显式可写根中；执行 CLI 任务时需宿主授权。该前提不阻碍本次文档修订。
7. 当前业务代码尚未按方案实施。新建方法是否编译、事务代理是否生效、递归读取是否真无写入、真实权限是否正确、模型是否准确，必须由计划中的实际验收证明。

## 本次已执行的文档核验

- 对照现有源文件核对关键读取行为、模板/权限递归调用、业务 DTO 与 CLI schema。
- 核对 9 个任务按依赖排序；新增方法在产生/消费任务中同名，新增测试先 Create 后 Modify。
- 核对计划中现有源文件、现有测试及 Maven 测试名；区分当前存在文件和未来任务产物。
- 检查 Markdown 表格列数、代码围栏、任务编号、未执行勾选状态、预算/错误码/feature 时机及源文件链接。
- 用上一版文档的临时备份检查差异；单独核验 untracked 文档空白，git diff --check 不作为它们的唯一检查。

实际检查结果：文档检查脚本退出 0，9 个任务依次编号、38 个未来文件声明、46 个步骤均未勾选；文件存在性/先创建再修改、Maven 测试名、关键字段、Markdown/源链接检查 issues=[]。git diff --check 退出 0，backend tracked diff 为空。临时检查脚本位于 /tmp/pms-context-review-20261014/check_docs.py，仅核验文档，不执行计划测试。

未运行 Maven/Vitest、数据库、外部 E2E 或模型验收；本次没有业务代码变更、提交或发布。上述检查只证明文档与静态代码证据的一致性，不代替实施后的运行验证。
