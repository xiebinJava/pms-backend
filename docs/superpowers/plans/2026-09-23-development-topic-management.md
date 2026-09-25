# 专题管理改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让专题列表、专题维护和流程模板按统一规则工作：可编辑/改绑/软删除/恢复；专题流程模板选择项目节点后，系统自动在该节点呈现“开发与迭代控制”，无需项目流程模板预先添加组件。

**Architecture:** 在专题流程模板定义 JSON 中保存 `sourceProjectNodeKey`，以项目模板节点的稳定 key 解析每个项目上的真实 `project_node`。使用运行时组件绑定解析器按“专题管理 → 项目流程节点 → `development-control`”派生有效节点定义；只影响节点详情、权限/能力校验和看板等运行时视图，不回写不可变的已发布项目流程版本。现有项目只要有匹配节点也会自动获得该组件；模板改绑后，仍有历史专题的旧节点继续显示工作台，但新增入口仅留在当前配置节点。后端负责项目状态、权限、节点组件、聚合迁移和软删除的一致性；前端只提交专题基本信息及目标项目，不提交目标节点。专题软删除只标记专题聚合根，关联故事和所有独立流程/任务保留并随专题隐藏或恢复。架构为未来“故事管理 → 专题流程节点 → `story-breakdown`”预留同一扩展点，但本次不实现故事拆分组件、故事模板绑定或节点注入。

**Tech Stack:** Java 21、Spring Boot、MyBatis-Plus、Flyway、JUnit 5/Mockito；Vue 3、TypeScript、Ant Design Vue、Node.js `node:test`、pnpm。

**Spec:** `docs/superpowers/specs/2026-09-23-development-topic-management-design.md`

**Repository roots:** Backend commands run from `/Users/fs/Desktop/Project/pms-backend`; frontend commands run from `/Users/fs/Desktop/Project/pms-front`. Backend file paths below are relative to the backend root; frontend file paths are absolute.

## Global Constraints

- 只允许进行中项目接收新建或改绑专题；目标项目必须有专题模板指定的项目节点，但不要求项目流程模板预先承载 `development-control`。节点候选项汇总当前可用于新建项目的已发布项目流程版本、仍被进行中项目引用的历史流程版本，并始终纳入内置兼容流程（支持旧项目/无版本项目），按稳定 key 去重。
- 专题绑定自动派生 `development-control`，所有读取/校验组件定义的主要路径必须使用同一有效定义：节点详情与完成校验、节点组件权限/定位、企业项目看板。不得改写项目已发布模板 JSON，也不得只在前端伪造组件。
- 模板绑定节点变化不迁移已有专题；历史专题所在节点即使不再是当前新建节点，仍保留工作台组件用于查看和管理存量专题，但不得在该节点创建新专题。当前绑定节点即使其项目模板没有显式组件，也必须正常显示和工作。
- 故事流程绑定到专题节点并派生 `story-breakdown` 属于后续扩展。本次只让绑定机制有清晰的可扩展边界，不增加故事模板配置字段或未实现组件。
- 专题流程模板版本存储 `sourceProjectNodeKey`；旧版本缺少该字段时回退到项目节点 key `develop`（“开发测试与项目控制”）。`development-control` 是组件 key，不是节点 key。
- 更换专题模板配置不迁移已有专题；专题改绑时才按当前发布默认专题模板的项目节点设置重新定位。
- 故事继承所属专题的项目和项目节点；改绑必须连同专题、故事、已存在事项流程的 `project_id/source_node_id` 在单一事务更新。
- 改绑清空旧项目的专题里程碑及故事迭代计划引用；不得删除或重建专题/故事流程节点、字段值、任务、子任务或任务执行人。
- 删除只设置专题软删除标记；普通列表、故事列表、详情和项目节点工作台隐藏已删除专题及其故事；恢复专题后原故事和流程数据重新可见。
- 专题操作为详情、编辑、删除；已删除范围提供恢复操作。故事不新增独立编辑/删除操作。
- 恢复不要求原项目仍处于进行中，已删除范围需能读取已完成/已终止/已删除项目下的专题上下文；仍检查当前用户对原项目/专题的管理权限，不能因项目状态变化而丢失恢复入口。
- 保留工作区中已有修改；不得重置、清理、暂存或提交与本计划无关的文件。
- 按阶段实施。每阶段结束先检查阶段差异和测试证据，再开始下一阶段。

## Review Focus

1. 旧模板 JSON 不含 `sourceProjectNodeKey` 时，专题创建/改绑仍使用项目节点 key `develop`，且故事模板不误读专题设置（Task 1、Task 2 测试）。
2. 节点候选项合并所有当前可用于新建项目的已发布流程版本、进行中项目仍引用的历史流程版本和内置兼容流程，不按模板是否预置组件过滤；只要活跃项目有匹配稳定 `nodeKey` 即可绑定，绑定后有效定义自动含 `development-control`（Task 1–3 测试）。内置兼容流程始终可选，保证旧版默认 key `develop` 在配置界面有对应候选。
3. 运行时组件注入覆盖 v1 的 `components` 和 v2 的 `contentOrder`，在现有项目和新项目的匹配节点均可见；节点权限、完成校验和企业项目看板对组件的判断结果一致，且不会改写原项目模板版本 JSON（Task 3 测试）。
4. 更换专题模板绑定节点不改变已有专题；有历史专题的旧节点保留管理组件，新建入口只出现在当前绑定节点（Task 3 测试）。故事 `story-breakdown` 仅记录为后续扩展，不在本次实现（范围复核）。
5. 模板切换不改变已有专题；仅项目 ID 变化时，专题、故事、流程来源节点全部原子更新，旧里程碑/迭代清空，工作流任务及版本不变（Task 2 测试）。
6. 专题被软删除后，所有列表/详情/工作台均隐藏其故事，但历史流程任务仍存在；恢复后数据原样可见（Task 2、Task 5 测试）。
7. 项目关闭/终止/删除、权限丢失、并发更新或事务中途失败不会造成部分迁移/删除，也不能误恢复其他专题（Task 2 测试）。
8. 专题/故事详情继续遵循通用流程详情规范：未完成节点的负责人、排期和已配置字段可编辑，变更后点击外部自动保存，不显示冗余的“流程节点 N”标签（Task 6 只读页面复核及前端全量测试）。

---

### Task 1: 后端专题模板节点绑定配置

**Files:**
- Modify: `src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinition.java`
- Modify: `src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java`
- Modify: `src/main/java/com/brad/pms/service/WorkflowTemplateService.java`
- Modify: `src/main/java/com/brad/pms/controller/AdminWorkflowConfigController.java`
- Modify: `src/main/java/com/brad/pms/mapper/ProjectMapper.java`
- Create: `src/main/java/com/brad/pms/dto/response/WorkflowProjectNodeOptionDTO.java`
- Test: `src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java`
- Create: `src/test/java/com/brad/pms/service/WorkflowTemplateServiceNodeBindingTest.java`
- Create: `src/test/java/com/brad/pms/controller/AdminWorkflowConfigControllerTest.java`

**Interfaces:**
- Produces: `WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes, String sourceProjectNodeKey)` and a backward-compatible two-argument constructor; `WorkflowTemplateService.listTopicSourceNodeOptions()` returns `List<WorkflowProjectNodeOptionDTO>` where each option is `{key,name}`.
- Produces: `GET /admin/workflow-config/project-node-options`, permission `WORKFLOW_READ`, returning the stable-key-deduplicated union of all nodes in every currently selectable published project workflow version, archived versions still pinned by active projects, and the built-in compatibility definition (always included for legacy/no-version projects); candidate inclusion is independent of component configuration. Duplicate visible names include the stable key as a disambiguator.
- Consumes: existing versioned `definition_json`, current project-creation template options, active-project template-version references, `ProjectMapper`, `WorkflowNodeDefinition.runtimeComponents()` and the `topic-management` process type code.

- [ ] **Step 1: Write failing compatibility and candidate tests.** In `WorkflowTemplateDefinitionValidatorTest`, assert that a template definition JSON containing `sourceProjectNodeKey` round-trips through the configured `ObjectMapper`, while legacy JSON remains readable. Add `WorkflowTemplateServiceNodeBindingTest` cases for valid/invalid keys, union/deduplication across multiple project types, archived versions referenced by active projects, and unconditional inclusion of the built-in compatibility definition. Include nodes both with and without `development-control` and assert both are candidates. Use reflection-based controller contract assertions with literal route `/admin/workflow-config/project-node-options` and permission `WORKFLOW_READ`; these fail by test assertion before production code exists, without importing uncreated controller classes.
- [ ] **Step 2: Run the targeted tests and observe the expected failures.** Run: `mvn -Dtest=WorkflowTemplateDefinitionValidatorTest,WorkflowTemplateServiceNodeBindingTest,AdminWorkflowConfigControllerTest test`. Expected: the new tests fail because the record field, candidate method, and endpoint behavior are not implemented.
- [ ] **Step 3: Add the optional root field and compatibility constructor.** Extend the Java record with `String sourceProjectNodeKey`; retain `WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes)` delegating to a null key so every existing test and seed constructor remains source-compatible.

```java
public record WorkflowTemplateDefinition(
        int schemaVersion,
        List<WorkflowNodeDefinition> nodes,
        String sourceProjectNodeKey) {
    public WorkflowTemplateDefinition(int schemaVersion, List<WorkflowNodeDefinition> nodes) {
        this(schemaVersion, nodes, null);
    }
}
```

- [ ] **Step 4: Implement template-specific validation and candidate lookup.** In `WorkflowTemplateService`, merge nodes from every published workflow version offered for current project creation, every archived version pinned by an active project, and the built-in compatibility definition unconditionally. Include all nodes regardless of their existing component list, deduplicate by stable key, and disambiguate duplicate names with the key. Validate a nonblank configured key against this union when the saved template belongs to the topic process type. Skip unconfigured project types; keep null as the legacy fallback.

```java
definition.nodes().stream()
        .map(node -> new WorkflowProjectNodeOptionDTO(node.key(), node.name()))
```
- [ ] **Step 5: Add the read endpoint and legacy default.** Add `GET /admin/workflow-config/project-node-options` guarded by `WORKFLOW_READ`; make `resolveTopicSourceProjectNodeKey` return project node key `develop` (the compatibility workflow’s “开发测试与项目控制” node) when the selected topic template has no configured key. Do not confuse it with component key `development-control`; do not auto-create or publish a topic workflow template.
- [ ] **Step 6: Re-run backend tests and review the phase.** Run the targeted command from Step 2 and inspect `git diff --check` plus the complete Task 1 diff. Expected: all named tests pass, old definition constructors compile, the selected key serializes in the versioned JSON, and candidates do not depend on a manually attached component.

### Task 2: 专题编辑、改绑、软删除及恢复领域服务

**Files:**
- Create: `src/main/resources/db/migration/V53__development_topic_soft_delete.sql`
- Modify: `src/main/java/com/brad/pms/entity/ProjectNodeDevelopmentTopicDO.java`
- Create: `src/main/java/com/brad/pms/controller/DevelopmentTopicController.java`
- Create: `src/main/java/com/brad/pms/dto/request/DevelopmentTopicUpdateCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/DevelopmentTopicProjectQry.java`
- Create: `src/main/java/com/brad/pms/dto/response/DevelopmentTopicProjectOptionDTO.java`
- Create: `src/main/java/com/brad/pms/service/DevelopmentTopicManagementService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemService.java`
- Modify: `src/main/java/com/brad/pms/dto/request/DevelopmentItemPageQry.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify: `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`
- Modify: `src/main/java/com/brad/pms/audit/AuditAction.java`
- Create: `src/test/java/com/brad/pms/service/DevelopmentTopicManagementServiceTest.java`
- Test: `src/test/java/com/brad/pms/service/DevelopmentItemServiceTest.java`
- Test: `src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceNodeEditTest.java`
- Test: `src/test/java/com/brad/pms/service/NodeDevelopmentControlServiceTest.java`
- Test: `src/test/java/com/brad/pms/config/FlywayMigrationVersionTest.java`

**Interfaces:**
- Produces: `PUT /development/topics/{id}` with `{title,ownerId,projectId}`; `DELETE /development/topics/{id}` soft-deletes; `POST /development/topics/{id}/restore` restores; `POST /development/topics/projects/page` accepts `{currPage,pageSize,keyword}` and returns ongoing, accessible project options with the configured compatible node. Topic page requests gain optional `deleted`; omitted/false means active records, true means only the deleted scope.
- Produces: `DevelopmentTopicManagementService.update(Long id, DevelopmentTopicUpdateCmd cmd)`, `.softDelete(Long id)`, `.restore(Long id)`, and `.projectOptions(DevelopmentTopicProjectQry qry)`.
- Consumes: Task 1’s `sourceProjectNodeKey` resolver; existing project read/manage permissions; existing item workflow, topic/story and node mappers.

- [ ] **Step 1: Write failing topic-management API contract tests against literal HTTP paths.** Begin with reflection-based route/permission contract assertions using `/development/topics/{id}`, `/development/topics/{id}/restore`, and `/development/topics/projects/page` so the tests fail by assertion without compiling references to not-yet-created controllers. Add the matching `MockMvc` request/response tests once the controller shell exists, and write focused service tests for same-project edits preserving the old node, aggregate project/node movement, clearing project-local links, active-project/matching-node validation (without requiring a stored component), preserving owners/assignees and workflows, deleted-topic visibility, and restore behavior.
- [ ] **Step 2: Run the focused test and migration checks.** Run: `mvn -Dtest=DevelopmentTopicManagementServiceTest,DevelopmentItemServiceTest,DevelopmentItemWorkflowServiceNodeEditTest,NodeDevelopmentControlServiceTest,FlywayMigrationVersionTest test`. Expected: new lifecycle tests fail before the service/schema changes.
- [ ] **Step 3: Add the topic deleted marker migration.** Create V53 adding `deleted BOOLEAN NOT NULL DEFAULT FALSE` to `project_node_development_topic`; add a plain Boolean entity field (not MyBatis `@TableLogic`, because the same table must support both active and deleted scopes). Reuse the existing `ProjectNodeDevelopmentTopicMapper.selectByIdForUpdate` to lock the aggregate before edits.

```sql
ALTER TABLE project_node_development_topic
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER test_status;
```

- [ ] **Step 4: Implement project/node resolution and options.** Resolve the topic template’s effective source key (`develop` for legacy/no default), filter options to projects with normalized `ProjectStatus.ACTIVE`, current-user manage permission, and an actual project node with matching key. Do not require a preconfigured `development-control` component; Task 3 supplies it through the runtime binding overlay. Re-check project status, permission, and matching node inside update transactions.
- [ ] **Step 5: Implement atomic update and restore/delete methods.** Update only title/owner when project ID is unchanged. On project change, lock topic, stories, workflows and tasks in a consistent order; resolve and validate the target project/node; update the topic, every child story and each existing workflow’s `projectId/sourceNodeId`; clear `milestoneId` and `iterationPlanId`; preserve workflow version/node/task data and every existing assignment. Any failed write rolls back the transaction. When later editing a moved item, accept an assignment only if unchanged from the stored assignment or a member of the target project; never add a project member implicitly. Soft-delete/restore only toggles the topic marker and records audit events. Restore checks management authority against the original project without requiring that project to remain operational.

- [ ] **Step 6: Hide soft-deleted aggregate members everywhere.** Default topic pages filter `deleted=false`; deleted-topic scope filters `deleted=true` and loads original project display data including soft-deleted projects, while retaining the caller’s project-management data scope. Story pages and item workflow detail reject/hide stories whose parent topic is deleted; project development-control reads and writes ignore deleted topics. When an entire active topic is omitted from workbench replacement, skip physical deletion of its child stories/workflows and soft-delete the topic aggregate; deleted rows must not be silently reintroduced by a later workbench save. Preserve existing physical-removal behavior for a story individually removed from a still-active topic.
- [ ] **Step 7: Add controller routes, repeat tests, and self-review this phase.** Guard list/options with `PROJECT_READ`; require manage permission on the source for edits/deletes and on both source and target for a rebind; require source-project manage permission for restore without conditioning it on project operational status. Re-run the command from Step 2 and inspect the complete Task 2 diff, migration ordering, audit scope, and transaction behavior before continuing. Expected: all tests pass and no endpoint accepts a client-supplied node ID.

### Task 3: 运行时自动派生专题工作台组件并限制创建入口

**Files:**
- Create: `src/main/java/com/brad/pms/service/WorkflowComponentBindingService.java`
- Test: `src/test/java/com/brad/pms/service/WorkflowComponentBindingServiceTest.java`
- Modify: `src/main/java/com/brad/pms/service/NodeService.java`
- Test: `src/test/java/com/brad/pms/service/NodeServiceCompleteTest.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectPermissionService.java`
- Test: `src/test/java/com/brad/pms/service/ProjectPermissionServiceTest.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectBoardService.java`
- Test: `src/test/java/com/brad/pms/service/ProjectBoardServiceTest.java`
- Modify: `src/main/java/com/brad/pms/dto/response/NodeDevelopmentControlDTO.java`
- Modify: `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`
- Test: `src/test/java/com/brad/pms/service/NodeDevelopmentControlServiceTest.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/node-development-control.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/DevelopmentControlWorkbench.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/development-control.test.mjs`

**Interfaces:**
- Produces: a single effective-definition resolver that overlays `development-control` for (a) the configured stable node key and (b) historical project nodes that still contain topic records; it supports v1 `components` and v2 `contentOrder` without mutating stored project workflow JSON.
- Produces: `NodeDevelopmentControlDTO.topicCreationAllowed` calculated from the current project node key and effective published topic template configuration. Historical nodes keep the component for existing topics but cannot create new topics unless they match the current binding.
- Consumes: Task 1 source-node resolver, Task 2 soft-delete visibility rules, and the project’s pinned workflow definition/node instance.

- [ ] **Step 1: Write failing resolver and consumer tests.** Assert the selected host node gets `development-control` when absent from its stored definition, existing active-topic nodes retain it after the binding moves, unrelated nodes do not; assert v1/v2 representations render the same effective component without duplicates. Add consumer tests proving node DTO/completion validation, `requireNodeComponent`/`findNodeWithComponent`, and project-board aggregation all see the same effective component. Verify historical nodes cannot add new topics, but their existing topics remain editable; the configured node can create new topics even when its project template omitted the component.
- [ ] **Step 2: Run the focused tests and confirm expected failures.** Run: `mvn -Dtest=WorkflowComponentBindingServiceTest,NodeServiceCompleteTest,ProjectPermissionServiceTest,ProjectBoardServiceTest,NodeDevelopmentControlServiceTest test`; in frontend run `node --test src/views/project/detail/development-control.test.mjs`. Expected: effective component resolver or consumer behavior assertions fail before implementation.
- [ ] **Step 3: Implement a non-persisted effective component overlay.** Resolve the effective published topic binding and merge `development-control` into the matching project node definition. Preserve existing schema semantics: append to v1 `components`, and add `component:development-control` to v2 `contentOrder` only if missing. Also retain the overlay on historical host nodes containing active or soft-deleted topics so template rebinding never strands existing items. Batch historical topic-node lookups per project/request (or memoize within the request) to avoid per-node N+1 queries. Never update `definition_json`, create a new project template version, or rewrite a project node just to attach the derived component.

```text
effectiveProjectNode(projectId, nodeKey)
  = pinnedWorkflowNode(projectId, nodeKey)
  + topicBindingOverlay(currentBoundNodeKey, historicalTopicNodeKeys)
```

- [ ] **Step 4: Route every component-sensitive project consumer through the resolver.** Use the effective definition for project-node response/rendering and required-component completion checks in `NodeService`, component authorization/discovery in `ProjectPermissionService`, and bulk component discovery in `ProjectBoardService`. Keep development-item (topic/story) workflow definitions isolated from the project-host overlay.
- [ ] **Step 5: Expose and enforce topic creation eligibility.** Populate the DTO flag and validate only newly inserted topic rows against the configured node key in `NodeDevelopmentControlService.save`; retain updates for existing topics at historical nodes. The runtime component may be present on historical nodes, but `topicCreationAllowed` remains false there.

```java
dto.setTopicCreationAllowed(Objects.equals(node.getNodeKey(), sourceProjectNodeKey));
```
- [ ] **Step 6: Gate the workbench UI.** Add `topicCreationAllowed` to the frontend type and hide the add-topic controls when false; show the configured-node hint on historical/non-selected workbenches that remain visible to manage existing topics.
- [ ] **Step 7: Re-run and self-review the phase before continuing.** Re-run both targeted commands and review the complete Task 3 diff for v1/v2 JSON safety, project templates with no preconfigured component, historical topics, soft-deleted records, board counts, and component authorization. Expected: all tests pass, persisted workflow template JSON is unchanged, and no project workflow status is changed.

### Task 4: 流程模板管理页配置项目节点

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/workflow.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/admin-workflow.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-model.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-model.d.mts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-template-model.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/workflows/workflow-admin-visual.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/zh-CN.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/en-US.ts`

**Interfaces:**
- Consumes: `GET /admin/workflow-config/project-node-options` from Task 1.
- Produces: Topic workflow draft JSON includes `sourceProjectNodeKey`; project/story workflow editor behavior stays unchanged.

- [ ] **Step 1: Write failing model/UI tests.** Assert the topic-only selector persists the key through draft normalization/save payload, receives only backend candidates, defaults legacy topics to project node key `develop`, and does not expose/set this option for story workflow templates. Keep the tests executable against the current model and make them fail by assertion before changing the model/UI.
- [ ] **Step 2: Run the tests before implementation.** Run: `node --test src/views/admin/workflows/workflow-template-model.test.mjs src/views/admin/workflows/workflow-admin-visual.test.mjs`. Expected: selector/model assertions fail before the API field and UI are added.
- [ ] **Step 3: Extend front-end types and API.** Add optional `sourceProjectNodeKey` to both root definition shapes (v1 and v2) and `getWorkflowProjectNodeOptions(): Promise<{key:string;name:string}[]>` to the admin workflow API.

```ts
interface WorkflowTemplateDefinitionV2 {
  schemaVersion: 2
  sourceProjectNodeKey?: string
  nodes: WorkflowNodeDefinitionV2[]
}
```
- [ ] **Step 4: Add a topic-only selector.** In the template editor’s type/template settings area, render the project-node selector only for process type `topic-management`; initialize legacy/empty topic definition to project node key `develop`, preserve the selected stable key, and explain that publishing this binding automatically adds the “开发与迭代控制” workspace to matching project nodes. Do not ask the administrator to add a second component in the project workflow editor.
- [ ] **Step 5: Verify and self-review this phase.** Re-run the command from Step 2, then `pnpm typecheck` in `pms-front`; inspect visual tests, both locale dictionaries, and the complete Task 4 diff before continuing. Expected: selected key survives load/edit/save/reload, the automatic component behavior is clearly explained, and other process types are visually unchanged.

### Task 5: 专题/故事列表精简及专题编辑、删除、恢复

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/development-item.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/development/DevelopmentListPage.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/development/DevelopmentTopicEditModal.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/development/development-list.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/zh-CN.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/en-US.ts`

**Interfaces:**
- Consumes: Task 2’s page `deleted` scope, topic update/delete/restore routes and paged compatible-project options.
- Produces: Normal topic operation actions `详情、编辑、删除`; deleted topic action `恢复`; story operation remains detail-only.

- [ ] **Step 1: Write failing list and API contract tests.** Assert both list modes remove title icons/IDs, context subtitle, workflow status subtitle, and progress subtitle; topic columns omit build/test but keep story count; topics expose detail/edit/delete and restore only in deleted scope; stories remain detail-only. Use source/column-model assertions in the existing `node:test` file so the initial run is a normal assertion failure.
- [ ] **Step 2: Run the tests and observe failures.** Run: `node --test src/views/development/development-list.test.mjs`. Expected: current template still renders the removed labels/columns and has no topic actions.
- [ ] **Step 3: Add typed API functions.** Add `deleted?: boolean`, topic update/delete/restore, and paged project-options functions to `development-item.ts`; use exact backend paths and payload fields from Task 2.

```ts
export function updateDevelopmentTopic(id: number, payload: { title: string; ownerId?: number; projectId: number })
export function deleteDevelopmentTopic(id: number): Promise<void>
export function restoreDevelopmentTopic(id: number): Promise<void>
```
- [ ] **Step 4: Refactor the shared list by mode.** Remove the approved secondary display fields; omit the topic build column; add detail/edit/delete actions for normal topics while preserving story detail action. Add a normal/deleted topic scope control and a restore action for deleted topics.
- [ ] **Step 5: Add the topic edit dialog.** Create a modal for title, topic owner, and associated project. Load/search only Task 2 project options; do not show a node selector. Keep the row’s existing project label selected even if a later template change makes that project ineligible for a new binding; only changed project IDs must come from eligible options. If the project changes, explain that the configured topic-template node will be used and project-local milestone/iteration links will be cleared.
- [ ] **Step 6: Confirm deletion and restore.** Before delete, show topic title and current story count, describing that stories and workflow/task history are hidden rather than erased. Call restore from the deleted view and refresh pagination after each mutation; when deletion empties a page, move to the previous page.
- [ ] **Step 7: Re-run tests and self-review.** Run `node --test src/views/development/development-list.test.mjs` and `pnpm typecheck`; review translations, keyboard dismissal, loading states, stale project-option results, and action permissions. Expected: tests pass and no list action sends a node ID.

### Task 6: 整体验证与阶段总结

**Files:**
- No new product files unless a failing regression test requires a focused fix.

**Interfaces:**
- Consumes: Tasks 1–5 implementation and the approved spec.
- Produces: verified backend/frontend results and a self-review of the combined diff.

- [ ] **Step 1: Run the focused backend suite.** Run: `mvn -Dtest=WorkflowTemplateDefinitionValidatorTest,WorkflowTemplateServiceNodeBindingTest,AdminWorkflowConfigControllerTest,WorkflowComponentBindingServiceTest,NodeServiceCompleteTest,ProjectPermissionServiceTest,ProjectBoardServiceTest,DevelopmentTopicManagementServiceTest,DevelopmentItemServiceTest,DevelopmentItemWorkflowServiceNodeEditTest,NodeDevelopmentControlServiceTest,FlywayMigrationVersionTest test`. Expected: exit 0; record any environment-only Testcontainers failures by exact test name rather than claiming a pass.
- [ ] **Step 2: Run the full frontend unit suite and production build.** In `pms-front`, run `pnpm test` then `pnpm build`. Expected: Node tests pass, Vue type-check passes, and Vite build exits 0.
- [ ] **Step 3: Verify the workflow in an isolated test fixture.** Confirm the topic template selector and eligible-project filtering; verify the shared workflow layout, editable node fields, and click-outside autosave using a disposable test record or UI test fixture; create/rebind a test topic, assert story/workflow/task preservation, soft-delete and restore it, and verify unsupported targets are rejected. Do not mutate the user’s existing topic/project records for this check.
- [ ] **Step 4: Perform the final self-review.** Compare the full diff against every spec acceptance criterion; inspect `git diff --check`, migration ordering, permissions, transaction boundaries, locale parity, and verify that runtime component injection never rewrites published project workflow JSON. Confirm story `story-breakdown` remains explicitly deferred, and ensure unrelated pre-existing changes remain untouched. Report any unverified UI environment or pre-existing suite failures explicitly.
