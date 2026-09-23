# Workflow Process Type Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** 将流程模板配置页按可扩展的流程类型筛选，并把专题/故事流程类型与普通项目创建选项隔离。

**Architecture:** 复用现有 \`pms_project_type\` 和 \`projectTypeId\` 绑定关系，新增 \`project_creation_enabled\` 标识区分“流程模板分类”和“可用于新建项目的类型”。后台流程模板接口读取全部启用类型，项目创建选项和服务端创建校验只允许标记为可创建项目的类型。前端只调整流程模板页的语义文案，继续使用现有类型选择器作为模板筛选器。

**Tech Stack:** Spring Boot 3.5、MyBatis-Plus、Flyway、JUnit 5/Mockito、Vue 3、TypeScript、Ant Design Vue、Node test runner。

**Spec:** \`docs/superpowers/specs/2026-09-23-workflow-process-type-filter-design.md\`

## Global Constraints

- 不重命名 \`pms_project_type\`、\`projectTypeId\` 或已有外键。
- \`project_creation_enabled = 1\` 才能出现在新建项目选项中；历史数据默认按可创建项目兼容处理。
- \`general\` 保持稳定编码，继续作为未指定类型时的回退类型。
- 启动初始化必须幂等创建 \`topic-management\` 和 \`story-management\`，且新建流程类型默认不可用于新建项目。
- 已有项目的 \`project_type_id\`、流程版本和节点数据不迁移、不改写。
- 本次不执行提交、推送、合并、重置或清理；只修改本计划涉及的文件。
- 每个任务完成后必须通过该任务的测试、\`git diff --check\` 和规格对照自审，才能进入下一任务。

## Review Focus

- 旧数据库行的 \`project_creation_enabled\` 为空或迁移前不存在时，项目创建不能被意外禁用；由任务 1 的兼容字段和任务 2 的默认解析测试覆盖。
- 专题/故事类型必须能在管理端配置模板，但不能通过项目创建接口绕过前端过滤；由任务 2 的服务测试覆盖。
- 初始化逻辑重复运行不能重复创建流程类型，也不能覆盖管理员对非默认类型的模板配置；由任务 1 的初始化测试/差异检查覆盖。
- 新增流程类型后管理页必须自动切换到该类型，且模板查询仍带当前类型 ID；由任务 3 的页面源码测试覆盖。
- “流程类型”和“项目类型”的文案不能串到项目新建弹窗；由任务 3 的中英文 locale 和页面引用测试覆盖。

---

### Task 1: 扩展类型数据模型并幂等初始化三类流程类型

**Files:**
- Create: src/main/resources/db/migration/V50__workflow_process_type_scope.sql
- Modify: src/main/java/com/brad/pms/entity/ProjectTypeDO.java
- Modify: src/main/java/com/brad/pms/dto/response/ProjectTypeDTO.java
- Modify: src/main/java/com/brad/pms/config/WorkflowTemplateSeedRunner.java
- Test: src/test/java/com/brad/pms/config/WorkflowTemplateSeedRunnerTest.java

**Interfaces:**
- Consumes: existing ProjectTypeMapper, WorkflowTemplateMapper, ProjectMapper and general seed behavior.
- Produces: ProjectTypeDO.projectCreationEnabled, ProjectTypeDTO.projectCreationEnabled, and active type rows general, topic-management, story-management.

- [x] **Step 1: Write the failing migration/seed tests**

Create WorkflowTemplateSeedRunnerTest with these behaviors:

~~~
@Test
void createsTopicAndStoryProcessTypesAsTemplateOnlyTypes() {
    // Stub projectTypeMapper.selectOne(...) to return null for all codes,
    // then run the runner and capture inserted ProjectTypeDO values.
    assertThat(insertedTypes).extracting(ProjectTypeDO::getCode)
            .contains("general", "topic-management", "story-management");
    assertThat(insertedTypes.stream()
            .filter(type -> !"general".equals(type.getCode()))
            .map(ProjectTypeDO::getProjectCreationEnabled))
            .containsOnly(false);
}

@Test
void preservesExistingNonGeneralTypesWhenInitializationRunsAgain() {
    // Return existing topic-management and story-management rows from selectOne(...).
    // Verify no insert is issued for those rows and their metadata is not overwritten.
    verify(projectTypeMapper, never()).insert(argThat(type ->
            "topic-management".equals(type.getCode()) || "story-management".equals(type.getCode())));
}
~~~

Use Mockito captors/stubs against the existing runner dependencies; do not connect to a real database for this unit test. The test must initially fail because the entity has no new field and the runner has no two new seed paths.

- [x] **Step 2: Run the focused test and verify the expected RED failure**

Run:

~~~
mvn -q -Dtest=WorkflowTemplateSeedRunnerTest test
~~~

Expected: FAIL because the new process types and projectCreationEnabled field do not exist yet. Fix test setup errors until the failure is specifically about the missing behavior.

- [x] **Step 3: Add the Flyway column and Java model field**

Create V50__workflow_process_type_scope.sql:

~~~
ALTER TABLE pms_project_type
    ADD COLUMN project_creation_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER status;
~~~

Add private Boolean projectCreationEnabled to ProjectTypeDO and ProjectTypeDTO.

- [x] **Step 4: Implement idempotent seed metadata**

Refactor WorkflowTemplateSeedRunner into a small ensureProjectType(code, name, description, sort, projectCreationEnabled) helper:

- Ensure general exists with code general, default project creation enabled, and fresh-install display name 项目管理.
- If an existing general row still has the old seed name 通用项目, update only that seed-owned name/description; preserve a deliberate custom name.
- Ensure topic-management and story-management rows exist with names 专题管理 and 故事管理, status 1, stable sort values after general, and project creation disabled.
- Never create template versions for the two new types.
- Keep current-process bound to general and keep the missing-project workflow backfill unchanged.

- [x] **Step 5: Run the focused test and verify GREEN**

Run:

~~~
mvn -q -Dtest=WorkflowTemplateSeedRunnerTest test
~~~

Expected: PASS with zero failures.

- [x] **Step 6: Stage 1 self-review before continuing**

Run:

~~~
git diff --check -- src/main/resources/db/migration/V50__workflow_process_type_scope.sql src/main/java/com/brad/pms/entity/ProjectTypeDO.java src/main/java/com/brad/pms/dto/response/ProjectTypeDTO.java src/main/java/com/brad/pms/config/WorkflowTemplateSeedRunner.java src/test/java/com/brad/pms/config/WorkflowTemplateSeedRunnerTest.java
git diff -- src/main/resources/db/migration/V50__workflow_process_type_scope.sql src/main/java/com/brad/pms/entity/ProjectTypeDO.java src/main/java/com/brad/pms/dto/response/ProjectTypeDTO.java src/main/java/com/brad/pms/config/WorkflowTemplateSeedRunner.java src/test/java/com/brad/pms/config/WorkflowTemplateSeedRunnerTest.java
~~~

Review the diff against spec sections 4 and 7. Confirm no existing project rows or workflow definition JSON are rewritten. Record the review result in the SDD ledger before Task 2.

---

### Task 2: 隔离项目创建选项并增加服务端校验

**Files:**
- Modify: src/main/java/com/brad/pms/dto/request/ProjectTypeSaveCmd.java
- Modify: src/main/java/com/brad/pms/service/WorkflowTemplateService.java
- Modify: src/test/java/com/brad/pms/service/WorkflowTemplateServiceSelectionTest.java
- Modify: src/test/java/com/brad/pms/dto/request/ProjectTypeSaveCmdTest.java if validation wording requires an assertion update

**Interfaces:**
- Consumes: Task 1 ProjectTypeDO.projectCreationEnabled and ProjectTypeDTO.projectCreationEnabled.
- Produces: options() returns only project-creation-enabled types/templates; resolveForProjectCreation(...) rejects template-only types; admin template APIs still see all active process types.

- [x] **Step 1: Write the failing service tests**

Extend WorkflowTemplateServiceSelectionTest with these behaviors:

~~~
@Test
void projectCreationOptionsExcludeTemplateOnlyProcessTypes() {
    ProjectTypeDO project = type(3L, 21L);
    project.setProjectCreationEnabled(true);
    ProjectTypeDO topic = type(4L, null);
    topic.setCode("topic-management");
    topic.setProjectCreationEnabled(false);
    when(projectTypeMapper.selectList(any())).thenReturn(List.of(project, topic));
    when(templateMapper.selectList(any())).thenReturn(List.of(template(8L, 3L), template(9L, 4L)));
    when(versionMapper.selectList(any())).thenReturn(List.of());

    WorkflowTemplateOptionsDTO options = service.options();

    assertThat(options.getProjectTypes()).extracting(ProjectTypeDTO::getCode)
            .containsExactly("general");
    assertThat(options.getTemplates()).extracting(WorkflowTemplateSummaryDTO::getProjectTypeId)
            .containsOnly(3L);
}

@Test
void projectCreationRejectsTemplateOnlyProcessTypeEvenWhenPassedDirectly() {
    ProjectTypeDO topic = type(4L, null);
    topic.setCode("topic-management");
    topic.setProjectCreationEnabled(false);
    when(projectTypeMapper.selectById(4L)).thenReturn(topic);

    assertThatThrownBy(() -> service.resolveForProjectCreation(4L, null))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不可用于新建项目");
}

@Test
void nullCreationFlagRemainsBackwardCompatibleAsEnabled() {
    ProjectTypeDO legacy = type(3L, 21L);
    legacy.setProjectCreationEnabled(null);
    when(projectTypeMapper.selectOne(any())).thenReturn(legacy);
    when(versionMapper.selectById(21L)).thenReturn(version(21L, 8L, "PUBLISHED"));
    when(templateMapper.selectById(8L)).thenReturn(template(8L, 3L));

    assertThat(service.resolveForProjectCreation(null, null).projectType().getId()).isEqualTo(3L);
}
~~~

Update the existing test helper to set projectCreationEnabled explicitly for ordinary project types. The tests must fail before the service changes because options() currently returns all types and direct resolution accepts every active type.

- [x] **Step 2: Run the focused test and verify RED**

Run:

~~~
mvn -q -Dtest=WorkflowTemplateServiceSelectionTest test
~~~

Expected: the new exclusion and rejection assertions fail for the current implementation.

- [x] **Step 3: Implement project-creation filtering and validation**

In WorkflowTemplateService:

- Keep listProjectTypes() as the admin list of all active types.
- Add listProjectCreationTypes() filtering status = 1 and projectCreationEnabled != false.
- Make options() use listProjectCreationTypes() and filter its published templates to those type IDs.
- Make createProjectType(ProjectTypeSaveCmd) set projectCreationEnabled = false for newly created process types. Keep the request payload backward-compatible; do not expose a new frontend checkbox in this task.
- Add requireProjectCreationType(Long) that delegates active-type validation and rejects Boolean.FALSE with the message 该流程类型不可用于新建项目.
- Use that guard from resolveForProjectCreation, including the general fallback path.
- Treat a null flag as enabled for legacy rows.
- Include the flag in toProjectTypeDTO.

- [x] **Step 4: Run focused tests and the existing workflow selection tests**

Run:

~~~
mvn -q -Dtest=WorkflowTemplateServiceSelectionTest,ProjectTypeSaveCmdTest test
~~~

Expected: PASS with zero failures.

- [x] **Step 5: Stage 2 self-review before continuing**

Run:

~~~
git diff --check -- src/main/java/com/brad/pms/dto/request/ProjectTypeSaveCmd.java src/main/java/com/brad/pms/service/WorkflowTemplateService.java src/test/java/com/brad/pms/service/WorkflowTemplateServiceSelectionTest.java src/test/java/com/brad/pms/dto/request/ProjectTypeSaveCmdTest.java
git diff -- src/main/java/com/brad/pms/service/WorkflowTemplateService.java src/test/java/com/brad/pms/service/WorkflowTemplateServiceSelectionTest.java
~~~

Review that admin template listing still returns all active process types while project creation options are filtered, and that the service—not only the browser—blocks a direct topic/story project creation request. Record the result in the ledger.

---

### Task 3: 更新流程模板页语义文案并锁定前端筛选契约

**Files:**
- Modify: src/views/admin/workflows/index.vue
- Modify: src/locales/zh-CN.ts
- Modify: src/locales/en-US.ts
- Modify: src/types/workflow.ts
- Create: src/views/admin/workflows/workflow-process-type.test.mjs

**Interfaces:**
- Consumes: Task 2 API behavior; admin endpoint still returns all active process types and the existing projectTypeId query remains the filter key.
- Produces: visible “流程类型/Process type” labels, “新增流程类型/Add process type” action, and a typed optional projectCreationEnabled field for API compatibility.

- [x] **Step 1: Write the failing frontend contract test**

Create workflow-process-type.test.mjs that reads the real Vue source and locale files and asserts:

~~~
test('workflow admin labels the selector as a process-type filter', () => {
  assert.match(source, /\$t\('admin\.workflow\.processTypes'\)/)
  assert.match(source, /\$t\('admin\.workflow\.addProcessType'\)/)
  assert.match(zhLocale, /processTypes:\s*'流程类型'/)
  assert.match(zhLocale, /addProcessType:\s*'新增流程类型'/)
  assert.match(zhLocale, /description:\s*'按流程类型维护流程模板。/)
  assert.match(enLocale, /processTypes:\s*'Process types'/)
  assert.match(enLocale, /addProcessType:\s*'Add process type'/)
})

test('project creation copy remains project-type-specific', () => {
  assert.match(projectLocale, /projectType:\s*'项目类型'/)
  assert.match(projectLocale, /projectType:\s*'Project type'/)
})
~~~

The test must initially fail because the page and locale files use the old project-type keys.

- [x] **Step 2: Run the focused frontend test and verify RED**

Run:

~~~
node --test src/views/admin/workflows/workflow-process-type.test.mjs
~~~

Expected: FAIL on the missing process-type labels.

- [x] **Step 3: Update the admin workflow page and locale keys**

Change only the admin workflow terminology:

- Use admin.workflow.processTypes for the selector label and accessible name.
- Use admin.workflow.addProcessType for the add button, empty state, and modal title.
- Change the description to “按流程类型维护流程模板。拖动节点调整顺序，编辑节点组件和字段；发布后项目将绑定固定版本。” and the matching English copy.
- Change the type form labels and errors to process-type wording while keeping project.projectType unchanged.
- Keep changeProjectType, listWorkflowTemplates(typeId), and projectTypeId API payloads unchanged; the selector remains the filter and the compatibility field stays internal.
- Add projectCreationEnabled?: boolean to ProjectType in src/types/workflow.ts.

- [x] **Step 4: Run the focused test and frontend typecheck**

Run:

~~~
node --test src/views/admin/workflows/workflow-process-type.test.mjs
pnpm typecheck
~~~

Expected: both commands exit 0 with zero test failures and no TypeScript errors.

- [x] **Step 5: Stage 3 self-review before continuing**

Run:

~~~
git diff --check -- src/views/admin/workflows/index.vue src/locales/zh-CN.ts src/locales/en-US.ts src/types/workflow.ts src/views/admin/workflows/workflow-process-type.test.mjs
git diff -- src/views/admin/workflows/index.vue src/locales/zh-CN.ts src/locales/en-US.ts src/types/workflow.ts src/views/admin/workflows/workflow-process-type.test.mjs
~~~

Check that only the admin workflow page uses “流程类型”; project creation still says “项目类型”. Record the result in the ledger.

---

### Task 4: 全量验证、页面联调和最终自审

**Files:**
- Modify only if verification reveals a scoped defect in the files from Tasks 1–3.
- Test: src/test/java/com/brad/pms/service/WorkflowTemplateServiceSelectionTest.java, src/test/java/com/brad/pms/config/WorkflowTemplateSeedRunnerTest.java, src/views/admin/workflows/workflow-process-type.test.mjs

**Interfaces:**
- Consumes: all outputs from Tasks 1–3.
- Produces: verified backend compilation/tests, frontend tests/build, and a browser-level confirmation of the three process-type filter choices.

- [x] **Step 1: Run the backend full test suite**

Run:

~~~
mvn -q test
~~~

Expected: exit 0. If unrelated pre-existing tests fail, record the exact test names and failure output without changing unrelated code.

- [x] **Step 2: Run the frontend full test suite and production build**

Run:

~~~
pnpm test
pnpm build
~~~

Expected: both exit 0 with zero test failures and a successful Vite build.

- [ ] **Step 3: Verify the running app through the existing local browser**

Open http://127.0.0.1:5173/admin/workflows and confirm:

1. The selector label is 流程类型.
2. It contains 项目管理、专题管理、故事管理 after the backend reloads/initializes.
3. Switching types changes the template request scope and does not show another type’s templates.
4. The project creation dialog still labels its selector 项目类型 and does not include 专题管理/故事管理.

Use browser accessibility state or the visible page; do not mutate data beyond creating no template. If the backend must restart to apply Flyway/seed changes, restart only the existing local process and record the result.

验收记录：后端已在持久前台会话中启动并成功应用 V50；刷新页面可达登录页，但现有本地管理员凭据返回 401，未擅自重置密码，因此第 3 项的登录后浏览器验收保留为待补验。对应边界已由后端服务测试、前端契约测试和数据库只读检查覆盖。

- [x] **Step 4: Final self-review against the spec and worktree scope**

Run:

~~~
git diff --check
git status --short
git diff --stat -- src/main/resources/db/migration/V50__workflow_process_type_scope.sql src/main/java/com/brad/pms/entity/ProjectTypeDO.java src/main/java/com/brad/pms/dto/response/ProjectTypeDTO.java src/main/java/com/brad/pms/config/WorkflowTemplateSeedRunner.java src/main/java/com/brad/pms/service/WorkflowTemplateService.java src/views/admin/workflows/index.vue src/locales/zh-CN.ts src/locales/en-US.ts src/types/workflow.ts
~~~

Review every requirement in the spec sections 4–7, ensure unrelated pre-existing changes remain untouched, and record any known limitation. Do not commit, push, merge, or claim completion until the fresh verification output has been read.

## Execution Notes

- The current workspace is intentionally used because both repositories contain pre-existing uncommitted work needed to run the existing app; no worktree creation or branch switch is performed.
- Keep a ledger at .superpowers/sdd/2026-09-23-workflow-process-type-filter/progress.md, beginning with the plan path. Each task entry must include RED evidence, GREEN evidence, the diff-review command, and a short ruling if implementation differs from the plan.
