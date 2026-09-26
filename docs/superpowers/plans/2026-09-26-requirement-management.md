# Requirement Management and Single Execution Target Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an independent requirement management work item with its own versioned workflow, a shared project/topic/story detail UI, and a server-enforced rule that each requirement has zero or one current execution target: project, topic, or story.

**Architecture:** Extend the existing generic development-item workflow instead of creating a second workflow engine. Add a requirement aggregate with nullable polymorphic execution-target columns plus append-only history, then expose a requirement-specific runtime component whose placement is controlled by the requirement workflow template. Reuse the existing shared list/detail, node, field, autosave, task-board, component-registry, permission, and audit patterns.

**Tech Stack:** Spring Boot, MyBatis-Plus, Flyway, MySQL, JUnit 5, Testcontainers, Vue 3, TypeScript, Ant Design Vue, Vite, pnpm, existing PMS workflow renderer and i18n system.

**Spec:** docs/superpowers/specs/2026-09-26-requirement-management-design.md

## Global Constraints

- A requirement has zero or one current execution target; the active target type is exactly PROJECT, TOPIC, or STORY.
- Requirement-to-target is a traceability relation, not a parent-child relation; it must not duplicate the relation onto all downstream topics or stories.
- A project can be a new requirement target only when ProjectStatus.isOpen returns true; completed, terminated, and deleted projects are rejected.
- Topic and story targets must be readable and not soft-deleted; their existing project/topic context is not changed by a requirement association.
- When a requirement has a workflow instance, it pins a published template version; draft/default changes cannot rewrite existing requirements. An explicitly unconfigured requirement has no workflow instance until an administrator supplies a valid published/default version.
- The requirement execution component is selected by template configuration and registered component key, never by a hard-coded node name, node ID, or arbitrary HTML.
- Project, topic, story, and requirement detail pages share the existing PMS background, cards, spacing, state colors, node shell, field renderer, autosave, and task board.
- Basic requirement create/edit APIs must not accept execution-target fields; target changes go through the transactional execution-target service.
- The first release links existing project/topic/story records; create-and-link flows are explicitly deferred and, if added later, must reuse the existing creation services.
- Requirement creation accepts an optional published requirement template version; when omitted it resolves the current default, and when neither exists it creates an explicitly unconfigured requirement that cannot advance nodes until an administrator configures a template.
- Historical user IDs and target history are retained; inactive users can be displayed historically but cannot be newly assigned.
- Database writes for target association, unassociation, replacement, history, and audit are one transaction with optimistic/concurrent conflict handling.
- Work in the existing workspace on the current release branch; do not create a worktree or change unrelated release metadata.
- Every implementation task ends with its own tests, self-review, and commit; later tasks start only after the previous gate passes.

## Review Focus

- **Concurrent target selection:** two users link different targets at the same time; exactly one current target survives and the losing request receives a conflict. Pin this in RequirementExecutionTargetServiceTest.
- **Stale target option:** a project is active when searched but becomes completed/deleted before submit; submit revalidates and rejects without changing the requirement. Pin this in RequirementExecutionTargetServiceTest.
- **Unconfigured or changed workflow template:** a requirement can be created without a default workflow, while an existing requirement stays on its pinned version after a new default is published. Pin this in RequirementWorkflowServiceTest and workflow-admin frontend tests.
- **Historical inactive user:** an inactive owner, node owner, or task assignee remains visible in existing data but is not offered for a new assignment. Pin this in RequirementHistoryAccountTest and the shared detail visual test.
- **Unknown or misplaced runtime component:** an unknown key or a component placed on a node that is not part of the published definition is rejected or rendered as a safe fallback, never arbitrary markup. Pin this in WorkflowComponentBindingServiceTest and requirement-execution visual tests.

---

## Current Baseline and File Map

The current backend already has a generic DevelopmentItemType containing TOPIC and STORY, a generic pms_development_item_workflow snapshot, reusable workflow nodes/tasks, configurable process types in pms_project_type, and a WorkflowComponentBindingService. The current frontend has a shared DevelopmentListPage, DevelopmentItemDetailPage, workflow renderer, runtime component registry, and a workflow template editor. The plan extends these seams rather than introducing parallel infrastructure.

### Backend files

| File | Responsibility in this feature |
| --- | --- |
| src/main/resources/db/migration/V56__requirement_management.sql | Add requirement and target-history tables; preserve the nullable generic workflow project context introduced by V54; add indexes and safe constraints. |
| src/main/java/com/brad/pms/entity/RequirementDO.java | Map pms_requirement, including current target pair and optimistic version. |
| src/main/java/com/brad/pms/entity/RequirementExecutionTargetHistoryDO.java | Map append-only target history. |
| src/main/java/com/brad/pms/mapper/RequirementMapper.java | Requirement CRUD, locked load, scoped page queries, and reverse target lookup. |
| src/main/java/com/brad/pms/mapper/RequirementExecutionTargetHistoryMapper.java | Insert and query target history. |
| src/main/java/com/brad/pms/dto/response/SourceRequirementSummaryDTO.java | Common direct-source read model used by project/topic/story detail and list responses. |
| src/main/java/com/brad/pms/dto/request/RequirementPageQry.java | Requirement list filters: keyword, workflow status, target type, owner, deleted. |
| src/main/java/com/brad/pms/dto/request/RequirementSaveCmd.java | Basic requirement fields only; deliberately excludes target fields. |
| src/main/java/com/brad/pms/dto/request/RequirementExecutionTargetCmd.java | Target type, target ID, requirement version, and optional reason. |
| src/main/java/com/brad/pms/dto/request/RequirementExecutionTargetOptionQry.java | Target type, keyword, page, and page size. |
| src/main/java/com/brad/pms/dto/response/RequirementListDTO.java | List row with workflow summary and one target summary. |
| src/main/java/com/brad/pms/dto/response/RequirementExecutionTargetDTO.java | Target type, target ID, title, status, owner, progress, and navigation metadata. |
| src/main/java/com/brad/pms/dto/response/RequirementExecutionTargetHistoryDTO.java | Read model for link/unlink/replace history. |
| src/main/java/com/brad/pms/dto/response/RequirementExecutionTargetOptionDTO.java | Polymorphic search option with common display fields. |
| src/main/java/com/brad/pms/common/enums/RequirementExecutionTargetType.java | Closed enum PROJECT, TOPIC, STORY. |
| src/main/java/com/brad/pms/workflow/DevelopmentItemType.java | Add REQUIREMENT with process type requirement-management. |
| src/main/java/com/brad/pms/workflow/WorkflowComponentKey.java | Add requirement-execution registry key. |
| src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java | Validate component keys and process-type-compatible placement. |
| src/main/java/com/brad/pms/config/WorkflowTemplateSeedRunner.java | Ensure the requirement-management process type exists and is not project-creatable. |
| src/main/java/com/brad/pms/service/WorkflowTemplateService.java | Include requirement templates/options and enforce requirement-specific definition rules. |
| src/main/java/com/brad/pms/dto/response/DevelopmentWorkflowTemplateOptionsDTO.java | Return topic, story, and requirement template collections. |
| src/main/java/com/brad/pms/service/RequirementManagementService.java | Requirement CRUD, scoped list, workflow binding, detail read model, and source-target summary. |
| src/main/java/com/brad/pms/service/RequirementExecutionTargetService.java | Transactional link, unlink, replace, option search, target validation, history, and audit. |
| src/main/java/com/brad/pms/service/RequirementExecutionTargetReadService.java | Read-only direct-source lookup for project/topic/story detail and list responses; has no dependency on project business services. |
| src/main/java/com/brad/pms/service/DevelopmentItemService.java | Add requirement page/read model and target summary batching. |
| src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java | Allow requirement workflows with no project/source node and load requirement context. |
| src/main/java/com/brad/pms/dto/response/DevelopmentItemWorkflowDetailDTO.java | Expose the direct source requirement on topic/story detail and the current target on requirement detail. |
| src/main/java/com/brad/pms/dto/response/DevelopmentTopicListDTO.java | Expose the direct source requirement summary for topic rows. |
| src/main/java/com/brad/pms/dto/response/DevelopmentStoryListDTO.java | Expose the direct source requirement summary for story rows. |
| src/main/java/com/brad/pms/dto/response/ProjectDTO.java | Expose the direct source requirement summary for project detail. |
| src/main/java/com/brad/pms/service/ProjectService.java | Batch-load direct source requirement summary without creating a second relationship table. |
| src/main/java/com/brad/pms/controller/DevelopmentRequirementController.java | Requirement page, basic CRUD, restore, target APIs, and target history endpoints. |
| src/main/java/com/brad/pms/controller/DevelopmentItemWorkflowController.java | Add requirement detail and allow type=requirement for generic node/task endpoints. |
| src/main/java/com/brad/pms/security/PermissionCode.java | Add requirement:read, requirement:write, and requirement:manage. |
| src/main/java/com/brad/pms/config/EnterpriseDataMigration.java | Seed requirement permissions and preserve existing built-in role compatibility. |
| src/main/java/com/brad/pms/audit/AuditAction.java | Add requirement and execution-target audit actions. |
| src/main/java/com/brad/pms/audit/AuditResourceType.java | Add REQUIREMENT resource type. |

### Backend tests

| File | Responsibility |
| --- | --- |
| src/test/java/com/brad/pms/migration/RequirementManagementMigrationTest.java | Assert V56 tables, nullable workflow context, target pair constraints, and indexes. |
| src/test/java/com/brad/pms/service/RequirementManagementServiceTest.java | CRUD, list scope, soft delete/restore, default template snapshot, and detail summary. |
| src/test/java/com/brad/pms/service/RequirementExecutionTargetServiceTest.java | One-target invariant, all target types, project status validation, rebind, unlink, conflict, and rollback. |
| src/test/java/com/brad/pms/service/RequirementExecutionTargetReadServiceTest.java | Direct-source lookup, batch lookup, and no-descendant-propagation behavior. |
| src/test/java/com/brad/pms/service/RequirementWorkflowServiceTest.java | Generic workflow creation, node/field/task operations, missing default, and pinned version behavior. |
| src/test/java/com/brad/pms/service/RequirementHistoryAccountTest.java | Historical account display and active-user assignment rules. |
| src/test/java/com/brad/pms/service/WorkflowComponentBindingServiceTest.java | Extend component compatibility and unknown-key behavior. |
| src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java | Extend process-type and runtime-component validation cases. |
| src/test/java/com/brad/pms/controller/DevelopmentRequirementPermissionTest.java | Verify read/write/manage annotations and endpoint separation. |

### Frontend files

| File | Responsibility in this feature |
| --- | --- |
| src/types/domain.ts | Extend DevelopmentItemType and shared detail/target DTO types. |
| src/types/workflow.ts | Represent requirement process type and runtime component metadata. |
| src/api/development-item.ts | Requirement list/detail/CRUD/workflow/target API contracts. |
| src/api/admin-workflow.ts | Include requirement template options if the existing API typing needs a named field. |
| src/router/index.ts | Add /development/requirements and /development/requirements/:id routes. |
| src/layout/Index.vue | Add 需求管理 under 研发管理 and active route handling. |
| src/views/development/requirements/index.vue | Thin wrapper selecting mode=requirements. |
| src/views/development/DevelopmentListPage.vue | Add requirement mode, columns, filters, create/edit/delete actions, and target navigation. |
| src/views/development/DevelopmentRequirementEditModal.vue | Edit requirement basic fields and choose the requirement template at creation. |
| src/views/development/detail/DevelopmentItemDetailPage.vue | Render requirement detail with the same project/topic/story layout and source-target summary. |
| src/views/development/detail/RequirementExecutionComponent.vue | Render the configured execution target component and its link/unlink/change flows. |
| src/components/workflow/workflow-component-registry.ts | Register requirement-execution and its process-type scope. |
| src/components/workflow/WorkflowRuntimeComponentHost.vue | Mount requirement-execution using the common host protocol. |
| src/views/admin/workflows/workflow-template-model.mjs | Normalize requirement definitions and prevent topic/story mount fields from leaking into requirement templates. |
| src/views/admin/workflows/workflow-template-schema.mjs | Add requirement component/process-type schema constraints. |
| src/views/admin/workflows/index.vue | Show requirement templates and filter the component palette by requirement-management. |
| src/locales/zh-CN.ts | Requirement navigation, list/detail, target, validation, and workflow copy. |
| src/locales/en-US.ts | English parity for all new copy. |
| src/views/development/development-list.test.mjs | Extend shared list contract for requirement mode. |
| src/views/development/requirement-management.test.mjs | Route, list, modal, and target action contracts. |
| src/views/development/requirement-execution.visual.test.mjs | Shared detail UI and component behavior contracts. |
| src/views/admin/workflows/workflow-admin-visual.test.mjs | Requirement process type and component palette coverage. |
| src/views/manual/index.vue and src/locales/zh-CN.ts | Add user-manual entry for requirement workflow and target rule. |
| src/views/manual/BusinessRules.vue and src/locales/zh-CN.ts | Add business rules for one-target association, invalid project states, and rebind history. |

## Implementation Tasks

### Task 1: Add the requirement data model and migration

**Files:**
- Create: src/main/resources/db/migration/V56__requirement_management.sql
- Create: src/main/java/com/brad/pms/entity/RequirementDO.java
- Create: src/main/java/com/brad/pms/entity/RequirementExecutionTargetHistoryDO.java
- Create: src/main/java/com/brad/pms/mapper/RequirementMapper.java
- Create: src/main/java/com/brad/pms/mapper/RequirementExecutionTargetHistoryMapper.java
- Create: src/main/java/com/brad/pms/common/enums/RequirementExecutionTargetType.java
- Modify: src/main/java/com/brad/pms/controller/HealthController.java
- Test: src/test/java/com/brad/pms/migration/RequirementManagementMigrationTest.java

**Interfaces:**
- Produces RequirementExecutionTargetType.PROJECT, TOPIC, STORY.
- Produces RequirementDO with executionTargetType, executionTargetId, version, and soft-delete fields.
- Produces a locked RequirementMapper.selectByIdForUpdate(Long id) and target reverse lookup methods.
- Produces V56 schema that allows only one current target by storing the pair on pms_requirement.

- [ ] **Step 1: Write the failing migration and enum tests.** Assert the migration contains pms_requirement and pms_requirement_execution_target_history with the target pair check and indexes; assert the existing V54 schema still exposes nullable project_id/source_node_id on pms_development_item_workflow; assert all three enum values are accepted and an unknown value is rejected.
- [ ] **Step 2: Run the focused tests to verify the new schema/model is absent.** Run:

~~~bash
mvn -q -Dtest=RequirementManagementMigrationTest,FlywayMigrationVersionTest test
~~~

Expected: FAIL because V56 and the new model are not present.
- [ ] **Step 3: Implement the migration and mappings.** Add the requirement table with title/description/priority/owner/status/current target/deleted/version/audit columns; add append-only history with a foreign key to pms_requirement; define history target columns as new-target fields for LINK/REPLACE and null for UNLINK, with previous-target fields holding the old target for UNLINK/REPLACE; retain the V54 nullable generic workflow context and all existing foreign keys/indexes; add a type-target reverse index. Do not repeat the V54 column alteration and do not add polymorphic foreign keys to project/topic/story.
- [ ] **Step 4: Run migration and model tests to verify the schema.** Run the focused command again and run the existing migration suite:

~~~bash
mvn -q -Dtest=RequirementManagementMigrationTest,FlywayMigrationVersionTest,DevelopmentControlMigrationTest test
~~~

Expected: PASS with no Flyway version collision and no V51-V55 regression.
- [ ] **Step 5: Self-review the stage.** Verify that target cardinality is enforced by the requirement row, history is append-only, project/topic/story tables are not mutated, the V54 nullable workflow context is preserved for independent items, and existing project/topic/story service paths still retain their required context. Only after this review passes, commit:

~~~bash
git add src/main/resources/db/migration/V56__requirement_management.sql src/main/java/com/brad/pms/entity/RequirementDO.java src/main/java/com/brad/pms/entity/RequirementExecutionTargetHistoryDO.java src/main/java/com/brad/pms/mapper/RequirementMapper.java src/main/java/com/brad/pms/mapper/RequirementExecutionTargetHistoryMapper.java src/main/java/com/brad/pms/common/enums/RequirementExecutionTargetType.java src/main/java/com/brad/pms/controller/HealthController.java src/test/java/com/brad/pms/migration/RequirementManagementMigrationTest.java
git commit -m "feat: add requirement management schema"
~~~

### Task 2: Extend process types, workflow snapshots, and runtime component contracts

**Files:**
- Modify: src/main/java/com/brad/pms/workflow/DevelopmentItemType.java
- Modify: src/main/java/com/brad/pms/workflow/WorkflowComponentKey.java
- Modify: src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java
- Modify: src/main/java/com/brad/pms/config/WorkflowTemplateSeedRunner.java
- Modify: src/main/java/com/brad/pms/service/WorkflowTemplateService.java
- Modify: src/main/java/com/brad/pms/dto/response/DevelopmentWorkflowTemplateOptionsDTO.java
- Modify: src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java
- Test: src/test/java/com/brad/pms/service/RequirementWorkflowServiceTest.java
- Test: src/test/java/com/brad/pms/service/WorkflowComponentBindingServiceTest.java
- Test: src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java

**Interfaces:**
- DevelopmentItemType.REQUIREMENT maps to process type requirement-management.
- WorkflowComponentKey.REQUIREMENT_EXECUTION is requirement-execution.
- WorkflowTemplateService.developmentOptions() returns topicTemplates, storyTemplates, and requirementTemplates.
- DevelopmentItemWorkflowService.createIfDefaultExists(REQUIREMENT, requirementId, null, null) creates a pinned snapshot when a default is configured.
- DevelopmentItemWorkflowService.detail(REQUIREMENT, requirementId) returns the same node/field/task DTO shape as topic and story.

- [ ] **Step 1: Write failing process-type, snapshot, and validator tests.** Cover seed creation of requirement-management with projectCreationEnabled=false; a requirement template with requirement-execution on a valid node; rejection of project/topic mount fields on requirement templates; creation with null project/source node; no-default unconfigured behavior; and old template versions remaining unchanged after publishing a new default.
- [ ] **Step 2: Run focused tests to verify they fail.** Run:

~~~bash
mvn -q -Dtest=RequirementWorkflowServiceTest,WorkflowComponentBindingServiceTest,WorkflowTemplateDefinitionValidatorTest,WorkflowTemplateSeedRunnerTest test
~~~

Expected: FAIL because the requirement type, component key, seed, and null-context branch do not exist.
- [ ] **Step 3: Implement the minimal backend extension.** Add the type and seed; extend development template option aggregation; make template validation process-type aware; add requirement-execution to the allowed registry; update workflow creation/loadContext/detail logic so only REQUIREMENT permits null project/source node; keep TOPIC and STORY validation unchanged; resolve an optional published template version on create, fall back to the default, and preserve the explicit unconfigured state when neither exists.
- [ ] **Step 4: Run focused tests and existing workflow regression.** Run:

~~~bash
mvn -q -Dtest=RequirementWorkflowServiceTest,WorkflowComponentBindingServiceTest,WorkflowTemplateDefinitionValidatorTest,WorkflowTemplateSeedRunnerTest,WorkflowTemplateServiceSelectionTest,DevelopmentItemWorkflowServiceFieldTest,DevelopmentItemWorkflowServiceNodeEditTest test
~~~

Expected: PASS, including existing topic/story mount behavior and template version pinning.
- [ ] **Step 5: Self-review the stage.** Confirm requirement templates cannot accidentally get project/topic mount fields, requirement workflows never need a fake project ID or source node, topic/story still reject null contexts, and the runtime component is only exposed for requirement-management. Commit:

~~~bash
git add src/main/java/com/brad/pms/workflow/DevelopmentItemType.java src/main/java/com/brad/pms/workflow/WorkflowComponentKey.java src/main/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidator.java src/main/java/com/brad/pms/config/WorkflowTemplateSeedRunner.java src/main/java/com/brad/pms/service/WorkflowTemplateService.java src/main/java/com/brad/pms/dto/response/DevelopmentWorkflowTemplateOptionsDTO.java src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java src/test/java/com/brad/pms/service/RequirementWorkflowServiceTest.java src/test/java/com/brad/pms/service/WorkflowComponentBindingServiceTest.java src/test/java/com/brad/pms/workflow/WorkflowTemplateDefinitionValidatorTest.java
git commit -m "feat: extend workflow templates for requirements"
~~~

### Task 3: Implement requirement CRUD and the single execution-target transaction

**Files:**
- Create: src/main/java/com/brad/pms/dto/request/RequirementPageQry.java
- Create: src/main/java/com/brad/pms/dto/request/RequirementSaveCmd.java
- Create: src/main/java/com/brad/pms/dto/request/RequirementExecutionTargetCmd.java
- Create: src/main/java/com/brad/pms/dto/request/RequirementExecutionTargetOptionQry.java
- Create: src/main/java/com/brad/pms/dto/response/RequirementListDTO.java
- Create: src/main/java/com/brad/pms/dto/response/RequirementExecutionTargetDTO.java
- Create: src/main/java/com/brad/pms/dto/response/RequirementExecutionTargetHistoryDTO.java
- Create: src/main/java/com/brad/pms/dto/response/RequirementExecutionTargetOptionDTO.java
- Create: src/main/java/com/brad/pms/dto/response/SourceRequirementSummaryDTO.java
- Create: src/main/java/com/brad/pms/service/RequirementManagementService.java
- Create: src/main/java/com/brad/pms/service/RequirementExecutionTargetService.java
- Create: src/main/java/com/brad/pms/service/RequirementExecutionTargetReadService.java
- Create: src/main/java/com/brad/pms/controller/DevelopmentRequirementController.java
- Modify: src/main/java/com/brad/pms/service/DevelopmentItemService.java
- Modify: src/main/java/com/brad/pms/service/ProjectService.java
- Modify: src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java
- Modify: src/main/java/com/brad/pms/dto/response/DevelopmentItemWorkflowDetailDTO.java
- Modify: src/main/java/com/brad/pms/dto/response/DevelopmentTopicListDTO.java
- Modify: src/main/java/com/brad/pms/dto/response/DevelopmentStoryListDTO.java
- Modify: src/main/java/com/brad/pms/dto/response/ProjectDTO.java
- Modify: src/main/java/com/brad/pms/controller/DevelopmentItemWorkflowController.java
- Modify: src/main/java/com/brad/pms/security/PermissionCode.java
- Modify: src/main/java/com/brad/pms/config/EnterpriseDataMigration.java
- Modify: src/main/java/com/brad/pms/audit/AuditAction.java
- Modify: src/main/java/com/brad/pms/audit/AuditResourceType.java
- Test: src/test/java/com/brad/pms/service/RequirementManagementServiceTest.java
- Test: src/test/java/com/brad/pms/service/RequirementExecutionTargetServiceTest.java
- Test: src/test/java/com/brad/pms/service/RequirementExecutionTargetReadServiceTest.java
- Test: src/test/java/com/brad/pms/service/RequirementHistoryAccountTest.java
- Test: src/test/java/com/brad/pms/controller/DevelopmentRequirementPermissionTest.java

**Interfaces:**
- RequirementManagementService.page(RequirementPageQry qry) returns PageResult<RequirementListDTO>.
- RequirementManagementService.create(RequirementSaveCmd cmd) returns the new requirement ID and binds the selected published requirement template version when supplied.
- RequirementManagementService.update(Long id, RequirementSaveCmd cmd) edits only basic fields and uses the requirement version for optimistic locking.
- RequirementExecutionTargetService.link(Long requirementId, RequirementExecutionTargetCmd cmd) returns RequirementExecutionTargetDTO and rejects an existing target.
- RequirementExecutionTargetService.unlink(Long requirementId, Integer requirementVersion, String reason) clears the active target and appends history.
- RequirementExecutionTargetService.change(Long requirementId, RequirementExecutionTargetCmd cmd) requires a nonblank reason, replaces the current target transactionally, and appends one REPLACE record containing old and new targets.
- RequirementExecutionTargetService.options(Long requirementId, RequirementExecutionTargetOptionQry qry) returns only valid, visible targets.
- RequirementExecutionTargetService.history(Long requirementId) returns append-only history ordered newest first.
- RequirementExecutionTargetReadService.findDirectSourceForTarget(RequirementExecutionTargetType type, Long targetId) returns SourceRequirementSummaryDTO or null without traversing descendants.
- DevelopmentItemService.pageRequirements(RequirementPageQry qry) returns list rows using one batched target summary lookup.

- [ ] **Step 1: Write failing service/controller tests.** Cover create/list/update/delete/restore, no-target rows, all three target types, target option filtering, project statuses ACTIVE/COMPLETED/TERMINATED/DELETED, direct topic/story targets, one-target rejection, unlink, reason-required change, stale version, concurrent lock, failed transaction rollback, historical inactive users, direct-source reverse lookup, and no descendant propagation.
- [ ] **Step 2: Run focused tests to verify they fail.** Run:

~~~bash
mvn -q -Dtest=RequirementManagementServiceTest,RequirementExecutionTargetServiceTest,RequirementExecutionTargetReadServiceTest,RequirementHistoryAccountTest,DevelopmentRequirementPermissionTest test
~~~

Expected: FAIL because the services, controller, permissions, and DTOs are absent.
- [ ] **Step 3: Implement requirement CRUD and target services.** Keep target fields out of RequirementSaveCmd. In link/change/unlink, load the requirement with selectByIdForUpdate, verify version, validate target type through the existing project/topic/story services, update the target pair, write history and audit in one transaction, and return a fresh read model. Use ProjectStatus.isOpen for project targets and preserve inactive user IDs in read DTOs.
- [ ] **Step 4: Add generic workflow endpoints and reverse source summaries.** Extend DevelopmentItemWorkflowController routes for requirement; make DevelopmentItemService batch requirement list summaries without copying topic/story relation logic; populate SourceRequirementSummaryDTO on DevelopmentItemWorkflowDetailDTO, DevelopmentTopicListDTO, DevelopmentStoryListDTO, and ProjectDTO through RequirementExecutionTargetReadService. Keep project:read/project:write checks on existing endpoints while adding requirement permissions to new endpoints. Ensure ProjectService depends only on the read service, while RequirementExecutionTargetService may depend on ProjectService for target validation, so no Spring circular dependency is introduced.
- [ ] **Step 5: Run service, controller, migration, and concurrency tests.** Run:

~~~bash
mvn -q -Dtest=RequirementManagementServiceTest,RequirementExecutionTargetServiceTest,RequirementExecutionTargetReadServiceTest,RequirementHistoryAccountTest,DevelopmentRequirementPermissionTest,RequirementManagementMigrationTest,OptimisticConcurrencyContractTest test
~~~

Expected: PASS; failed target validation leaves both current relation and history unchanged, and concurrent linking leaves one current pair.
- [ ] **Step 6: Self-review the stage.** Inspect every write path to ensure no controller or mapper can write target fields without the target service; verify target type and target ID are always paired; verify rebind never deletes the old target; verify direct topic/story association does not alter their project/topic node fields; verify reverse source summaries are direct-only and do not recursively appear on descendants; verify the read service has no dependency on ProjectService or the target mutation service; verify permission seeds do not silently grant custom roles. Commit:

~~~bash
git add src/main/java/com/brad/pms/dto src/main/java/com/brad/pms/service/RequirementManagementService.java src/main/java/com/brad/pms/service/RequirementExecutionTargetService.java src/main/java/com/brad/pms/service/RequirementExecutionTargetReadService.java src/main/java/com/brad/pms/service/DevelopmentItemService.java src/main/java/com/brad/pms/service/ProjectService.java src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java src/main/java/com/brad/pms/controller/DevelopmentRequirementController.java src/main/java/com/brad/pms/controller/DevelopmentItemWorkflowController.java src/main/java/com/brad/pms/security/PermissionCode.java src/main/java/com/brad/pms/config/EnterpriseDataMigration.java src/main/java/com/brad/pms/audit/AuditAction.java src/main/java/com/brad/pms/audit/AuditResourceType.java src/test/java/com/brad/pms/service/RequirementManagementServiceTest.java src/test/java/com/brad/pms/service/RequirementExecutionTargetServiceTest.java src/test/java/com/brad/pms/service/RequirementExecutionTargetReadServiceTest.java src/test/java/com/brad/pms/service/RequirementHistoryAccountTest.java src/test/java/com/brad/pms/controller/DevelopmentRequirementPermissionTest.java
git commit -m "feat: add requirement execution target lifecycle"
~~~

### Task 4: Add the requirement list, basic form, route, and navigation

**Files:**
- Modify: src/types/domain.ts
- Modify: src/api/development-item.ts
- Modify: src/router/index.ts
- Modify: src/layout/Index.vue
- Create: src/views/development/requirements/index.vue
- Create: src/views/development/DevelopmentRequirementEditModal.vue
- Modify: src/views/development/DevelopmentListPage.vue
- Modify: src/locales/zh-CN.ts
- Modify: src/locales/en-US.ts
- Test: src/views/development/requirement-management.test.mjs
- Test: src/views/development/development-list.test.mjs

**Interfaces:**
- Frontend DevelopmentItemType becomes topic | story | requirement.
- getDevelopmentRequirementPage, createDevelopmentRequirement, updateDevelopmentRequirement, deleteDevelopmentRequirement, and restoreDevelopmentRequirement mirror the existing topic/story API shape.
- DevelopmentRequirementRow exposes title, owner, workflowStatus/progress, executionTarget, and action permissions.
- DevelopmentListPage accepts mode=requirements and renders the same PMS table toolbar, row action pattern, pagination, and empty states.
- The edit modal only submits title, description, owner, priority, and templateVersionId; it never submits executionTargetType or executionTargetId.

- [ ] **Step 1: Write failing frontend contract tests.** Assert the requirement route, navigation item, shared list mode, create button, target-type filter, columns, three actions (detail/edit/delete), unassociated copy, and that the basic form does not contain target fields.
- [ ] **Step 2: Run the focused frontend tests to verify they fail.** Run:

~~~bash
pnpm test -- src/views/development/requirement-management.test.mjs src/views/development/development-list.test.mjs
~~~

Expected: FAIL because the route, mode, API functions, and locale keys are absent.
- [ ] **Step 3: Implement the route and list mode.** Add the 需求管理 navigation under 研发管理; extend active-key and route-title handling; use the existing DevelopmentListPage for the requirement list; add status and execution-target filters; keep table spacing, colors, action order, and refresh behavior aligned with project/topic/story lists.
- [ ] **Step 4: Implement the basic edit modal.** Reuse PersonSelect and the existing workflow-template option pattern; allow a standalone requirement with no target; show the selected requirement template only on create; preserve optimistic errors and disabled states used by topic/story modals.
- [ ] **Step 5: Run tests, typecheck, and build.** Run:

~~~bash
pnpm test -- src/views/development/requirement-management.test.mjs src/views/development/development-list.test.mjs
pnpm typecheck
~~~

Expected: PASS with no missing locale/type errors.
- [ ] **Step 6: Self-review the stage.** Compare the requirement list against the project list at the same viewport: background, table card, toolbar alignment, action column, pagination, empty state, and Chinese/English copy. Confirm a requirement with no target is a valid row, not a broken row. Commit:

~~~bash
git add src/types/domain.ts src/api/development-item.ts src/router/index.ts src/layout/Index.vue src/views/development/requirements/index.vue src/views/development/DevelopmentRequirementEditModal.vue src/views/development/DevelopmentListPage.vue src/locales/zh-CN.ts src/locales/en-US.ts src/views/development/requirement-management.test.mjs src/views/development/development-list.test.mjs
git commit -m "feat: add requirement management list"
~~~

### Task 5: Render requirement detail and the configurable execution component

**Files:**
- Modify: src/views/development/detail/DevelopmentItemDetailPage.vue
- Create: src/views/development/detail/RequirementExecutionComponent.vue
- Modify: src/views/development/detail/components/DevelopmentItemFlow.vue
- Modify: src/views/development/detail/components/DevelopmentItemWorkflowFields.vue
- Modify: src/views/development/detail/components/DevelopmentItemTaskBoard.vue
- Modify: src/components/workflow/workflow-component-registry.ts
- Modify: src/components/workflow/WorkflowRuntimeComponentHost.vue
- Modify: src/views/admin/workflows/workflow-template-model.mjs
- Modify: src/views/admin/workflows/workflow-template-schema.mjs
- Modify: src/views/admin/workflows/index.vue
- Modify: src/types/workflow.ts
- Modify: src/api/admin-workflow.ts
- Modify: src/locales/zh-CN.ts
- Modify: src/locales/en-US.ts
- Test: src/views/development/requirement-execution.visual.test.mjs
- Test: src/views/admin/workflows/workflow-admin-visual.test.mjs

**Interfaces:**
- getDevelopmentItemWorkflow('requirement', id) returns the shared DevelopmentItemWorkflowDetail plus executionTarget and executionTargetHistory.
- getDevelopmentItemWorkflow maps topic, story, and requirement to their own detail paths rather than treating every non-topic type as story.
- linkRequirementExecutionTarget, unlinkRequirementExecutionTarget, changeRequirementExecutionTarget, getRequirementExecutionTargetOptions, and getRequirementExecutionTargetHistory map directly to the backend endpoints.
- RequirementExecutionComponent receives itemId, target, targetHistory, canWrite, and an update callback; it emits a refreshed DevelopmentItemWorkflowDetail after a successful mutation.
- WorkflowRuntimeComponentKey.REQUIREMENT_EXECUTION is visible only when the selected process type is requirement-management.

- [ ] **Step 1: Write failing visual/contract tests.** Assert that requirement detail uses the same root classes and shared node/task components as topic/story; execution component can display empty, linked, unavailable, and history states; target selection enforces one current target; requirement templates expose the component but topic/story templates do not.
- [ ] **Step 2: Run the focused tests to verify they fail.** Run:

~~~bash
pnpm test -- src/views/development/requirement-execution.visual.test.mjs src/views/admin/workflows/workflow-admin-visual.test.mjs
~~~

Expected: FAIL because requirement is not a supported item type and the runtime registry does not contain requirement-execution.
- [ ] **Step 3: Extend the shared detail page.** Add requirement title, list path, status labels, owner/source-target summary, node selection, autosave-on-blur, dynamic fields, task board, and completion behavior through the existing components. Do not fork a requirement-specific page shell or introduce a second color/token set.
- [ ] **Step 4: Implement RequirementExecutionComponent.** Add target-type tabs, debounced search, valid option display, link/clear/change flows, version/conflict handling, target status/progress card, navigation, unavailable-target state, and history drawer. Require a reason for change after the requirement has a target; keep target mutations outside basic node save.
- [ ] **Step 5: Register and configure the component.** Add requirement-execution to the registry with processTypeCodes=[requirement-management]; normalize requirement definitions by removing sourceProjectNodeKey/sourceTopicNodeKey; add admin palette copy and ensure preview and runtime use the same definition protocol.
- [ ] **Step 6: Run tests, typecheck, build, and visual smoke checks.** Run:

~~~bash
pnpm test -- src/views/development/requirement-execution.visual.test.mjs src/views/admin/workflows/workflow-admin-visual.test.mjs src/views/development/development-item-detail-visual.test.mjs
pnpm typecheck
pnpm build
~~~

Expected: PASS; at least one browser smoke check confirms project/topic/story/requirement details use the same background/card/spacing tokens and a configured component appears only on its configured requirement node.
- [ ] **Step 7: Self-review the stage.** Inspect empty, active, completed, unavailable, conflict, narrow viewport, inactive-account, and long-title states. Confirm the component cannot be rendered on a topic/story template, cannot silently replace a target, and cannot change project/topic/story node context. Commit:

~~~bash
git add src/views/development/detail src/components/workflow src/views/admin/workflows src/types/workflow.ts src/api/admin-workflow.ts src/locales/zh-CN.ts src/locales/en-US.ts src/views/development/requirement-execution.visual.test.mjs src/views/admin/workflows/workflow-admin-visual.test.mjs
git commit -m "feat: add requirement workflow detail and execution component"
~~~

### Task 6: Update manual, business rules, and cross-object source summaries

**Files:**
- Modify: src/views/manual/index.vue
- Modify: src/views/manual/BusinessRules.vue
- Modify: src/locales/zh-CN.ts
- Modify: src/locales/en-US.ts
- Modify: src/views/development/detail/DevelopmentItemDetailPage.vue
- Modify: src/views/project/detail/index.vue
- Modify: src/views/development/DevelopmentListPage.vue
- Modify: src/api/project.ts
- Modify: src/api/development-item.ts
- Test: src/views/manual/manual-navigation.test.mjs
- Test: src/views/development/topic-story-section.test.mjs
- Test: src/views/project/detail/development-control.test.mjs

**Interfaces:**
- All three existing detail surfaces may consume a common SourceRequirementSummary DTO without owning the target relationship.
- Manual content explicitly states that a requirement can be unassociated or associated with exactly one project/topic/story.
- Business rules explicitly state that project targets must be active and that rebind history is retained.

- [ ] **Step 1: Write failing documentation and cross-link tests.** Assert navigation exposes requirement workflow guidance, list/detail copy distinguishes no target from invalid target, and project/topic/story summaries show only a direct source requirement.
- [ ] **Step 2: Run focused tests to verify they fail.** Run:

~~~bash
pnpm test -- src/views/manual/manual-navigation.test.mjs src/views/development/topic-story-section.test.mjs src/views/project/detail/development-control.test.mjs
~~~

Expected: FAIL because requirement documentation and source summary contracts are absent.
- [ ] **Step 3: Implement documentation and read-only source summaries.** Add the requirement workflow/template guide, single-target rule, valid-project-state rule, change-path rule, and examples. Reuse existing source-summary styling; do not create a second relationship model in the frontend.
- [ ] **Step 4: Run focused tests and review copy parity.** Run the same command and inspect both zh-CN and en-US keys for no fallback text.
- [ ] **Step 5: Self-review the stage.** Verify docs match the implemented API and UI exactly, direct source requirement is not recursively shown on descendants, and no manual statement suggests that one requirement can create multiple current targets. Commit:

~~~bash
git add src/views/manual src/locales/zh-CN.ts src/locales/en-US.ts src/views/development/detail/DevelopmentItemDetailPage.vue src/views/project/detail/index.vue src/views/development/DevelopmentListPage.vue src/api/project.ts src/api/development-item.ts src/views/manual/manual-navigation.test.mjs src/views/development/topic-story-section.test.mjs src/views/project/detail/development-control.test.mjs
git commit -m "docs: document requirement execution rules"
~~~

### Task 7: Full regression, browser acceptance, and release-branch review

**Files:**
- Modify only files already listed above if verification finds a defect.
- Test: existing backend and frontend suites plus new requirement suites.

**Interfaces:**
- Backend and frontend contracts from Tasks 1-6 are frozen for acceptance.
- No new behavior is introduced during this task unless a failing regression proves a defect in the preceding tasks.

- [ ] **Step 1: Run backend unit and integration tests with Docker/Testcontainers.** Run:

~~~bash
DOCKER_HOST=unix:///Users/fs/.colima/fsclaw/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 mvn -q test
~~~

Expected: the full suite passes with zero failures and zero errors; any skipped test is explained in the review note.
- [ ] **Step 2: Build the backend.** Run:

~~~bash
mvn -q -DskipTests package
~~~

Expected: pms-backend-1.0.8.jar is produced and no compilation warnings hide a missing requirement type.
- [ ] **Step 3: Run the complete frontend verification.** Run:

~~~bash
pnpm test
pnpm typecheck
pnpm build
~~~

Expected: all tests pass, typecheck passes, and the production build succeeds.
- [ ] **Step 4: Perform browser acceptance on the release branch.** Verify:

1. 需求管理 appears under 研发管理.
2. A requirement can be created without a target.
3. A configured requirement workflow shows the same detail background, header, process strip, node card, autosave, and task board as topic/story.
4. The execution component appears only on the node selected in the requirement template.
5. A requirement can link one active project, topic, or story.
6. The second link is rejected or requires explicit change-path confirmation with a reason.
7. Completed, terminated, and deleted projects are not selectable.
8. Unlink keeps the requirement and history.
9. Target details show only a direct source requirement.
10. Existing project/topic/story flows remain visually and behaviorally unchanged.

- [ ] **Step 5: Self-review the whole change.** Compare the implementation against every heading in the spec: hierarchy, one-target invariant, template snapshots, runtime component placement, permissions, audit, historical accounts, UI consistency, error handling, and future scope. Record any known limitation; do not call the feature complete until the review is clean.
- [ ] **Step 6: Commit only verified fixes.** For any defect found, add a regression test in the owning task’s test file, fix it, rerun the relevant stage and the full suite, then commit:

~~~bash
git status --short
git diff --check
git commit -am "fix: close requirement management review findings"
~~~

Do not push automatically in this plan. After the user reviews the result and confirms release readiness, push the existing release branches using the normal release procedure.

## Suggested Execution Order and Gates

The tasks are intentionally sequential:

~~~text
Task 1 schema
   ↓ self-review
Task 2 workflow/template contract
   ↓ self-review
Task 3 backend API and target invariant
   ↓ self-review
Task 4 requirement list/form
   ↓ self-review
Task 5 shared detail/runtime component
   ↓ self-review
Task 6 documentation and cross-links
   ↓ self-review
Task 7 full regression and browser acceptance
~~~

The recommended execution approach is Native because the backend and frontend tasks share exact DTO and component contracts, the current workspace is already on the requested release branch, and each stage has an explicit self-review gate. Subagent-driven execution is also valid if an independent reviewer is preferred for each stage.
