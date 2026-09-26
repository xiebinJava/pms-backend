# 专题与故事可选关联实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: After the user approves the plan, use either `superpowers:subagent-driven-development` or `superpowers:executing-plans` task-by-task, according to the user's chosen execution method. Keep the existing working tree and branch; preserve unrelated changes. Each phase ends with a self-review and verification before proceeding.

**Goal:** 专题可选择项目或独立存在，故事可选择专题或独立存在；项目下专题/故事的负责人、流程节点负责人、任务及子任务执行人自动确保为普通项目成员，并在解除或变更指派后按来源引用安全回收系统自动加入的成员。

**Architecture:** 在现有专题、故事和事项流程表上将项目上下文改为可空，并将故事的 `topic_id` 改为可空。故事有关联专题时继承专题的项目/节点上下文，无专题时上下文为空。新增项目成员自动管理标记及人员分配引用表，引用以专题/故事为上下文、以负责人/流程节点负责人/任务执行记录为来源；统一的 `ProjectMemberAssignmentService` 负责确保成员、变更/释放引用和安全回收。后端按是否有关联项目分开施加项目 ACL 或模块级权限；前端让两种关联均可选、可编辑、可清空，所有人员仍从公司全员中选择。

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Flyway/MySQL, Vue 3, TypeScript, Ant Design Vue, Node.js built-in test runner, Maven.

**Spec:** `docs/superpowers/specs/2026-09-24-independent-development-topics-design.md`

## Global Constraints

- 保持 `item_type + item_id` 为事项流程唯一身份，不创建隐藏项目或虚构项目节点。
- 项目及来源项目节点要么同时存在且相互匹配，要么同时为空。
- 故事专题关联可空；有关联专题时故事的项目/节点必须与专题一致，无专题时项目/节点必须为空；不得建立独立 story-project 关联。
- 新分配对象仅允许状态为 `ACTIVE` 且未删除的用户；历史指派只读展示，不因停用而清除。
- 绑定项目的专题/故事负责人、事项流程节点负责人、任务及子任务执行人保存时自动确保以普通成员身份加入项目；保留已有成员角色。赋值变更、任务软删除、解绑和改绑均须同步其项目成员引用。
- 自动成员回收只适用于有本功能自动加入标记、没有其他有效事项分配引用且当前仍为普通成员角色的用户；预先存在或人工维护的成员、项目负责人/管理员及仍被引用者均保留。
- 研发模块的 `PROJECT_READ` / `PROJECT_WRITE` 权限仍生效；绑定项目的事项继续遵守项目级读取和写入范围。
- 对项目/节点/流程关系或人员分配的改变必须事务性更新专题、故事、流程实例、成员、自动管理标记及分配引用；任何失败完整回滚。
- 不回改历史 Flyway migration；新增迁移保留所有已有非空关联、约束与索引。
- 每阶段完成后先运行定向测试、检查差异和自审，再进入下一阶段；不得混入工作区里既有的无关改动。

## Review Focus

1. 项目 ID 与节点 ID 不一致或只有一个为空，或故事与专题上下文不一致：工作流和故事详情必须拒绝；Task 2/3/5 用服务测试覆盖。
2. 无项目事项不得借由全局可见性读取其他绑定项目的事项：列表和详情按模块权限与项目可读范围隔离；Task 4 用跨范围测试覆盖。
3. 用户被停用或删除后不能成为新指派对象，但既有历史指派仍可查看；Task 2/3 用后端校验与展示测试覆盖。
4. 解绑/改绑、人员改派/移除、任务软删除或自动加/回收成员中途写入失败不能留下专题、故事、流程、成员、标记及引用不一致；Task 1/2/3/5 用迁移与事务失败路径测试覆盖。
5. `projectId` 为空时前端不能请求成员列表或跳转到 `/projects/undefined`，`topicId` 为空时不能生成无效专题入口；Task 6 用列表与详情交互测试覆盖。
6. 专题/故事 owner、每个流程节点 owner、父任务及子任务 assignee 都必须以稳定 assignment key 建立独立引用，并覆盖事项详情和项目节点开发控制两个入口；改派/清空/软删除只释放对应引用，解绑/改绑必须重建包括首次绑定在内的完整引用集；自定义 PERSON 字段不应误当项目成员 assignment。Task 2/3/5 的单元与事务集成测试覆盖。

---

### Task 1: 数据库与 API 类型允许空项目上下文

**Files:**
- Create: `src/main/resources/db/migration/V54__optional_development_item_project_context.sql`
- Modify: `src/main/java/com/brad/pms/controller/HealthController.java`
- Modify: `src/main/java/com/brad/pms/dto/request/DevelopmentTopicUpdateCmd.java`
- Modify: `src/test/java/com/brad/pms/config/FlywayMigrationVersionTest.java`
- Create: `src/test/java/com/brad/pms/config/OptionalDevelopmentContextMigrationTest.java`
- Create: `src/test/java/com/brad/pms/config/ProjectMemberAssignmentMigrationTest.java`
- Modify: `../pms-front/src/api/development-item.ts`
- Modify: `../pms-front/src/types/domain.ts`

**Interfaces:**
- `DevelopmentTopicUpdateCmd.projectId` 可空；缺省与显式 `null` 都表示不关联项目。
- 故事持久化 `topic_id` 可空；故事保存命令 `topicId` 可空，缺省与显式 `null` 都表示不关联专题。
- 专题、故事和工作流详情的 `projectId`、`nodeId/sourceNodeId` 对前端统一表示为 `number | null`；故事 `topicId` 对前端表示为 `number | null`，接口显式返回 `null`。
- V54 新建 `pms_project_member_auto_managed(project_id,user_id,created_at)` 和 `pms_project_member_assignment_ref(project_id,item_type,item_id,assignment_type,assignment_id,user_id,created_at)`；前者只记录由本功能新插入且仍由系统管理的普通项目成员，后者以 `(project_id,item_type,item_id,assignment_type,assignment_id)` 唯一定位一项有效分配。
- auto-managed marker 以 `(project_id,user_id)` 复合外键引用唯一 `project_member` 行并级联删除；assignment ref 外键引用项目与用户，但不引用成员或多态的专题/故事/节点/任务记录，因为历史有效分配可能尚无成员记录，源记录生命周期由同事务显式维护。增加按 `(project_id,user_id)` 和 `(project_id,item_type,item_id)` 的索引。
- `item_type` 持久化现有 `DevelopmentItemType` 中的 `TOPIC`/`STORY`；`assignment_type` 由新 `DevelopmentAssignmentType` 枚举限定为 `TOPIC_OWNER`、`STORY_OWNER`、`WORKFLOW_NODE_OWNER`、`TASK_ASSIGNEE`。owner 的 `assignment_id` 为对应专题/故事 ID；节点 owner 的 `assignment_id` 为流程节点 ID；任务含父任务和子任务，`assignment_id` 为任务 ID。批量解绑按项目和事项上下文释放其全部分配引用。
- V54 回填所有已绑定专题/故事 owner、其流程节点 owner 及未软删除任务/子任务 assignee 的分配引用，供后续解绑/改派时避免错误回收；历史 `project_member` 不写入自动管理标记，迁移不推断其来源，也不改变历史成员角色或删除记录。
- API 固定为 `replaceAssignment(Long projectId, DevelopmentItemType itemType, Long itemId, DevelopmentAssignmentType assignmentType, Long assignmentId, Long userId)`、`releaseAssignment(Long projectId, DevelopmentItemType itemType, Long itemId, DevelopmentAssignmentType assignmentType, Long assignmentId)` 和 `synchronizeItemAssignments(Long sourceProjectId, Long targetProjectId, DevelopmentItemType itemType, Long itemId)`。替换通过稳定 assignment key 找到旧 user 并释放，再确保新 user 的成员与引用；同步时从专题/故事持久化 owner、所有流程节点 owner 及未删除任务/子任务 assignee 重建目标引用（覆盖 source 为空的首次绑定），目标成员仅为当前 ACTIVE 且未删除的指派人自动确保。历史停用/删除用户仍保留引用和展示、不阻断绑定，也不新建其成员关系；source 引用在目标引用建立后释放，target 为空时仅释放 source 引用。调用方以事务包裹全流程。

- [ ] **Step 1: 先写迁移契约测试**

```java
@Test
void optionalDevelopmentContextMigrationMakesProjectContextNullable() throws IOException {
    Path migration = Path.of("src/main/resources/db/migration/V54__optional_development_item_project_context.sql");
    String sql = Files.readString(migration);
    assertThat(sql).contains("MODIFY COLUMN project_id BIGINT NULL", "MODIFY COLUMN node_id BIGINT NULL",
            "MODIFY COLUMN source_node_id BIGINT NULL");
    assertThat(sql).contains("project_node_development_topic", "project_node_development_story",
            "pms_development_item_workflow");
    assertThat(sql).contains("MODIFY COLUMN topic_id BIGINT NULL");
}
```

- [ ] **Step 2: 运行 `mvn -Dtest=FlywayMigrationVersionTest test`，确认契约测试因 V54 不存在而失败；新增基于隔离 MySQL Testcontainer 和 programmatic Flyway 的迁移测试，以元数据断言专题/故事/工作流来源上下文列及故事 `topic_id` 均可空，且两张成员引用表存在。**
- [ ] **Step 3: 新增 V54 迁移**，将专题、故事和流程实例的 `project_id`，专题/故事的项目 `node_id`、流程实例的 `source_node_id` 及故事 `topic_id` 改为 nullable；按原定义保留列类型、默认值、索引及已声明外键，已有非空值不变。创建两个成员表及外键、唯一键和 `(project_id,user_id)` 引用查询索引；回填现存绑定事项的 owner、流程节点 owner、未删除任务/子任务 assignee 引用，不给历史 `project_member` 添加自动管理标记、不改变角色或删除记录；readiness migration version 更新为 54。**
- [ ] **Step 4: 移除请求 DTO 对 `projectId` 的 `@NotNull`，更新前端专题命令、故事命令、行/详情类型以表达可空关联；清空关联时序列化为显式 `null`，不得隐式省略成旧关联值。**
- [ ] **Step 5: 增加迁移集成断言**：Testcontainer 中先用 Flyway `target("53")` 完成历史迁移，插入已有成员与历史分配夹具，再用 `target("54")` 执行新增迁移；确认两类指派人都有 ref、已有成员没有 auto-managed marker、成员集合及事项上下文不变。每次使用新隔离 database，不依赖本地开发库。
- [ ] **Step 6: 重跑 `mvn -Dtest=FlywayMigrationVersionTest,OptionalDevelopmentContextMigrationTest,ProjectMemberAssignmentMigrationTest test`、前端 `pnpm typecheck`，检查 `git diff --check`。**
- [ ] **Step 7: 自审迁移仅放宽来源上下文/专题关联列并新增成员追踪表；确认历史模板版本、流程内部节点 ID、成员角色及非空事项上下文不变，无自动标记误判；工作区已有其他未提交改动不提交，后续仅准确隔离本功能文件。**

### Task 2: 专题创建、绑定、改绑与解绑

**Files:**
- Modify: `src/main/java/com/brad/pms/service/DevelopmentTopicManagementService.java`
- Modify: `src/main/java/com/brad/pms/service/MemberService.java`
- Create: `src/main/java/com/brad/pms/service/ProjectMemberAssignmentService.java`
- Create: `src/main/java/com/brad/pms/common/enums/DevelopmentAssignmentType.java`
- Create: `src/main/java/com/brad/pms/entity/ProjectMemberAutoManagedDO.java`
- Create: `src/main/java/com/brad/pms/entity/ProjectMemberAssignmentRefDO.java`
- Create: `src/main/java/com/brad/pms/mapper/ProjectMemberAutoManagedMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/ProjectMemberAssignmentRefMapper.java`
- Modify: `src/main/java/com/brad/pms/mapper/DevelopmentItemWorkflowMapper.java`
- Modify: `src/main/java/com/brad/pms/mapper/DevelopmentItemWorkflowNodeMapper.java`
- Modify: `src/main/java/com/brad/pms/mapper/DevelopmentItemTaskMapper.java`
- Modify: `src/main/java/com/brad/pms/controller/DevelopmentTopicController.java`
- Modify: `src/test/java/com/brad/pms/service/DevelopmentTopicManagementServiceTest.java`
- Create: `src/test/java/com/brad/pms/service/MemberServiceTest.java`
- Create: `src/test/java/com/brad/pms/service/ProjectMemberAssignmentServiceTest.java`
- Create: `src/test/java/com/brad/pms/service/DevelopmentTopicRebindRollbackIntegrationTest.java`
- Create: `src/test/java/com/brad/pms/service/UserServiceTest.java`
- Modify: `src/main/java/com/brad/pms/service/UserService.java`

**Interfaces:**
- `create(cmd)` 在 `cmd.projectId == null` 时创建专题并以 `(TOPIC, id, null, null)` 初始化流程。
- `UserService.requireActiveUser(Long userId)` 返回 `UserDO`，对非空的新负责人执行存在、ACTIVE、未删除校验；负责人允许清空。
- 同包内部 API `MemberService.ensureMemberForAssignment(projectId,userId)` 使用 `project_member(project_id,user_id)` 唯一键原子化插入并返回 `{member, inserted}`；已有成员不改角色，`inserted == true` 时由 `ProjectMemberAssignmentService` 写 auto-managed marker。它不重复执行项目成员管理 ACL，调用方必须先授权事项赋值；新增成员仍记审计。`MemberService.removeAutoManagedMember(projectId,userId)` 仅供同包分配服务在 marker/ref/role 检查通过后移除并记录审计，不能复用要求项目管理权限的人工移除入口。人工成员编辑/角色变更通过 `ProjectMemberAutoManagedMapper` 清 marker，不反向依赖 assignment service。
- `ProjectMemberAssignmentService.replaceAssignment(...)` 以 `DevelopmentItemType`、事项 ID、`DevelopmentAssignmentType` 和赋值记录 ID作为稳定键替换当前 user 引用；非空 project/user 时先验证 active user，再原子 ensure 成员，若本次新建普通成员则写 auto-managed marker，之后 upsert 分配引用；user 变更或设空时在同一事务释放旧 user 并按引用数安全回收。该入口代表新增/变更指派，停用或删除用户必须拒绝。
- `releaseAssignment(...)` 删除一个稳定分配键对应的引用；事项级全量重建/解绑统一走 `synchronizeItemAssignments(...)`，避免遗漏无项目时期已有的分配。
- `synchronizeItemAssignments(sourceProjectId,targetProjectId,itemType,itemId)` 从持久化负责人/节点/任务记录生成目标项目当前完整引用集；source 可空（独立事项首次绑定），target 可空（解绑）。source 与 target 不同时，先 upsert 目标引用并仅为当前 ACTIVE 且未删除的历史指派人确保普通成员，再释放 source 的该事项引用；inactive/deleted 历史指派保留显示和引用、不因此阻断绑定，也不新建成员。source 与 target 相同时，仅补齐当前引用并清理不再存在的旧 assignment key，不能把刚重建的引用一起删除。专题绑定/改绑时须分别对专题及每个关联故事调用。
- 归零回收按 `(project_id,user_id)` 串行化检查：对已有 `project_member` 行先 `SELECT ... FOR UPDATE`，再检查引用数；仍有引用则保留；无 marker 则保留；有 marker 但角色不再是普通成员则只清除 marker、保留成员；否则删除成员和 marker并记录移除审计。成员缺失时使用唯一键原子插入并重新读取，处理并发确保；人工变更成员角色须清 marker，使该成员转为人工维护。
- `requireReadableTopic(Long id)` 和 `requireWritableTopic(Long id)` 返回未删除专题；若专题绑定项目，则分别验证现有项目读取/管理范围；无项目事项的模块权限由其 controller `PROJECT_READ/PROJECT_WRITE` 注解验证。
- `update(id, cmd)` 支持项目上下文四种迁移：空→空、空→项目、项目→项目、项目→空；负责人不因项目变更而清空。
- 绑定项目时继续使用 `resolveTopicSourceProjectNodeKey()` 解析目标节点，项目必须进行中且由操作者可管理。
- 新增/变更的 topic owner 通过全局 active-account 校验，不要求预先是项目成员；有项目时以 `DevelopmentAssignmentType.TOPIC_OWNER` assignment reference 确保普通成员。专题改绑时从持久化状态为专题及现存故事重建目标项目的 owner/node/task refs；解绑时释放专题及故事全部 refs 并按规则回收。专题、故事、流程、成员、marker 和 refs 在同一事务内更新。

- [ ] **Step 1: 先添加两个失败的生命周期测试**

在现有 `DevelopmentTopicManagementServiceTest` 中 mock `UserService`、`ProjectMemberAssignmentService` 并注入服务，新增以下创建/解绑用例；另在 `UserServiceTest` 中直接覆盖 active 和 inactive 状态：

```java
@Test
void createsUnboundTopicAndUsesGlobalOwnerValidation() {
    when(developmentItemWorkflowService.createIfDefaultExists(DevelopmentItemType.TOPIC, 99L, null, null))
            .thenReturn(workflow(3000L, "TOPIC", 99L, null, null, 501L));
    doAnswer(invocation -> {
        ProjectNodeDevelopmentTopicDO inserted = invocation.getArgument(0);
        inserted.setId(99L);
        return 1;
    }).when(topicMapper).insert(any(ProjectNodeDevelopmentTopicDO.class));

    Long id = service.create(update("新专题", 88L, null));

    assertThat(id).isEqualTo(99L);
    verify(userService).requireActiveUser(88L);
    verify(permissionService, never()).requireProjectMember(anyLong(), anyLong());
    verify(projectMemberAssignmentService, never()).replaceAssignment(any(), any(), any(), any(), any(), any());
    verify(developmentItemWorkflowService).createIfDefaultExists(DevelopmentItemType.TOPIC, 99L, null, null);
}

@Test
void unbindsTopicStoriesAndWorkflowsWithoutDeletingTheirHistory() {
    ProjectNodeDevelopmentTopicDO topic = topic(10L, 1L, 11L, 88L);
    ProjectNodeDevelopmentStoryDO story = story(30L, 10L, 1L, 11L, 99L);
    DevelopmentItemWorkflowDO topicFlow = workflow(1000L, "TOPIC", 10L, 1L, 11L, 501L);
    DevelopmentItemWorkflowDO storyFlow = workflow(2000L, "STORY", 30L, 1L, 11L, 502L);
    when(topicMapper.selectByIdForUpdate(10L)).thenReturn(topic);
    when(projectMapper.selectIncludingDeleted(1L)).thenReturn(project(1L, 1));
    when(permissionService.canManageProject(any(ProjectDO.class))).thenReturn(true);
    when(storyMapper.selectList(any())).thenReturn(List.of(story));
    when(storyMapper.selectByIdForUpdate(30L)).thenReturn(story);
    when(workflowMapper.selectForUpdate("TOPIC", 10L)).thenReturn(topicFlow);
    when(workflowMapper.selectForUpdate("STORY", 30L)).thenReturn(storyFlow);
    when(taskMapper.selectByWorkflowIdsForUpdate(any())).thenReturn(List.of());
    when(topicMapper.updateById(any(ProjectNodeDevelopmentTopicDO.class))).thenReturn(1);
    when(storyMapper.updateById(any(ProjectNodeDevelopmentStoryDO.class))).thenReturn(1);
    when(workflowMapper.updateById(any(DevelopmentItemWorkflowDO.class))).thenReturn(1);

    service.update(10L, update("专题", 88L, null));

    assertThat(topic.getProjectId()).isNull();
    assertThat(topic.getNodeId()).isNull();
    assertThat(story.getProjectId()).isNull();
    assertThat(story.getNodeId()).isNull();
    assertThat(topicFlow.getTemplateVersionId()).isEqualTo(501L);
    assertThat(storyFlow.getTemplateVersionId()).isEqualTo(502L);
    assertThat(topicFlow.getSourceNodeId()).isNull();
    assertThat(storyFlow.getSourceNodeId()).isNull();
}
```

再增加 RED 用例：独立专题/故事已有 owner、多个流程节点 owner 及父/子任务 assignee 后首次绑定项目，全部被扫描并创建 refs，active 人员加入目标项目；负责人变更释放旧 ref 并加入新 owner；项目间改绑时专题及故事当前 owner/node/task 人员均在目标项目有 refs；解绑释放专题和故事全部 refs。另验证 rebind 遇到已停用的历史 assignee 时保留 ref、不新增其成员且不阻断流程。`ProjectMemberAssignmentServiceTest` 覆盖幂等重复赋值、替换 user、source 为空首次绑定、source/target 不同改绑、source/target 相同重建、解绑、剩余引用保留、最后引用归零后只回收系统新增 MEMBER、已有成员不标记、管理员/负责人角色保留并清除 marker、软删除任务释放 assignee ref。`MemberServiceTest` 验证已有成员保留角色；人工成员编辑/提升角色会解除 auto-managed 标记。

- [ ] **Step 2: 运行 `mvn -Dtest=DevelopmentTopicManagementServiceTest test`，确认测试因当前必填项目和项目成员校验而失败。**
- [ ] **Step 3: 实现无项目创建与全局负责人校验；无项目时跳过项目/节点查找，仍要求默认已发布专题流程并初始化事项流程。有项目且 owner 非空时通过 `replaceAssignment(projectId,DevelopmentItemType.TOPIC,topicId,DevelopmentAssignmentType.TOPIC_OWNER,topicId,ownerId)` 同事务登记成员/ref。**
- [ ] **Step 4: 实现 update 的改绑与解绑分支；锁定专题、故事、流程节点和任务；在一个事务中同步项目上下文、清空里程碑/迭代引用并保留流程及任务历史。绑定/改绑时对专题及每个关联故事调用 `synchronizeItemAssignments(sourceProjectId,targetProjectId,itemType,itemId)`，从持久化 owner/node/task 状态重建完整目标 refs（包括 source 为空的首次绑定）；解绑 target 为空时释放 source refs；仅回收符合 marker/ref/role 三条件的旧成员。**
- [ ] **Step 5: 无项目记录的增改删恢复 API 均要求 `@RequirePermission(PROJECT_WRITE)`；有关联项目时服务层继续追加原有源/目标项目可管理校验。项目选项查询仍为 `PROJECT_READ`。为 create/update/delete/restore 添加 controller 权限契约测试，逐个反射断言方法上的注解值。**
- [ ] **Step 6: 运行 `mvn -Dtest=DevelopmentTopicManagementServiceTest,UserServiceTest,NodeDevelopmentControlServiceTest test`。**
- [ ] **Step 7: 扩展 Testcontainers 集成测试：先创建无项目专题/故事并保存 owner/node/task assignments，再绑定到项目，断言即使 source 无 refs 也会扫描全部已持久化指派并创建目标普通成员及 refs；项目间改绑与解绑分别核验新旧引用回收。用 `@SpyBean ProjectNodeDevelopmentStoryMapper` 让故事 `updateById` 在改绑中途抛错，断言上下文、成员、marker/ref 全回滚。解绑验证只删除系统新增、无其他 ref 的普通成员，保留预先存在成员、被其他事项引用者以及角色已提升成员。运行 `mvn -Dtest=DevelopmentTopicRebindRollbackIntegrationTest test`，确认事务原子性；随后自审模板快照、任务、审计和项目引用，再进入下一阶段。**

`DevelopmentTopicRebindRollbackIntegrationTest` 使用 `@SpringBootTest @ActiveProfiles("test")`、`JdbcTemplate` 和真实事务代理，不给测试方法添加 `@Transactional`，确保失败后在新查询中读到数据库已回滚的值。固定插入字段包括 project `(code,name,owner_id,created_by,status,priority)`、node `(project_id,node_key,name,status,sort)`、topic `(project_id,node_id,title,owner_id,test_status,sort,deleted)`、story `(project_id,node_id,topic_id,title,owner_id,status,progress,story_points,sort)`、workflow `(item_type,item_id,project_id,source_node_id,template_version_id,version)`、workflow node `(workflow_id,node_key,name,status,sort,owner_id)`、task `(workflow_id,node_id,parent_id,title,status,assignee_id,deleted,sort)`、project member `(project_id,user_id,role)` 及对应 marker/ref；`template_version_id` 使用测试库已发布夹具。项目和 `UserContext` 均使用测试库中的管理员账号，`@AfterEach` 按 assignment refs → auto-managed markers → story → topic → workflow（级联任务/流程节点）→ project_member → project_node → project 顺序清理，避免新外键阻塞夹具回收。

`UserServiceTest.requireActiveUserRejectsInactiveAccount` 使用 `when(userMapper.selectById(88L)).thenReturn(user(88L, "DISABLED", false))`，断言 `assertThatThrownBy(() -> service.requireActiveUser(88L)).isInstanceOf(BusinessException.class)`；另用 `user(88L, "ACTIVE", false)` 断言返回同一 `UserDO`，并用 mapper 返回 `null` 覆盖用户不存在。测试 helper `user(id, status, deleted)` 填写这三个字段。

### Task 3: 事项流程无项目运行与全公司指派

**Files:**
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify: `src/main/java/com/brad/pms/controller/DevelopmentItemWorkflowController.java`
- Modify: `src/main/java/com/brad/pms/service/UserService.java`
- Modify: `src/main/java/com/brad/pms/mapper/UserMapper.java`
- Modify: `src/main/java/com/brad/pms/mapper/DevelopmentItemWorkflowNodeMapper.java`
- Modify: `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`
- Modify: `src/main/java/com/brad/pms/service/MemberService.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectMemberAssignmentService.java`
- Modify: `src/main/java/com/brad/pms/mapper/ProjectMemberAssignmentRefMapper.java`
- Modify: `src/main/java/com/brad/pms/mapper/DevelopmentItemTaskMapper.java`
- Modify: `src/test/java/com/brad/pms/service/UserServiceTest.java`
- Modify: `src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceNodeEditTest.java`
- Modify: `src/test/java/com/brad/pms/service/NodeDevelopmentControlServiceTest.java`
- Create: `src/test/java/com/brad/pms/controller/DevelopmentItemWorkflowControllerTest.java`

**Interfaces:**
- `loadContext` 返回可空的 project/source-node 上下文；绑定数据仍验证项目与来源节点相符。
- `createIfDefaultExists` 接受 `(null,null)`，拒绝只空一项；事项归属通过 TOPIC/STORY 自身及父专题校验。
- `UserService.requireActiveUser(Long userId)` 验证目标 `UserDO` 存在、`status == ACTIVE`、未删除；`UserMapper.search(keyword)` SQL 仅返回 ACTIVE 且未删除用户。专题/故事 owner、流程节点 owner、任务及子任务 assignee 统一调用同一校验；绑定项目时所有四类 assignment 都通过 `ProjectMemberAssignmentService.replaceAssignment(...)` 确保普通成员并登记引用。
- `UserService.listByIdsIncludingDeleted(Collection<Long>)` 仅用于授权详情中展示历史指派人；SQL 按显式 ID 集合查询，包含停用/软删除账户，不参与候选搜索或新指派校验。

- [ ] **Step 1: 写失败用例，覆盖无项目 topic 详情可加载、可编辑节点/排期/字段/任务，以及停用人员被拒绝为新指派；再覆盖用户不可读的绑定项目专题详情必须拒绝，不能因无项目专题全员可读而绕过项目 ACL。**

```java
@Test
void loadsUnboundTopicWorkflowWithoutDereferencingProjectContext() {
    Fixture fixture = new Fixture(0);
    fixture.topic.setProjectId(null);
    fixture.topic.setNodeId(null);
    fixture.workflow.setProjectId(null);
    fixture.workflow.setSourceNodeId(null);
    when(fixture.topicMapper.selectById(7L)).thenReturn(fixture.topic);
    when(fixture.workflowMapper.selectByItem("TOPIC", 7L)).thenReturn(fixture.workflow);

    DevelopmentItemWorkflowDetailDTO result = fixture.service.detail(DevelopmentItemType.TOPIC, 7L);

    assertThat(result.getProjectId()).isNull();
    assertThat(result.getSourceNodeId()).isNull();
    assertThat(result.getNodes()).hasSize(1);
}
```

Before this test, promote the fixture's existing local `topic` and `workflow` objects to package-private fixture fields; keep the existing constructor setup and add no new project fixture for the null-context branch.

- [ ] **Step 2: 运行 `mvn -Dtest=DevelopmentItemWorkflowServiceNodeEditTest test`，确认当前 null context 路径失败。**
- [ ] **Step 3: 将 `ItemContext.project/sourceNode` 变为可空，并在 detail、update、complete、task CRUD 的可读/可写授权处分开处理：有关联项目走项目 ACL，无项目走 Controller 的模块权限；任何路径都重新验证事项归属和专题未删除状态。**
- [ ] **Step 4: owner、PERSON 字段、流程节点负责人、任务/子任务执行人的新增/变更改用 `UserService.requireActiveUser(Long)` 校验；`/users/search` SQL 过滤 `status = 'ACTIVE' AND deleted = FALSE`；只读返回时按历史 ID 解析已有用户显示名，不要求通过 active 校验。每次写入对比旧/新人员并以稳定 assignment key 调用 `replaceAssignment`；清空人员及任务软删除时调用 `releaseAssignment`。`NodeDevelopmentControlService` 保存专题/故事 owner 时也复用 assignment service；无项目 context 不生成 project ref/member。节点和父/子任务必须各自建立、释放独立 ref。**

为 `DevelopmentItemWorkflowServiceNodeEditTest` 增加回归：停用用户作为新节点负责人时抛 `BusinessException`；停用或软删除用户已存在于节点/任务记录时，`detail()` 仍返回原 owner/assignee ID 和历史显示名，不清空历史值。详情展示改用 `UserService.listByIdsIncludingDeleted(Collection<Long>)`。另在 `UserServiceTest` 反射读取 `@Select` 的 `search` SQL，断言包含 `status = 'ACTIVE'` 与 `deleted = FALSE`，并验证历史 ID 查询包含 `deleted` 行但不参与 `search`。
- 为 `DevelopmentItemWorkflowServiceNodeEditTest` 增加引用测试：绑定项目下设置/改派/清空节点负责人，创建并改派父任务与子任务执行人，软删除已指派任务；分别断言普通成员、marker、每个 assignment ref 的建立/迁移/释放及引用归零回收。自定义 `PERSON` 模板字段仅校验 active 用户，不作为项目成员 assignment 来源。用事务集成测试验证成员/引用写入失败时节点/任务修改整体回滚。
- [ ] **Step 5: 为 TOPIC/STORY context 设置一致性校验：project 与 source node 同时为空或同时有效；story 与父 topic 的 project/node 必须一致；增加两项不一致组合测试（project 为空但 node 非空、project/node 都有值但与父专题不匹配），并确认均以业务错误拒绝而不产生 NPE。**
- [ ] **Step 6: 运行 `mvn -Dtest=DevelopmentItemWorkflowServiceNodeEditTest,NodeDevelopmentControlServiceTest,ProjectMemberAssignmentServiceTest test`。**
- [ ] **Step 7: 自审无项目节点推进、字段校验、任务父子约束、版本冲突和权限路径；验证专题/故事详情及项目节点“开发与迭代控制”两处写入的 owner/node/task/subtask assignments 全部自动确保成员并追踪 refs，清空/改派/软删除时正确释放，独立事项无成员副作用；确认流程仍按模板顺序推进，并确认读接口为 PROJECT_READ、所有写接口为 PROJECT_WRITE；再进入下一阶段。**

### Task 4: 专题与独立故事列表的可见范围

**Files:**
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemService.java`
- Modify: `src/main/java/com/brad/pms/mapper/ProjectNodeDevelopmentTopicMapper.java`
- Modify: `src/test/java/com/brad/pms/service/DevelopmentItemServiceTest.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentTopicListDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentStoryListDTO.java`

**Interfaces:**
- 无 project filter 时返回当前用户可读项目中的已绑定事项，并合并所有未关联项目的正常事项。
- 无 project filter 时故事列表返回当前用户可读项目下的专题故事、无项目专题下的故事，以及 `topic_id IS NULL` 的独立故事。
- project filter 有值时只返回该项目上下文的事项，不混入无项目专题/故事或独立故事。
- 专题删除列表合并用户有治理权限的无项目软删除专题；故事列表始终隐藏软删除父专题下的故事，项目删除记录继续遵守现有项目治理范围。
- `ProjectNodeDevelopmentTopicMapper.selectUnbound(Boolean deleted)` 查询 `project_id IS NULL` 的专题，可精确区分正常/已删除专题；故事查询包含 `topic_id IS NULL` 的独立故事，并按父专题可见范围查询关联故事。

- [ ] **Step 1: 写服务测试，验证无项目专题对有 `PROJECT_READ` 的用户可见、不可读的已绑定项目仍被过滤，以及具体项目筛选不返回无项目记录。**

```java
@Test
void listsUnboundTopicsForModuleReadersWithoutLeakingUnreadableProjects() {
    ProjectNodeDevelopmentTopicDO standalone = topic(901L, null, null, "独立专题", 9L);
    when(projectService.listReadableIds()).thenReturn(List.of());
    when(topicMapper.selectUnbound(false)).thenReturn(List.of(standalone));
    when(storyMapper.selectList(any())).thenReturn(List.of());
    when(iterationPlanMapper.selectList(any())).thenReturn(List.of());
    DevelopmentItemPageQry query = new DevelopmentItemPageQry();

    var page = service.pageTopics(query);

    assertThat(page.getList()).singleElement().satisfies(row -> {
        assertThat(row.getId()).isEqualTo(901L);
        assertThat(row.getProjectId()).isNull();
        assertThat(row.getNodeId()).isNull();
    });
}
```

Extend the test with an independent story (`topicId == null`, project/node null) and a story under an unbound topic; assert both are visible to a module reader. Then set `query.setProjectId(7L)`, assert neither appears, and verify unbound-topic lookup is skipped. Retain `doesNotListDataFromProjectsOutsideReadableScope` to verify bound topics/stories outside readable scope remain absent. Add deleted-scope test for standalone deleted topics.

- [ ] **Step 2: 运行 `mvn -Dtest=DevelopmentItemServiceTest test`，确认当前 projects-empty 早退和 `.in(projectIds)` 查询导致无项目数据缺失。**
- [ ] **Step 3: 增加 `ProjectNodeDevelopmentTopicMapper.selectUnbound(Boolean deleted)` 查询未关联项目专题；在服务层分开查用户可读项目 ID 与 unbound 记录，避免空集合 `.in`。故事列表补充 `topic_id IS NULL` 的独立故事；有关联专题的故事仅在父专题可读时展示。删除列表仅在 `PROJECT_WRITE` 时合并可治理的独立已删除专题；保留状态、软删除、分页和排序语义。显式项目筛选只查该项目绑定项，且不返回独立故事/专题。**
- [ ] **Step 4: 让 DTO 明确返回 nullable project/node 信息；统计专题故事数量和流程摘要时仍包含独立专题。**
- [ ] **Step 5: 运行 `mvn -Dtest=DevelopmentItemServiceTest test` 并自审混合/空/删除/项目过滤组合。**
- [ ] **Step 6: 自审确认新可见范围只扩展到 unbound items，没有将非用户可读项目里的绑定专题/故事并入结果。**

### Task 5: 故事可选专题关联的创建与编辑

**Files:**
- Create: `src/main/java/com/brad/pms/service/DevelopmentStoryManagementService.java`
- Create: `src/main/java/com/brad/pms/controller/DevelopmentStoryManagementController.java`
- Create: `src/main/java/com/brad/pms/dto/request/DevelopmentStorySaveCmd.java`
- Create: `src/main/java/com/brad/pms/dto/response/DevelopmentTopicStoryDTO.java`
- Create: `src/test/java/com/brad/pms/service/DevelopmentStoryManagementServiceTest.java`
- Create: `src/test/java/com/brad/pms/controller/DevelopmentStoryManagementControllerTest.java`
- Modify: `src/main/java/com/brad/pms/mapper/ProjectNodeDevelopmentStoryMapper.java`

**Interfaces:**
- `POST /development/stories` 创建故事；保存命令包含可空 `topicId`，没有专题时创建独立故事，有专题时由服务端从专题派生 project/node。命令不含 `projectId` 或项目 `nodeId`。
- `PUT /development/stories/{id}` 编辑故事基本信息及可选 `topicId`；显式清空 topicId 解除关联，不能直接编辑故事项目上下文。
- `GET /development/topics/{topicId}/stories` 仅返回该专题下未删除故事，详情列表通过此接口刷新。
- 创建/编辑故事时使用现有故事状态/日期/故事点校验；关联专题必须存在且未删除。新故事按照现有默认故事流程规则初始化，独立故事同样可使用故事模板流程。
- 故事 owner 从全公司 active 用户选取；当新/改后的上下文有项目时登记 `DevelopmentAssignmentType.STORY_OWNER` 分配引用并确保普通成员；故事关联改绑/解绑时同步该故事 owner/node/task refs 并按规则回收；无专题时不产生项目成员关系。
- 故事列表提供创建与编辑入口；专题详情“新增故事”入口复用相同保存逻辑并预选当前专题，仍允许之后改绑或解除；项目/节点上下文不可在故事表单直接指定。
- `DevelopmentTopicStoryDTO` 返回 `id,title,ownerId,ownerName,status,progress,storyPoints,startDate,dueDate,blocker,sort`，故事状态和负责人展示沿用现有故事列表枚举与姓名格式。

服务端固定接口为 `Long DevelopmentStoryManagementService.create(DevelopmentStorySaveCmd cmd)`、`void update(Long id, DevelopmentStorySaveCmd cmd)` 和 `List<DevelopmentTopicStoryDTO> listByTopic(Long topicId)`。保存命令属性为 `Long topicId, String title, Long ownerId, String status, Integer progress, Integer storyPoints, LocalDate startDate, LocalDate dueDate, String blocker, Integer sort`；复用 `NodeDevelopmentStoryCmd` 的长度及数值约束，但不含 `projectId`、项目 `nodeId` 或 `iterationPlanId`。Mapper 提供 `selectByTopicId(Long topicId)` 查询专题下故事；独立故事通过 `topic_id IS NULL` 查询。更新关联时锁定 story、关联前后的 topic 及 workflow，验证源/目标访问范围并在事务中同步 story 与 workflow project/source-node context，清空旧迭代计划引用；任一写入失败完整回滚。

- [ ] **Step 1: 写 `DevelopmentStoryManagementServiceTest`，验证无 topicId 时创建独立故事、project/node 均为空，topicId 命令字段存在但不存在 project/node 字段；再验证选 topic 时上下文从专题派生且 owner 自动确保进入目标项目。新增 controller contract test，断言 POST/PUT 为 PROJECT_WRITE、专题故事列表 GET 为 PROJECT_READ。**

测试类声明 `@Mock ProjectNodeDevelopmentStoryMapper storyMapper; @Mock DevelopmentTopicManagementService topicManagementService; @Mock DevelopmentItemWorkflowService developmentItemWorkflowService; @Mock UserService userService; @Mock ProjectMemberAssignmentService projectMemberAssignmentService; @InjectMocks DevelopmentStoryManagementService service;`，并导入 `java.lang.reflect.Field`、`java.util.Arrays`。

```java
@Test
void createsStoryWithoutTopicAndPinsDefaultStoryWorkflow() {
    when(storyMapper.insert(any(ProjectNodeDevelopmentStoryDO.class))).thenAnswer(invocation -> {
        ((ProjectNodeDevelopmentStoryDO) invocation.getArgument(0)).setId(81L);
        return 1;
    });
    DevelopmentStorySaveCmd cmd = new DevelopmentStorySaveCmd();
    cmd.setTitle("拆分后的故事");

    Long storyId = service.create(cmd);

    assertThat(storyId).isEqualTo(81L);
    verify(developmentItemWorkflowService).createIfDefaultExists(DevelopmentItemType.STORY, 81L, null, null);
    assertThat(Arrays.stream(DevelopmentStorySaveCmd.class.getDeclaredFields())
            .map(Field::getName).toList()).contains("topicId")
            .doesNotContain("projectId", "nodeId", "iterationPlanId");
}
```
- [ ] **Step 2: 运行 `mvn -Dtest=DevelopmentStoryManagementServiceTest,DevelopmentStoryManagementControllerTest test`，确认新服务方法缺失导致测试失败。**
- [ ] **Step 3: 实现独立故事创建与绑定专题创建；有关联 topic 时调用 `requireWritableTopic`，派生 project/node，校验 active owner 并登记 `DevelopmentAssignmentType.STORY_OWNER` ref/确保成员；无 topic 时 project/node 为 null。**
- [ ] **Step 4: 实现故事改绑与解除关联；事务内锁定 story、关联前后的 topic、workflow、nodes 和 tasks，验证源/目标访问范围，同步上下文并清空失效 iteration reference；移动/释放该故事 owner/node/task refs，目标项目先确保所有相关成员，旧项目按 marker/ref/role 三条件安全回收。新增中途 mapper 抛错集成测试，断言 story、workflow、membership、marker、refs 全回滚。**
- [ ] **Step 5: 运行 `mvn -Dtest=DevelopmentStoryManagementServiceTest,DevelopmentItemWorkflowServiceNodeEditTest test`。**
- [ ] **Step 6: 自审故事字段约束、无 topic 工作流初始化、软删除专题边界、独立/关联故事列表权限和成员副作用；不改项目节点工作台已有 story-save 领域字段。**

### Task 6: 前端专题编辑、列表、详情和全员人员选择

**Files:**
- Modify: `../pms-front/src/views/development/DevelopmentTopicEditModal.vue`
- Create: `../pms-front/src/views/development/DevelopmentStoryEditModal.vue`
- Modify: `../pms-front/src/views/development/DevelopmentListPage.vue`
- Modify: `../pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Create: `../pms-front/src/views/development/detail/TopicStorySection.vue`
- Modify: `../pms-front/src/views/development/detail/components/DevelopmentItemTaskBoard.vue`
- Modify: `../pms-front/src/views/project/detail/components/DevelopmentControlWorkbench.vue`
- Modify: `../pms-front/src/api/development-item.ts`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`
- Modify: `../pms-front/src/views/development/development-list.test.mjs`
- Create: `../pms-front/src/views/development/development-story-edit.test.mjs`
- Create: `../pms-front/src/views/development/topic-story-section.test.mjs`
- Modify: `../pms-front/docs/user-manual.md`
- Modify: `../pms-front/src/views/manual/manual.test.mjs`

**Interfaces:**
- Topic create/update uses `projectId: number | null`; story create/update uses `topicId: number | null`; both associations can be explicitly cleared.
- Topic/story owner, workflow-node owner, task and subtask assignees use the reusable company-wide person selector and do not wait for a selected project; project-member synchronization is server-side and never narrows person choices to current project members.
- Story editor uses a searchable optional active-topic picker; selecting/clearing a topic never exposes direct project/node inputs. Story owner uses company-wide people search.
- Null project context renders translated “未关联项目”; source-project navigation is absent rather than targeting an invalid URL.

- [ ] **Step 1: Replace the previous “select project before owner” regression test with failing assertions that owner selection is enabled without a project, project selection is optional, and clearing project emits explicit `null`.**

```js
test('topic owner is independent from optional project association', () => {
  const modal = read('views/development/DevelopmentTopicEditModal.vue')
  const detail = read('views/development/detail/DevelopmentItemDetailPage.vue')
  const api = read('api/development-item.ts')
  assert.match(modal, /<PersonSelect[\s\S]*?v-model="form\.ownerId"/)
  assert.doesNotMatch(modal, /:disabled="form\.projectId == null/)
  assert.doesNotMatch(modal, /topicProject" required/)
  assert.doesNotMatch(modal, /topicProjectRequired/)
  assert.match(modal, /projectId:\s*form\.projectId \?\? null/)
  assert.doesNotMatch(detail, /getMembers\(/)
  assert.doesNotMatch(detail, /projects\/\$\{[^}]*projectId/)
  assert.match(api, /projectId:\s*number \| null/)
})
```
- [ ] **Step 2: Run `pnpm test`; verify the old modal and current test contract fail for the newly confirmed rule.**
- [ ] **Step 3: Reuse `src/views/project/detail/components/PersonSelect.vue` for global active-person selection; remove the incorrect project-member loading/disabled-owner logic and do not clear owner when project changes. Keep server-side `/users/search` restricted to selectable active accounts.**
- [ ] **Step 4: Update create/edit forms to allow empty project and clearing an existing binding; keep eligible project search, active-project/node validation messages and rebound semantics.**
- [ ] **Step 5: Render unbound project labels in topic/story rows and details; guard project navigation and skip `getMembers(null)`; make task and project development-control owner selectors use global search; preserve all current bound-topic links and actions.**
- [ ] **Step 6: Add story create/edit modal and list actions; test `POST/PUT /development/stories`, optional topic ID, explicit unlink, and absence of direct project/node fields. In topic detail, implement `TopicStorySection.vue` using `GET /development/topics/{topicId}/stories` and reuse the shared editor with current topic preselected; assert submit/reload behavior.**
- [ ] **Step 7: Run `pnpm test`, `pnpm typecheck` and `pnpm build`; if local runtime/database is available, create an unbound topic and standalone story, set company-wide owners, assign workflow node owner and parent/subtask assignees, bind/rebind/unlink topic/story; verify every project-bound assignee appears as an ordinary member and only eligible auto-added members are reclaimed when their final assignment reference disappears.**
- [ ] **Step 8: Update `docs/user-manual.md` sections 5 and 7.1 to document optional topic-project and story-topic associations, global active-person selection, all owner/node/task/subtask assignees auto-ensured as ordinary project members, safe cleanup on unlink/reassignment, visibility/write permissions, standalone workflows/tasks, binding restrictions, and history preservation. Update Chinese/English business rules and tests to cover assignment references and manual/pre-existing member preservation.**
- [ ] **Step 9: Self-review Chinese/English copy, responsive modal/list/detail, empty/error states, and console output; confirm no list action or existing project-bound path regressed.**

### Task 7: Full verification and cross-repository review

**Files:**
- Review backend and frontend diff; change source only to fix verified defects.

- [ ] **Step 1: Run backend focused suites:** `mvn -Dtest=FlywayMigrationVersionTest,OptionalDevelopmentContextMigrationTest,ProjectMemberAssignmentMigrationTest,UserServiceTest,MemberServiceTest,ProjectMemberAssignmentServiceTest,DevelopmentTopicManagementServiceTest,DevelopmentTopicRebindRollbackIntegrationTest,DevelopmentItemWorkflowServiceNodeEditTest,DevelopmentItemServiceTest,DevelopmentStoryManagementServiceTest,DevelopmentStoryManagementControllerTest,DevelopmentItemWorkflowControllerTest,NodeDevelopmentControlServiceTest,WorkflowComponentBindingServiceTest test`.
- [ ] **Step 2: Run backend full suite:** `mvn test`.
- [ ] **Step 3: Run frontend full tests and production build:** `pnpm test && pnpm build` from the sibling `../pms-front` checkout.
- [ ] **Step 4: If local runtime is available, apply migration through normal startup and exercise create-unbound → edit owner → create story → assign node/parent-task/subtask → bind project → reassign/clear people → rebind/clear project; verify page refresh persistence, every current assignment has the right project member/reference, and only eligible auto-added members are reclaimed.**
- [ ] **Step 5: Review both repository diffs against their pre-existing dirty state; confirm only this feature's hunks are claimed, `git diff --check` is clean, and no secrets/local data were staged.**
- [ ] **Step 6: Report test outputs, any environment-blocked UI/database checks, and remaining limitations before deciding whether to commit the implementation.**

## Phase Gates

After each task, perform the listed self-review and report the test evidence before proceeding to the next task. If an invariant or API choice conflicts with the design spec, stop and ask rather than changing scope silently.
