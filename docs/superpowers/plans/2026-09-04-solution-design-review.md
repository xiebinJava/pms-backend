# 方案设计评审决策节点 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将已确认的 V2 原型落地为正式的“方案设计、评审与决策”节点工作台，并确保方案包、三类必要评审、决策锁定和节点完成规则由后端真实约束。

**Architecture:** 以 `nodeKey=design` 为边界新增三张节点业务表：单个方案包、三条固定评审记录、单个决策记录；服务层负责节点归属、权限、状态流转、乐观锁和完成门禁，控制器提供读取、保存、提交、评审完成、决策确认/重新打开接口。前端在现有项目详情页中仅对 design 节点渲染专用工作台，复用已有节点任务看板和权限模型，不改变 requirement 节点工作台及通用流程导航。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、Flyway、JUnit 5/Mockito、Vue 3 `<script setup>`、TypeScript、Ant Design Vue、Node test runner、Playwright。

**Spec:** `/Users/fs/Desktop/Project/pms-front/public/solution-design-review-demo-v2.html`（已验收的 V2 交互原型）及其对应的本轮用户确认方案。

## Global Constraints

- 仅 `nodeKey=design` 支持本工作台，其他节点继续使用现有通用详情。
- 一个节点只有一个方案包、三条固定评审（业务/产品、技术、测试/发布）和一个决策记录，不增加候选方案打分、任意新增评审或无明确用途的配置入口。
- 上游“需求澄清与范围基线”只读展示摘要；不得在本节点复制或编辑需求清单。
- 方案包必须先提交；三条必要评审必须全部通过；决策必须确认后才能完成节点。
- 决策确认后锁定结果、原因、条件和确认时间；重新打开决策后恢复可编辑且节点完成门禁重新关闭。
- 所有写接口由服务层再次校验项目、节点、权限、节点可编辑状态和状态前置条件；不能只依赖前端禁用按钮。
- 方案包和决策使用 `version` 做乐观锁；并发更新返回冲突，不覆盖他人修改。
- 不新增文件上传、Webhook、候选方案评分、复杂审批流或独立的评审人员配置页面。
- 每个任务完成一个独立的 RED-GREEN-REFACTOR 循环；测试必须先于对应生产代码。

---

### Task 1: 固化数据结构和迁移

**Files:**
- Create: `src/main/resources/db/migration/V18__solution_design_review.sql`
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/brad/pms/migration/SolutionDesignReviewMigrationTest.java`

**Interfaces:**
- Produces three tables consumed by the service layer: `project_node_solution_package`, `project_node_solution_review`, `project_node_solution_decision`.
- `project_node_solution_package` is unique by `(project_id, node_id)` and contains `package_version`, `product_solution`, `technical_solution`, `summary`, `scope_coverage`, `rollout_premise`, `status`, `version`.
- `project_node_solution_review` is unique by `(project_id, node_id, review_type)` and contains fixed review types `BUSINESS_PRODUCT`, `TECHNICAL`, `TEST_RELEASE`, status `PENDING`/`PASSED`, comment, completed metadata.
- `project_node_solution_decision` is unique by `(project_id, node_id)` and contains result `PASS`/`CONDITIONAL_PASS`/`RETURN_FOR_CHANGES`, reason, conditions, status `DRAFT`/`CONFIRMED`, confirmed metadata and `version`.

- [ ] **Step 1: Write the failing migration contract test**

  Add a test that reads V18 and the baseline schema and asserts the three table names, the two unique keys, the fixed status/type comments, optimistic-lock columns, and indexes for `(project_id,node_id)`.

- [ ] **Step 2: Run the migration test to verify it fails**

  Run `mvn -q -Dtest=SolutionDesignReviewMigrationTest test`.
  Expected: FAIL because V18 and the schema definitions do not exist.

- [ ] **Step 3: Add the minimal V18 migration and baseline schema definitions**

  Define the three tables with foreign keys to `project(id)` and `project_node(id)`, `VARCHAR` lengths matching the domain text limits, `TIMESTAMP` metadata, `version INT NOT NULL DEFAULT 0` on package/decision, and the uniqueness/index constraints described above. Keep `schema.sql` synchronized for fresh local schema initialization.

- [ ] **Step 4: Run the migration test to verify it passes**

  Run `mvn -q -Dtest=SolutionDesignReviewMigrationTest test`.
  Expected: PASS.

### Task 2: Backend solution-design domain and service

**Files:**
- Create: `src/main/java/com/brad/pms/entity/ProjectNodeSolutionPackageDO.java`
- Create: `src/main/java/com/brad/pms/entity/ProjectNodeSolutionReviewDO.java`
- Create: `src/main/java/com/brad/pms/entity/ProjectNodeSolutionDecisionDO.java`
- Create: `src/main/java/com/brad/pms/mapper/ProjectNodeSolutionPackageMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/ProjectNodeSolutionReviewMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/ProjectNodeSolutionDecisionMapper.java`
- Create: `src/main/java/com/brad/pms/dto/request/NodeSolutionPackageUpdateCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/NodeSolutionDecisionConfirmCmd.java`
- Create: `src/main/java/com/brad/pms/dto/response/NodeSolutionDesignDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/NodeSolutionPackageDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/NodeSolutionReviewDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/NodeSolutionDecisionDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/NodeRequirementBaselineSummaryDTO.java`
- Create: `src/main/java/com/brad/pms/service/NodeSolutionDesignService.java`
- Test: `src/test/java/com/brad/pms/service/NodeSolutionDesignServiceTest.java`

**Interfaces:**
- `NodeSolutionDesignService#get(Long projectId, Long nodeId): NodeSolutionDesignDTO`
- `NodeSolutionDesignService#saveDraft(Long projectId, Long nodeId, NodeSolutionPackageUpdateCmd cmd): NodeSolutionDesignDTO`
- `NodeSolutionDesignService#submitPackage(Long projectId, Long nodeId, Integer version): NodeSolutionDesignDTO`
- `NodeSolutionDesignService#completeReview(Long projectId, Long nodeId, String reviewType, String comment): NodeSolutionDesignDTO`
- `NodeSolutionDesignService#confirmDecision(Long projectId, Long nodeId, NodeSolutionDecisionConfirmCmd cmd): NodeSolutionDesignDTO`
- `NodeSolutionDesignService#reopenDecision(Long projectId, Long nodeId): NodeSolutionDesignDTO`
- `NodeSolutionDesignService#requireConfirmed(Long projectId, Long nodeId): void`

- [ ] **Step 1: Write failing service tests**

  Cover these behaviors with Mockito and real DTO validation: non-design nodes are rejected; saving trims text and persists a draft; submitting rejects an incomplete package or an unconfirmed upstream baseline; completing a review only accepts the three fixed types; decision confirmation rejects a missing review/package/reason and requires conditions for `CONDITIONAL_PASS`; confirmed decisions are locked; reopening changes status back to draft; stale package/decision versions throw a conflict; `requireConfirmed` rejects bypass attempts.

- [ ] **Step 2: Run the service tests to verify they fail**

  Run `mvn -q -Dtest=NodeSolutionDesignServiceTest test`.
  Expected: FAIL because the DTOs, entities, mappers, and service do not exist.

- [ ] **Step 3: Implement entities, DTOs, mappers, and service**

  Follow the existing MyBatis-Plus entity and mapper conventions. `get` must return an empty draft package, three fixed review rows, an empty draft decision, and a read-only upstream baseline summary when no rows exist. `saveDraft` must reject confirmed decisions, use the package `version`, normalize whitespace, and keep the package in `DRAFT`. `submitPackage` validates the package version plus five non-empty text fields (product solution, technical solution, summary, scope coverage, rollout premise), checks the upstream requirement baseline is confirmed, and marks the package `SUBMITTED`. `completeReview` validates the review type and submitted package, sets only that fixed row to `PASSED`, and records the current user/time. `confirmDecision` checks package submitted, all three rows passed, result/reason/conditional conditions, uses the decision `version`, and sets `CONFIRMED`; `reopenDecision` clears confirmation metadata and sets `DRAFT`. All methods must call the existing `ProjectPermissionService` and `requireManageableNode` for writes.

- [ ] **Step 4: Run the service tests to verify they pass**

  Run `mvn -q -Dtest=NodeSolutionDesignServiceTest test`.
  Expected: PASS.

### Task 3: Backend HTTP endpoints and node completion gate

**Files:**
- Create: `src/main/java/com/brad/pms/controller/NodeSolutionDesignController.java`
- Modify: `src/main/java/com/brad/pms/service/NodeService.java`
- Test: `src/test/java/com/brad/pms/controller/NodeSolutionDesignPermissionTest.java`
- Test: `src/test/java/com/brad/pms/service/NodeServiceCompleteTest.java`
- Modify: `src/main/resources/openapi/pms-api.yaml`

**Interfaces:**
- `GET /projects/{projectId}/nodes/{nodeId}/solution-design`
- `PUT /projects/{projectId}/nodes/{nodeId}/solution-design`
- `POST /projects/{projectId}/nodes/{nodeId}/solution-design/submit`
- `POST /projects/{projectId}/nodes/{nodeId}/solution-design/reviews/{reviewType}/complete`
- `POST /projects/{projectId}/nodes/{nodeId}/solution-design/decision/confirm`
- `POST /projects/{projectId}/nodes/{nodeId}/solution-design/decision/reopen`
- `NodeService.complete` calls `solutionDesignService.requireConfirmed(projectId,nodeId)` when `nodeKey` is `design`, after the common owner/task checks and before updating node status.

- [ ] **Step 1: Write failing controller and completion-gate tests**

  Assert all six endpoints use `@RequirePermission(PROJECT_READ)` consistently with the existing node workbench, invalid review types return a business 400, and `NodeService.complete` invokes the design gate for a design node but does not invoke it for a develop node.

- [ ] **Step 2: Run the tests to verify they fail**

  Run `mvn -q -Dtest=NodeSolutionDesignPermissionTest,NodeServiceCompleteTest test`.
  Expected: FAIL because the controller/endpoints and design gate do not exist.

- [ ] **Step 3: Implement the controller, completion gate, and OpenAPI contract**

  Map request bodies to the new commands, pass the path `reviewType` directly to service validation, return `ResponseResult.success`, and document request/response schemas and 400/403/409 cases in the same OpenAPI style as the requirement-scope endpoints. Add the `NodeSolutionDesignService` dependency to `NodeService` without changing other node behavior.

- [ ] **Step 4: Run targeted backend tests and OpenAPI validation**

  Run `mvn -q -Dtest=NodeSolutionDesignPermissionTest,NodeServiceCompleteTest test` and `./scripts/validate-openapi.sh`.
  Expected: PASS.

### Task 4: Frontend domain, API, and deterministic state helpers

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/api/node-solution-design.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/solution-design.ts`
- Test: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/solution-design.test.mjs`

**Interfaces:**
- `getNodeSolutionDesign(projectId,nodeId): Promise<NodeSolutionDesign>`
- `saveNodeSolutionPackage(projectId,nodeId,payload): Promise<NodeSolutionDesign>`
- `submitNodeSolutionPackage(projectId,nodeId,version?): Promise<NodeSolutionDesign>`
- `completeNodeSolutionReview(projectId,nodeId,reviewType,comment?): Promise<NodeSolutionDesign>`
- `confirmNodeSolutionDecision(projectId,nodeId,payload): Promise<NodeSolutionDesign>`
- `reopenNodeSolutionDecision(projectId,nodeId): Promise<NodeSolutionDesign>`
- Helpers `isSolutionPackageComplete`, `isSolutionDecisionComplete`, `allSolutionReviewsPassed`, and `solutionReviewTypes` are pure and have no UI dependencies.

- [ ] **Step 1: Write failing helper/API/source contract tests**

  Assert package completeness requires the package version plus all five text fields, conditional-pass decisions require conditions, all three fixed review types must be passed, API paths contain the exact endpoint suffixes, and the project detail source will later mount the workbench only for `activeNode.nodeKey === 'design'`.

- [ ] **Step 2: Run the frontend tests to verify they fail**

  Run `cd /Users/fs/Desktop/Project/pms-front && pnpm test -- src/views/project/detail/solution-design.test.mjs`.
  Expected: FAIL because the helper, domain types, API module, and design-node integration do not exist.

- [ ] **Step 3: Add the types, API wrappers, and pure helpers**

  Model the server DTOs exactly, including optimistic-lock versions, `DRAFT`/`SUBMITTED`/`CONFIRMED` statuses, upstream baseline summary, fixed review type union, and decision result union. Keep API wrappers thin like `node-requirement-scope.ts` and keep gating calculations pure.

- [ ] **Step 4: Run the helper/API tests to verify they pass**

  Run `cd /Users/fs/Desktop/Project/pms-front && pnpm test -- src/views/project/detail/solution-design.test.mjs`.
  Expected: PASS.

### Task 5: Frontend solution-design workbench and project-detail integration

**Files:**
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/SolutionDesignWorkbench.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/zh-CN.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/en-US.ts`
- Test: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/solution-design.test.mjs`

**Interfaces:**
- Component props: `projectId: number`, `nodeId: number`, `nodeReadOnly: boolean`, `canEdit: boolean`.
- Component events: `solution-status(status: string)` and `saved()`.
- The component renders: upstream baseline read-only strip; one editable solution package with product/technical solution, summary, scope coverage and rollout premise; exactly three review rows; one decision form; compact completion checks; no candidate cards, arbitrary scores, file-upload controls, or task-add control.

- [ ] **Step 1: Extend failing source/component tests**

  Assert the workbench uses the new API methods, renders the three fixed review labels, keeps upstream content read-only, disables decision confirmation until package/reviews/reason are complete, locks decision inputs after confirmation, offers reopen, and `index.vue` renders it only for `design` while retaining `RequirementScopeWorkbench` for `requirement`.

- [ ] **Step 2: Run the frontend tests to verify they fail**

  Run `cd /Users/fs/Desktop/Project/pms-front && pnpm test -- src/views/project/detail/solution-design.test.mjs`.
  Expected: FAIL because the component and integration are not present.

- [ ] **Step 3: Implement the workbench and integration**

  Follow the existing requirement workbench patterns for loading, skeleton/error state, messages, disabled/read-only behavior, and optimistic-lock payloads. Add a save-draft action and a submit-package action; allow one `完成评审` action per pending review; provide the decision select, reason and conditional conditions, confirm and reopen actions. Emit the solution status so the parent can keep the node completion button state understandable, but retain the backend as the final gate. Insert the component before the shared task board and leave the existing requirement workbench condition unchanged.

- [ ] **Step 4: Run frontend unit/type/build checks**

  Run `cd /Users/fs/Desktop/Project/pms-front && pnpm test`, `pnpm typecheck`, and `pnpm build`.
  Expected: PASS with no TypeScript errors.

### Task 6: Documentation and integration review

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-backend/docs/business-specification.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/docs/user-manual.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/solution-design.test.mjs`
- Create outside repository: `/tmp/pms-solution-design-review.mjs`

**Interfaces:**
- Business documentation records the fixed three-review model, submit/confirm/reopen lifecycle, upstream read-only baseline, backend completion gates, and no-overdesign boundary.
- User manual explains what the node is for, what users must fill, what each status means, and the minimum steps to complete it in Chinese and English-compatible wording.

- [ ] **Step 1: Write documentation/source regression assertions**

  Add assertions for the exact business terms and the no-overdesign constraints, then run them to confirm they fail before documentation changes.

- [ ] **Step 2: Update both documents and the source assertions**

  Describe actual implemented behavior only: no claims of automatic approval, no file management promises, and no claim that a review can be reassigned from this screen.

- [ ] **Step 3: Run backend and frontend regression suites**

  Run `mvn -q test`, `cd /Users/fs/Desktop/Project/pms-front && pnpm test && pnpm typecheck && pnpm build`.
  Expected: PASS.

- [ ] **Step 4: Run rendered Playwright validation and capture evidence**

  Use the current Vite app at `http://127.0.0.1:5173/projects/2` when the local session is available; otherwise use a test-authenticated or static route only to validate the component surface. Check desktop and a 390px viewport, page identity, nonblank render, no framework overlay, console errors, package/review/decision interactions, and screenshot evidence. The target flow is: project detail -> select “方案设计、评审与决策” -> save/submit package -> complete remaining review -> confirm decision -> reopen decision -> confirm locked state.

- [ ] **Step 5: Perform independent code review and fix findings**

  Inspect only the relevant diff, verify service-layer authorization and lifecycle gates cannot be bypassed, check stale-version handling, confirm requirement node behavior is unchanged, and compare rendered layout with the accepted V2 demo. Fix every actionable finding and rerun the affected tests before reporting completion.
