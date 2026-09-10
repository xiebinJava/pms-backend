# 迭代计划替代里程碑实施计划

> **For the agent executing this plan:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to execute this plan task-by-task with checkpoints.

**Goal:** 在计划、资源与风险基线中新增可确认的迭代计划，并让研发故事关联迭代计划；日常研发流程移除里程碑概念，同时保留旧数据的兼容存储。

**Architecture:** 新增 `project_node_iteration_plan` 作为计划节点的子表，复用现有计划基线的版本、自动保存、确认和回滚生命周期。计划工作台一次性读写迭代计划、资源和风险；研发工作台通过项目级只读接口读取已确认的迭代计划，并在故事保存时由后端校验关联合法性。旧里程碑表和旧专题字段暂不删除。

**Spec:** `docs/superpowers/specs/2026-09-08-iteration-plan-design.md`

## Task 1: 写数据迁移和后端领域契约的失败测试

**Files:**
- Create: `src/test/resources/db/migration/V37__iteration_plan_migration.sql` (only if the project keeps migration fixtures)
- Create: `src/test/java/com/brad/pms/migration/IterationPlanMigrationTest.java`
- Modify: `src/test/java/com/brad/pms/service/NodePlanResourceRiskServiceTest.java`
- Modify: `src/test/java/com/brad/pms/service/NodeDevelopmentControlServiceTest.java`

**Steps:**

1. 先增加迁移测试，断言 `project_node_iteration_plan`、`iteration_plan_id` 和相应索引存在，并断言旧里程碑表仍存在。
2. 在计划服务测试中增加“保存并回显迭代计划”“删除仍被故事引用的迭代计划会拒绝”“确认时没有完整迭代计划会拒绝”的测试。
3. 在研发服务测试中增加“故事可以保存合法迭代计划”“跨项目/未确认计划会拒绝”“专题里程碑不再参与保存和完成校验”的测试。
4. 运行 `mvn -q -Dtest=IterationPlanMigrationTest,NodePlanResourceRiskServiceTest,NodeDevelopmentControlServiceTest test`，确认新测试先失败，失败原因应是领域类型、字段或服务行为尚未实现，而不是测试语法错误。

## Task 2: 实现数据库迁移、实体、Mapper 和后端 DTO

**Files:**
- Create: `src/main/resources/db/migration/V37__add_iteration_plans.sql`
- Modify: `src/main/resources/schema.sql`
- Create: `src/main/java/com/brad/pms/entity/ProjectNodeIterationPlanDO.java`
- Create: `src/main/java/com/brad/pms/mapper/ProjectNodeIterationPlanMapper.java`
- Create: `src/main/java/com/brad/pms/dto/request/NodeIterationPlanCmd.java`
- Create: `src/main/java/com/brad/pms/dto/response/NodeIterationPlanDTO.java`
- Modify: `src/main/java/com/brad/pms/entity/ProjectNodeDevelopmentStoryDO.java`
- Modify: `src/main/java/com/brad/pms/dto/request/NodeDevelopmentStoryCmd.java`
- Modify: `src/main/java/com/brad/pms/dto/response/NodeDevelopmentStoryDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/request/NodePlanResourceRiskUpdateCmd.java`
- Modify: `src/main/java/com/brad/pms/dto/response/NodePlanResourceRiskDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/request/NodeDevelopmentTopicCmd.java`
- Modify: `src/main/java/com/brad/pms/dto/response/NodeDevelopmentTopicDTO.java`

**Steps:**

1. Add V37 table with project/node ownership, name, owner, goal, status, start/due date, sort, timestamps and `(project_id, node_id, sort)` index.
2. Add nullable story `iteration_plan_id` and `(project_id, iteration_plan_id)` index. Do not drop historical milestone columns.
3. Add typed request/response fields and MyBatis mappings. Keep iteration plan owner optional while drafting, but validate it for confirmation.
4. Update `schema.sql` and OpenAPI declarations so a fresh database and generated API documentation match the migration.

## Task 3: Implement plan-baseline iteration plan persistence and validation

**Files:**
- Modify: `src/main/java/com/brad/pms/service/NodePlanResourceRiskService.java`
- Modify: `src/main/java/com/brad/pms/dto/request/NodePlanResourceRiskUpdateCmd.java`
- Modify: `src/main/java/com/brad/pms/dto/response/NodePlanResourceRiskDTO.java`
- Modify: `src/main/java/com/brad/pms/controller/NodePlanResourceRiskController.java` (only if endpoint annotations need changes)
- Create: `src/main/java/com/brad/pms/service/IterationPlanService.java`
- Create: `src/main/java/com/brad/pms/controller/IterationPlanController.java`

**Steps:**

1. Inject the iteration mapper and include sorted iteration plans in plan-workbench reads and save responses.
2. Normalize names, goal and status; validate date order and project-member owners.
3. Replace iteration plan rows inside the same transaction as resources and risks. Refuse removal of rows referenced by development stories.
4. Extend completion validation to require at least one complete iteration plan, while leaving draft autosave permissive.
5. Add project-level `GET /projects/{projectId}/iteration-plans`; return only rows from the confirmed, decision-current plan baseline, so development never selects stale planning data.
6. Run the focused backend tests from Task 1 and make them pass.

## Task 4: Move story association from milestone to iteration plan

**Files:**
- Modify: `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`
- Modify: `src/main/java/com/brad/pms/entity/ProjectNodeDevelopmentStoryDO.java`
- Modify: `src/main/java/com/brad/pms/dto/request/NodeDevelopmentStoryCmd.java`
- Modify: `src/main/java/com/brad/pms/dto/response/NodeDevelopmentStoryDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/request/NodeDevelopmentTopicCmd.java`
- Modify: `src/main/java/com/brad/pms/dto/response/NodeDevelopmentTopicDTO.java`
- Modify: `src/main/java/com/brad/pms/entity/ProjectNodeDevelopmentTopicDO.java` only if legacy field mapping must be isolated
- Modify: `src/test/java/com/brad/pms/service/NodeDevelopmentControlServiceTest.java`

**Steps:**

1. Remove milestone mapper lookup, milestone validation, milestone completion guard, and milestone DTO mapping from the active development flow.
2. Validate non-null story iteration-plan IDs against `IterationPlanService`/the confirmed plan set; allow null for unplanned stories.
3. Persist and map `iterationPlanId` and display name on stories; preserve topic/story owner validation and existing story/task decoupling.
4. Ensure no task association is reintroduced and run all backend tests.

## Task 5: Add the iteration-plan UI to the plan workbench

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/node-plan-resource-risk.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/plan-resource-risk.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/PlanResourceRiskWorkbench.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/plan-resource-risk.test.mjs`

**Steps:**

1. Add domain types and payload mapping for iteration plans.
2. Add an “迭代计划” section above resources with add/remove controls and one-row fields for name, owner, goal, status, and date range.
3. Include iteration plans in autosave fingerprints and save payloads; display them locked together with the confirmed baseline.
4. Update completeness helpers and tests so confirmation requires complete iteration plans.
5. Run `pnpm test -- src/views/project/detail/plan-resource-risk.test.mjs` and `pnpm typecheck`.

## Task 6: Replace milestone usage in the development UI

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Create or modify: `/Users/fs/Desktop/Project/pms-front/src/api/iteration-plan.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/DevelopmentControlWorkbench.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/development-control.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`

**Steps:**

1. Load iteration plans alongside development control data and remove `getMilestones` from this workbench.
2. Remove topic “所属里程碑” input and detail display. Add story “所属迭代计划” selector, with an explicit “未安排” option.
3. Show the selected iteration plan in the story tree/detail without adding task links.
4. Keep current node edit/rollback lifecycle and no full-page refresh behavior.
5. Add source tests for no milestone field/API usage and iteration-plan payload usage; run frontend tests and typecheck.

## Task 7: Remove the daily milestone entry points without deleting legacy data

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/Milestones.vue` (remove mount/use or leave unreachable if routing compatibility requires it)
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/milestone.ts` only if no active page imports it
- Modify: `src/main/java/com/brad/pms/service/SearchService.java` and its test only if milestone hits are still exposed in the normal project search

**Steps:**

1. Remove the project collaboration milestone tab and active milestone route/query entry points from the project detail page.
2. Keep legacy API/table code available for old data and migration safety unless no callers remain and tests prove it can be removed.
3. Run frontend source tests and search for remaining user-facing milestone labels in the project detail flow.

## Task 8: Verify live migration, services, and browser behavior

**Files:** no new files.

**Steps:**

1. Run `mvn -q test` and `mvn -q -DskipTests package` in `/Users/fs/Desktop/Project/pms-backend`.
2. Run `pnpm test`, `pnpm typecheck`, and `pnpm build` in `/Users/fs/Desktop/Project/pms-front`.
3. Apply V37 to the local MySQL database with the existing migrator credentials, then restart the backend and verify `/actuator/health/readiness` returns HTTP 200.
4. Use the current browser route to verify: plan node shows an editable 迭代计划 section when unlocked; confirmed plan is read-only; development story modal has an iteration-plan selector and no milestone selector; tasks remain independent.
5. Capture any remaining layout or API errors, fix them, then rerun the relevant focused tests and the final verification suite.
