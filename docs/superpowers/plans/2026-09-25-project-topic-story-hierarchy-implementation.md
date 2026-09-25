# 项目、专题与故事层级及统一 UI 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: After the user approves the plan, use either `superpowers:subagent-driven-development` or `superpowers:executing-plans` task-by-task, according to the user's chosen execution method. Keep the existing working tree and branch; preserve unrelated changes. Each phase ends with a self-review and verification before proceeding.

**Goal:** 建立并验证“项目 → 项目流程节点 → 可配置的专题管理/开发与迭代控制挂载组件 → 专题 → 专题流程节点 → 故事拆分 → 故事 → 故事流程节点 → 任务/子任务”的闭环；同时保证模板只决定业务内容，项目、专题、故事详情页始终使用同一套 UI 设计令牌、节点容器、字段渲染协议和组件注册机制。

**Architecture:**

```text
项目
└── 项目流程节点 C（由专题流程模板配置挂载点）
    └── 专题管理/开发与迭代控制组件（组件固定，挂载节点可变）
        └── 专题 A
            └── 专题流程节点 D
                └── 故事拆分
                    ├── 故事 1
                    │   └── 故事流程节点
                    │       └── 任务/子任务
                    └── 故事 2 ...
```

专题和故事不是同级对象。专题可以独立存在或绑定进行中的项目；故事可以独立存在或绑定专题。故事绑定专题后，故事的项目上下文从专题继承，故事不直接选择项目，也不直接选择项目节点。故事必须归属于专题流程中的具体节点；该节点由专题流程模板配置的故事挂载点决定。

流程实例创建时固定已发布模板版本及运行时挂载快照。后续修改默认模板只影响新建事项，不移动历史事项的节点、字段、任务或故事挂载位置。

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Flyway/MySQL, Vue 3, TypeScript, Ant Design Vue, Node.js built-in test runner, Maven, Docker/Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-25-project-topic-story-hierarchy-design.md`、`docs/business-specification.md`、`../pms-front/docs/user-manual.md`、`../pms-front/src/views/manual/reference.ts`

## 当前基线

- 已有专题/故事独立创建、可选关联和成员自动同步能力，本轮不重复发明另一套关联模型。
- 项目详情已经有项目流程节点和开发树；专题/故事详情已经有独立详情页，但部分动态流程展示仍与项目详情存在两套样式和渲染逻辑。
- `DevelopmentItemWorkflowDO.sourceNodeId` 当前表达项目上下文节点，不能直接复用来表达专题流程节点；需要新增明确的 `topicWorkflowNodeId`。
- 当前专题/故事运行时部分读取“当前默认模板”，这会使默认模板变化后影响历史事项；本轮必须改为创建时快照、详情时按快照读取。
- 当前工作区和分支沿用用户指定状态，不创建新的 worktree；本计划只描述实现，不在计划审阅阶段修改业务代码。

## Global Constraints

- 关系字段必须明确区分：`story.topicId` 是专题上下文，`story.topicWorkflowNodeId` 是专题流程节点；`workflow.sourceNodeId` 仍只表示项目上下文节点。
- 绑定故事必须满足 `topicId != null` 且 `topicWorkflowNodeId != null`；独立故事两个字段都为空。故事不能直接绑定项目。
- 专题流程模板配置一个项目节点挂载点 `sourceProjectNodeKey`；故事流程模板配置一个专题节点挂载点 `sourceTopicNodeKey`。模板配置使用稳定 `nodeKey`，不能使用数据库自增 ID；当前“开发与迭代控制”只是组件名称/当前示例，不是固定挂载节点。
- 专题流程实例必须保存故事挂载快照：`storyMountTemplateVersionId` 和 `storyMountNodeKey`。详情页不得用当前默认故事模板覆盖该快照。
- 项目、专题、故事详情页可以保留各自的业务页组件，但必须共享设计 token、节点容器、字段布局、任务看板和运行时组件注册协议；模板不能返回 CSS、HTML 或任意组件名。
- 未注册的运行时组件不得发布；历史异常 key 只能进入统一降级提示，不能静默渲染旧布局。
- 项目完成、终止、删除后不能新绑定或改绑专题；故事只能绑定未删除的专题。解绑不删除流程历史、任务、子任务和历史指派。
- 负责人、节点负责人和任务执行人必须进入必要的项目成员/上下文引用；解绑时只回收系统自动加入且没有其他引用的成员。
- 历史停用/删除账号继续展示为历史引用，不自动清空，也不能用于新指派。
- 每个阶段必须完成定向测试、差异检查和 self-review，确认闭环后才能进入下一阶段。

## Review Focus

1. 故事只能挂在某个专题流程节点的“故事拆分”组件下；独立故事不出现在任何专题节点，且不产生项目关系。
2. 专题绑定项目时，专题管理组件挂在专题模板配置的项目节点；“开发与迭代控制”不是固定节点，不能在代码中写死节点名称、`develop` key 或数据库 ID。
3. 故事挂载点来自专题实例快照，不能用当前默认模板动态漂移。
4. 变更默认模板不能移动历史事项；新模板只影响新建事项，发布前草稿不参与运行时解析。
5. 专题有故事模板挂载快照但暂无故事时，对应节点显示统一空状态；专题没有可用挂载配置时不显示故事组件。
6. 关联、改绑、解绑和软删除必须保持数据、权限、成员引用、列表展示和详情展示一致，并且失败时事务回滚。
7. 项目、专题、故事的详情 UI 不是复制三份页面，而是由同一套设计 token 和共享工作流原语渲染；模板变化只能改变内容和组件出现位置。

---

### Task 0: 先固定数据模型和历史迁移策略

**Files:**

- Create: `src/main/resources/db/migration/V55__topic_story_workflow_node_context.sql`
- Modify: `src/main/java/com/brad/pms/entity/ProjectNodeDevelopmentStoryDO.java`
- Modify: `src/main/java/com/brad/pms/entity/DevelopmentItemWorkflowDO.java`
- Modify: `src/main/java/com/brad/pms/mapper/ProjectNodeDevelopmentStoryMapper.java`
- Modify: `src/main/java/com/brad/pms/mapper/DevelopmentItemWorkflowMapper.java`
- Modify: `src/main/java/com/brad/pms/mapper/DevelopmentItemWorkflowNodeMapper.java`
- Modify: `src/main/java/com/brad/pms/controller/HealthController.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentStoryListDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentItemWorkflowDetailDTO.java`
- Create: `src/test/java/com/brad/pms/service/TopicStoryWorkflowNodeMigrationTest.java`

**Interfaces:**

- `project_node_development_story.topic_workflow_node_id`：可空，仅对绑定专题的故事有值；它指向父专题流程实例中的节点，不是项目节点。
- `pms_development_item_workflow.project_mount_node_key`：仅专题流程实例使用；专题流程实例已有的 `templateVersionId` 作为项目挂载模板版本，`sourceNodeId` 保存当时解析出的实际项目节点 ID，三者共同固定项目挂载快照。
- `pms_development_item_workflow.story_mount_template_version_id` 和 `story_mount_node_key`：仅专题流程实例使用，用于固定故事组件的模板版本和挂载节点。
- 故事列表/详情 DTO 增加 `topicWorkflowNodeId`、`topicWorkflowNodeName`；独立故事明确返回空关联状态。
- 增加版本化的快照/解析接口，例如 `resolveTopicStoryMount(topicWorkflowId)`，禁止详情读取直接依赖当前默认模板。
- V55 必须先执行可审计回填：项目挂载 key 优先从已绑定专题模板版本解析；故事挂载只有能唯一确定已发布故事模板及挂载节点时才补历史快照和故事节点；无法唯一确定时保留原数据、置空新字段并输出迁移报告，不允许静默猜测。

- [ ] **Step 1: RED**：写 schema、实体映射、DTO 和历史回填测试，覆盖独立故事、绑定故事、旧流程实例、项目模板缺少挂载 key、无默认故事模板、多个候选挂载点等情况。
- [ ] **Step 2: Run RED**：运行 `mvn -Dtest=TopicStoryWorkflowNodeMigrationTest test`，确认新增字段和回填规则尚未实现而失败。
- [ ] **Step 3: Implement**：增加 V55、`project_mount_node_key`、故事节点字段、实体映射和 DTO；实现安全回填及迁移报告，保留旧数据和历史账号引用。
- [ ] **Step 4: GREEN**：运行定向测试、Flyway 校验和 `git diff --check`。
- [ ] **Step 5: Self-review**：确认项目节点、专题节点、故事节点没有字段复用；确认挂载点不是固定节点；确认无法判断的历史数据不会被错误挂载。通过后才能进入 Task 1。

### Task 1: 让流程模板配置和运行时解析使用正确的版本

**Files:**

- Modify: `src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinition.java`
- Modify: `src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java`
- Modify: `src/main/java/com/brad/pms/service/WorkflowTemplateService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentTopicManagementService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentStoryManagementService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify: `src/test/java/com/brad/pms/service/WorkflowTemplateServiceNodeBindingTest.java`
- Modify: `src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java`
- Modify: `../pms-front/src/views/admin/workflows/index.vue`
- Modify: `../pms-front/src/views/admin/workflows/workflow-template-schema.mjs`
- Modify: `../pms-front/src/views/admin/workflows/workflow-template-model.mjs`
- Modify: `../pms-front/src/views/admin/workflows/workflow-admin-visual.test.mjs`
- Modify: `../pms-front/src/views/admin/workflows/workflow-template-model.test.mjs`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`

**Interfaces:**

- `WorkflowTemplateDefinition.sourceTopicNodeKey` 仅用于 `story-management`；`sourceProjectNodeKey` 仅用于 `topic-management`。旧 JSON 缺字段时解析为 `null`。
- 增加 `resolveTopicSourceProjectNodeKey(Long topicTemplateVersionId)` 和 `resolveStorySourceTopicNodeKey(Long storyTemplateVersionId)`；详情、改绑和历史流程只传已绑定版本，不读取当前默认模板。
- `sourceProjectNodeKey` 的选项必须来自可挂载的项目流程模板节点；发布时校验目标节点存在且唯一。新建/改绑专题时按绑定版本解析实际项目节点 ID，并把模板版本/key 快照写入专题上下文；项目详情按节点 key 注入组件，不能通过固定节点名称判断。
- 新建专题/故事时才读取对应流程类型的当前默认已发布版本；创建成功后将模板版本和挂载快照写入事项流程实例。
- 故事模板节点选项来自已发布专题模板；发布校验拒绝不存在、未发布、已删除或类型不匹配的 `sourceTopicNodeKey`。
- 专题模板中的项目挂载节点也必须按专题实例绑定的模板版本解析，不能使用全局默认专题模板覆盖已有专题。

- [ ] **Step 1: RED**：覆盖新旧 JSON、类型互斥、非法 node key、草稿不生效、修改默认模板不影响旧实例、项目/专题绑定版本不漂移，以及“挂载点从节点 C 改为节点 E 后，新建专题只出现在 E、旧专题仍留在 C”。
- [ ] **Step 2: Run RED**：运行 `mvn -Dtest=WorkflowTemplateServiceNodeBindingTest,WorkflowTemplateDefinitionValidatorTest test` 及前端 workflow model 测试。
- [ ] **Step 3: Implement**：补充定义模型、校验、节点选项、版本化解析和创建时快照；修正专题/故事创建和改绑服务的调用链。
- [ ] **Step 4: GREEN**：运行后端定向测试、前端定向测试、`pnpm typecheck` 和 `git diff --check`。
- [ ] **Step 5: Self-review**：逐条验证“当前默认模板只影响新建事项”“历史事项不漂移”“专题组件挂载点可配置且不依赖固定节点文案”“故事只能挂专题节点”；通过后进入 Task 2。

### Task 2: 建立项目、专题、故事共用的 UI 渲染系统

**Files:**

- Modify: `../pms-front/src/styles/index.css`
- Modify: `../pms-front/src/styles/pms-theme.css`
- Create: `../pms-front/src/components/workflow/WorkflowNodeShell.vue`
- Create: `../pms-front/src/components/workflow/WorkflowRuntimeComponentHost.vue`
- Create: `../pms-front/src/components/workflow/workflow-component-registry.ts`
- Modify: `../pms-front/src/views/project/detail/index.vue`
- Modify: `../pms-front/src/views/project/detail/workflow-config.mjs`
- Modify: `../pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Modify: `../pms-front/src/views/development/detail/components/DevelopmentItemFlow.vue`
- Modify: `../pms-front/src/views/development/detail/components/DevelopmentItemWorkflowFields.vue`
- Modify: `../pms-front/src/types/domain.ts`
- Modify: `../pms-front/src/views/admin/workflows/index.vue`
- Create: `../pms-front/src/components/workflow/workflow-renderer.visual.test.mjs`

**Interfaces:**

- 详情页不强制复用同一个页面组件；项目、专题、故事保留各自业务布局，但统一使用 `pms-theme.css` 的背景、surface、border、radius、spacing、status color 等 token，以及共享节点壳、字段区和运行时组件 host。
- `runtimeComponents: string[]` 是后端与前端之间唯一的运行时组件协议；模板不能传 HTML、CSS 或任意组件路径。组件 key 与挂载节点 key 分离：组件注册表负责“渲染什么”，模板快照负责“挂到哪个节点”。
- 项目详情的既有工作流映射、专题/故事详情的动态节点渲染和流程模板预览全部接入同一注册表；未知 key 显示统一降级状态。
- 项目详情按专题实例的 `projectMountNodeKey + sourceNodeId` 将专题组件放入对应项目节点；如果旧专题仍挂在 C、新专题挂在 E，两个节点都必须能显示各自专题，不能因当前默认模板变化而移动旧专题。
- 节点数量、节点字段、负责人/排期是否为空只影响内容，不改变页面背景、卡片层级、操作位置和保存逻辑。

- [ ] **Step 1: RED**：写渲染协议和视觉契约测试，覆盖项目/专题/故事三类事项、挂载点从 C 切换到 E、旧专题 C 与新专题 E 并存、不同节点数量、空字段、空任务、未知组件 key、桌面/窄屏尺寸。
- [ ] **Step 2: Run RED**：运行 `pnpm test -- workflow-renderer.visual.test.mjs` 和 `pnpm typecheck`，确认当前存在多套渲染入口或 token 不一致。
- [ ] **Step 3: Implement**：提取共享节点壳、字段区、运行时组件 host 和注册表；将项目详情与专题/故事详情逐步接入，不改变业务数据接口。
- [ ] **Step 4: GREEN**：运行前端测试、类型检查和构建；使用浏览器检查项目/专题/故事详情的背景、卡片、节点、任务看板和空状态，并验证项目节点 C/E 的动态挂载位置。
- [ ] **Step 5: Self-review**：确认模板无法改变 CSS/HTML，确认专题组件不会绑定固定的“开发与迭代控制”节点，确认新增字段或组件不会复制出第二套详情布局；通过后进入 Task 3。

### Task 3: 在专题具体节点挂载“故事拆分”，并让故事属于该节点

**Files:**

- Modify: `src/main/java/com/brad/pms/workflow/WorkflowComponentKey.java`
- Modify: `src/main/java/com/brad/pms/service/WorkflowComponentBindingService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentStoryManagementService.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentItemWorkflowNodeDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentStoryListDTO.java`
- Create: `src/test/java/com/brad/pms/service/StoryWorkflowComponentBindingServiceTest.java`
- Create: `src/test/java/com/brad/pms/service/TopicStoryNodeAssociationTest.java`
- Modify: `../pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Create: `../pms-front/src/views/development/detail/components/DevelopmentStorySplitComponent.vue`
- Modify: `../pms-front/src/views/development/development-item-detail-visual.test.mjs`
- Modify: `../pms-front/src/types/domain.ts`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`

**Interfaces:**

- `WorkflowComponentKey.STORY_SPLIT = "story-split"`，只在专题流程实例的 `storyMountNodeKey` 对应节点上生效，不写回模板节点定义。
- `applyStoryBinding(topicWorkflowSnapshot)` 根据专题实例快照而不是当前默认故事模板计算组件；同一节点去重并保留原内容顺序。
- 创建故事时只提交 `topicId`；后端根据专题的挂载快照自动写入 `topicWorkflowNodeId`。创建独立故事时两个专题字段为空。
- `DevelopmentStoryListDTO` 返回专题节点名称；故事组件只列出绑定当前专题且未删除的故事，并提供详情入口。空列表保留统一空状态。
- 故事任务/子任务仍只归属于故事自身流程节点，不复制到专题节点；专题节点只负责故事拆分入口和故事摘要。

- [ ] **Step 1: RED**：覆盖无故事、独立故事、绑定故事、多个故事、删除故事、挂载点非当前节点、重复组件 key 和模板版本变更。
- [ ] **Step 2: Run RED**：运行 `mvn -Dtest=StoryWorkflowComponentBindingServiceTest,TopicStoryNodeAssociationTest test` 及前端详情定向测试。
- [ ] **Step 3: Implement**：增加故事组件 key、服务端节点 overlay、故事节点归属写入和 DTO；实现专题详情组件及统一空状态。
- [ ] **Step 4: GREEN**：运行后端定向测试、前端定向测试、`pnpm typecheck`，浏览器验证专题 D 节点和故事详情。
- [ ] **Step 5: Self-review**：确认故事不会出现在专题的其他节点、独立故事不会进入专题组件、模板更新不会改写历史故事流程；通过后进入 Task 4。

### Task 4: 闭环验证关联变更、权限和成员引用

**Files:**

- Modify: `src/main/java/com/brad/pms/service/DevelopmentTopicManagementService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentStoryManagementService.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectMemberAssignmentService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Create: `src/test/java/com/brad/pms/service/DevelopmentTopicStoryLifecycleMatrixTest.java`
- Create: `src/test/java/com/brad/pms/service/DevelopmentTopicStoryPermissionMatrixTest.java`
- Create: `src/test/java/com/brad/pms/service/DevelopmentTopicStoryRollbackIntegrationTest.java`
- Modify: `src/test/java/com/brad/pms/service/ProjectMemberAssignmentServiceTest.java`

**Interfaces:**

- 专题：独立 → 项目、项目 A → 项目 B、项目 → 独立；改绑只更新项目上下文，保留专题流程节点和故事挂载快照。
- 故事：独立 → 专题、专题 A → 专题 B、专题 → 独立；改绑后必须根据新专题快照更新 `topicWorkflowNodeId`，旧专题不再显示该故事。
- 项目必须处于进行中且当前用户有权限；专题软删除、故事软删除和非法父级关系必须阻止新关联。
- 负责人、节点负责人、任务/子任务执行人产生的成员引用在绑定、改绑、解绑、删除后保持可审计、可回滚、无误回收。

- [ ] **Step 1: RED**：先写关系状态矩阵、权限矩阵和异常回滚测试。
- [ ] **Step 2: Run RED**：运行定向服务测试，确认现有实现遗漏的上下文、节点归属、ACL 或 member ref。
- [ ] **Step 3: Implement**：只修复测试暴露的问题，所有关联和成员变更放在同一事务边界内。
- [ ] **Step 4: GREEN**：启动 Docker/Testcontainers 后运行集成测试；Docker 不可用时明确记录，不用 mock 结果冒充集成通过。
- [ ] **Step 5: Self-review**：检查数据库快照、历史账号、项目 ACL、故事列表、专题节点组件和解绑后的历史展示；确认没有复制任务或删除流程历史。通过后进入 Task 5。

### Task 5: 统一列表/详情文案并更新使用手册

**Files:**

- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentTopicListDTO.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemService.java`
- Modify: `../pms-front/src/views/development/DevelopmentListPage.vue`
- Modify: `../pms-front/src/views/development/DevelopmentTopicEditModal.vue`
- Modify: `../pms-front/src/views/development/DevelopmentStoryEditModal.vue`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`
- Modify: `docs/business-specification.md`
- Modify: `../pms-front/docs/user-manual.md`
- Modify: `../pms-front/src/views/manual/reference.ts`

**Interfaces:**

- 专题明确显示“未关联项目”或项目及项目节点；故事明确显示“未关联专题”或专题及专题节点，不再用空白或“未设置”表达业务关系。
- 故事编辑只允许选择/清空专题；专题编辑项目可选，项目候选只包含进行中的、当前用户可读的项目。
- 文档关系图必须明确项目节点挂专题、专题节点挂故事拆分、故事任务归属故事节点；流程模板章节必须写明版本快照和 UI 统一规则。
- 文档不描述本轮尚未实现的故事拆分层级编辑、故事依赖图或全局故事看板。

- [ ] **Step 1: RED**：写 DTO/read model 和文档核对清单，覆盖四种关系状态、权限筛选、已删除父级和无效链接。
- [ ] **Step 2: Implement**：统一列表、详情摘要、编辑表单、使用手册和业务规则。
- [ ] **Step 3: GREEN**：运行 `mvn test`、`mvn -DskipTests package`、`pnpm test`、`pnpm typecheck`、`pnpm build`、`git diff --check`。
- [ ] **Step 4: Self-review**：按“数据模型 → 模板版本 → 运行时组件 → 详情 UI → 关联生命周期 → 文档”顺序复核，生成最终变更清单和已知限制。
- [ ] **Step 5: 只有全部验证通过后才提交**：继续沿用当前工作区和当前分支，按阶段提交，提交前再次确认没有混入无关修改。

## 暂不纳入本轮

- 故事拆分的完整编辑、拆分层级、故事之间依赖关系。
- 全局项目任务看板、通知策略、消息中心和跨项目故事汇总。
- 改写已经发布的历史流程版本；历史数据只做安全、可审计、可回滚的兼容回填。
