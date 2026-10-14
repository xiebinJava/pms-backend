# AI 只读流程上下文实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 当前默认主代理顺序实施；没有用户明确选择时不启动子代理。

**Goal:** 让 AI 通过现有 PMS CLI 读取四类对象的真实流程与已有成果，且授权、存在性、版本、加载状态可验证。

**Architecture:** 两套领域实现增加纯读入口，统一事务门面及引用投影；缺省 legacy 保持调用兼容，显式 focused 去重、按节点/组件取内容。先证明项目/需求纵向切片，再扩组件和完整验收。

**Tech Stack:** Java 17、当前 Spring Boot/MyBatis-Plus/Jackson、MySQL 8、JUnit/Mockito/Testcontainers；TypeScript、Node.js 22+、Zod 4、pnpm 10.18.2。

**Spec:** /Users/fs/Desktop/Project/pms-backend/docs/superpowers/specs/2026-10-14-ai-readonly-workflow-context-design.md

状态：Task 1 已实施并提交（commit 2692fc6，38 项新增/回归测试全绿；全量套件中 67 个 Docker 集成用例因 testcontainers 无法连接 Docker 环境失败，与本次改动无关，基线提交同样失败）。Task 2-9 未执行。配套评审记录：docs/superpowers/reviews/2026-10-14-ai-readonly-workflow-context-review.md。

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

B=/Users/fs/Desktop/Project/pms-backend；C=/Users/fs/Desktop/Project/pms-ai-connector；F=/Users/fs/Desktop/Project/pms-front（仅页面核验）。

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

