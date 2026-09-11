# Project Permission Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一项目读取、创建、写入、治理、评论和生命周期的权限与数据范围，使所有具备 `project:read` 的标准员工可读取全部非删除项目内容，所有标准员工可创建项目，同时保留责任人有限写入和组织治理边界。

**Architecture:** 后端以 `AuthorizationService` 负责功能权限、`DataScopeResolver` 负责范围、`ProjectPermissionService` 负责项目资源加载与 capability 聚合、`ProjectPermissionPolicy` 负责责任关系和生命周期规则。项目读取采用“权限码 + 固定全公司覆盖”，项目写入采用治理路径或责任路径，不把三者强行做交集。前端只消费后端 capability；文档同步维护业务规则、使用手册和角色矩阵。

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL/MySQL-compatible SQL, JUnit 5/Mockito, Vue 3, TypeScript, Vite, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-03-project-permission-consolidation-design.md`

## Global Constraints

- 保留用户当前未提交改动；每次修改前先确认目标文件的现有 diff，不使用 reset、checkout 或整文件覆盖。
- 遵守 TDD：每个后端权限行为先补回归测试并观察失败，再实现最小代码，最后重构。
- 不改变人员、组织、审计和反馈模块现有权限码及行为；项目可读不继承反馈可见性。
- `ownerId` 不作为项目权限来源；项目责任关系只认创建人、项目经理、节点负责人和任务负责人。
- `project:read` 的 ALL 是代码内固定规则，不开放为角色管理页面可编辑的覆盖配置；`SELF` 的通用语义不改，项目创建单独将 SELF 解析为当前用户主属组织。
- 删除项目对普通读取接口表现为不存在；治理角色仍可加载并恢复；终止项目可读但不可写，恢复是唯一例外。
- 修改代码后必须执行后端测试、前端测试/构建和权限专项验收；完成前做一次独立的静态 review，并记录发现与处理结果。

---

## Task 1: Establish permission codes, fixed read scope, and policy test harness

**Files:**

- Modify: `src/main/java/com/brad/pms/security/PermissionCode.java`
- Modify: `src/main/java/com/brad/pms/security/DataScopeResolver.java`
- Modify: `src/main/java/com/brad/pms/migration/EnterpriseDataMigration.java`
- Test: `src/test/java/com/brad/pms/security/DataScopeResolverTest.java`
- Test: `src/test/java/com/brad/pms/security/PermissionCodeTest.java`

- [x] Inspect existing security tests and current user/role fixtures without changing them.
- [x] Add failing tests for `project:create`, `project:manage`, and `project:comment:write`; verify project read is fixed to ALL only when the caller has `project:read`.
- [x] Add the role seed bindings: every standard employee role gets `project:read`, `project:create`, and `project:comment:write`; preserve role-default ranges for create/write/manage; keep MEMBER without global `project:write`.
- [x] Add a dedicated project-create scope resolver so SELF means the caller’s exact primary organization, while generic SELF behavior remains unchanged.
- [x] Run the focused security tests and observe the expected RED state before implementation.
- [x] Implement the minimum permission-code, resolver, and seed changes; run the same tests to GREEN.
- [x] Add regression coverage proving an unrelated permission does not become ALL merely because `project:read` is ALL.

## Task 2: Split project resource loading, governance, responsibility, and lifecycle checks

**Files:**

- Modify: `src/main/java/com/brad/pms/service/ProjectPermissionService.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectPermissionPolicy.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectService.java`
- Modify: `src/main/java/com/brad/pms/controller/ProjectController.java`
- Modify: `src/main/java/com/brad/pms/dto/project/ProjectPermissionsDTO.java`
- Test: existing project permission/policy tests discovered in `src/test/java/com/brad/pms/service/`
- Test: add `src/test/java/com/brad/pms/service/ProjectPermissionServiceTest.java` if no equivalent exists

- [x] Add failing tests covering: read permission is required; ordinary employee reads all non-deleted projects; deleted projects are not-found to ordinary users; terminated projects are readable but not operational; creator and project manager have identical project capabilities; `project:manage` governance follows ALL or organization-tree scope; `ownerId` alone grants nothing; all standard roles can create; create scope uses the primary organization.
- [x] Add failing tests for lifecycle: only restore can operate on terminated projects, governance/creator/project manager can restore deleted projects, and ordinary users cannot discover deleted projects through direct IDs.
- [x] Implement explicit resource methods (`requireProjectReadable`, operational/write/manage/restorable/deletable variants) while keeping compatibility aliases only where necessary.
- [x] Implement two write paths: creator/PM responsibility path and role permission + data-scope governance path. Use `project:write` for routine project changes and `project:manage` for members, manager assignment, lifecycle, and destructive operations.
- [x] Make project creation use `project:create`; do not accept a manager on creation. Keep manager selection in the first-node kickoff confirmation.
- [x] Make project permissions DTO expose operation-level capabilities, including project comment writing, and ensure terminated/deleted state is reflected by backend capability rather than frontend status inference.
- [x] Update project controller annotations and service guards so controller annotations do not block creator/PM or responsible-user paths merely because MEMBER lacks `project:write`.
- [x] Run focused service/controller tests to GREEN, then refactor duplicated authorization predicates into named helpers.

## Task 3: Apply the permission model to nodes, tasks, comments, members, followers, and attachments

**Files:**

- Modify: `src/main/java/com/brad/pms/controller/NodeController.java`
- Modify: `src/main/java/com/brad/pms/service/NodeService.java`
- Modify: `src/main/java/com/brad/pms/controller/TaskController.java`
- Modify: `src/main/java/com/brad/pms/service/TaskService.java`
- Modify: `src/main/java/com/brad/pms/service/TaskAttachmentService.java`
- Modify: `src/main/java/com/brad/pms/controller/CommentController.java`
- Modify: `src/main/java/com/brad/pms/service/CommentService.java`
- Modify: `src/main/java/com/brad/pms/service/MemberService.java`
- Modify: `src/main/java/com/brad/pms/service/FollowerService.java`
- Modify: `src/main/java/com/brad/pms/dto/project/ProjectCommentDTO.java`
- Test: add/extend service tests for responsible-user writes, comment ownership, and resource ID binding

- [x] Add failing tests proving a task负责人 without `project:write` can update allowed own-task fields and upload an attachment, but cannot modify another task, move/delete tasks, or change project data.
- [x] Add failing tests proving a node负责人 without `project:write` can perform only the explicitly allowed node write operations; governance operations such as assigning another node owner remain guarded.
- [x] Add failing tests for project comments: every readable standard employee can add/delete their own comment using `project:comment:write`; no edit capability is exposed in phase one; governance can delete others only inside scope.
- [x] Add failing tests that member, follower, comment, task attachment, and project image reads cannot cross project IDs and that deleted projects do not leak through these paths.
- [x] Implement operation-specific controller baselines and service checks. Keep create/move/delete/manage operations on governance permissions; allow limited responsible-user operations through readable-project authorization plus relationship checks.
- [x] Add per-comment `canDelete` (or equivalent backend-derived capability) and preserve node/task capability fields in their own DTOs.
- [x] Add a project-bound image persistence path: migration/table and service/mapper carrying `project_id`; change image upload/read/delete routes to require the path project ID and readable/operational authorization.
- [x] Run focused resource-security tests to GREEN and inspect all direct-ID endpoints with `rg` for missing project authorization.

## Task 4: Remove full-scope ID materialization from search, lists, and statistics

**Files:**

- Modify: `src/main/java/com/brad/pms/service/SearchService.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectService.java`
- Modify: relevant project/task/milestone/comment mapper XML or mapper interfaces discovered during implementation
- Test: add `src/test/java/com/brad/pms/service/SearchServicePermissionTest.java`

- [x] Add failing tests verifying ALL-company project reads do not call `listReadableIds()` and still exclude deleted projects.
- [x] Add failing tests verifying narrow data scopes continue using database filtering/pagination and search/detail/statistics use the same visible-project rule.
- [x] Implement a direct database predicate/join/EXISTS path for ALL-company search instead of loading every project ID into memory.
- [x] Make project list and statistics exclude deleted projects for ordinary readers; preserve response compatibility for existing frontend fields.
- [x] Keep workbench queries personal (`my todo`/`my participation`) and do not replace them with company-wide project visibility.
- [x] Run search/list/statistics tests and inspect generated SQL or mapper conditions for pagination and deleted-state correctness.

## Task 5: Align frontend capability usage and project-image API

**Files:**

- Modify: `src/api/project.ts`
- Modify: `src/types/domain.ts`
- Modify: `src/store/user.ts` or the existing auth store only if needed
- Modify: `src/views/project/list/index.vue`
- Modify: `src/views/project/detail/index.vue`
- Modify: `src/views/project/detail/components/Comments.vue`
- Modify: `src/views/project/detail/components/TaskWorkPanel.vue`
- Modify: related project/task/comment tests discovered under `src/**/*.spec.*`

- [x] Add failing frontend tests for: create button shown only from user-level `project:create`; project comments use `canWriteComment`; comment delete uses per-comment capability; responsible task/node actions remain visible from DTO capability even without `project:write`; deleted/terminated project actions are hidden/disabled from backend capabilities.
- [x] Update the project image API to pass `projectId` and update detail-page upload/read call sites.
- [x] Add `canCreate` at login/list context rather than project detail DTO; do not collapse node/task capabilities into project capabilities.
- [x] Remove UI actions known to produce 403 (including unauthorized project comment publish/delete and inappropriate project management buttons) while keeping backend guards authoritative.
- [x] Run focused Vitest tests, then the full frontend test suite and production build.

## Task 6: Update business rules, user manuals, and permission matrix

**Files:**

- Modify: `docs/business-specification.md` in backend and frontend where both copies exist
- Modify: `docs/user-manual.md` in backend and frontend where both copies exist
- Modify: `docs/design-logic.md` in backend and frontend where both copies exist
- Modify: `src/locales/zh-CN.ts` and `src/locales/en-US.ts` only in the manual/business-rule sections that mirror the policy
- Test/verify: documentation references and permission-matrix search checks

- [x] Add a canonical “项目权限与数据范围” section covering read-all non-deleted contents, create scope, no manager on create, first-node manager selection, creator/PM equality, responsibility path vs governance path, lifecycle semantics, comments, attachments/images, feedback independence, workbench behavior, and system-admin bypass.
- [x] Add a complete role × permission × scope matrix for MEMBER, PROJECT_MANAGER, PROJECT_ADMIN, ORG_ADMIN, DEPT_MANAGER, BUSINESS_OWNER, and SUPER_ADMIN. Explicitly show that all standard roles have `project:create`, with DEPT/BUSINESS scoped to their organization tree.
- [x] Update user-facing operating instructions: all authorized employees can search/read project contents; creation does not ask for a project manager; the manager is selected at first-node confirmation; task/node responsible users get limited actions; governance actions depend on role scope.
- [x] Remove obsolete claims that ordinary employees only see participated projects, cannot create projects, or cannot comment on readable projects.
- [x] Keep people/organization/audit and feedback permission descriptions unchanged except for cross-reference notes.
- [x] Run `rg` checks for obsolete permission claims and review the rendered/manual test fixtures where available.

## Task 7: Verification and review

- [x] Run backend focused tests, all backend tests, and package/build verification using the repository’s documented commands.
- [x] Run frontend focused tests, all frontend tests, and the typecheck/build commands available in `package.json`; confirm no lint script is defined.
- [x] Start/restart backend and frontend as needed and run smoke checks against project list, detail, create, first-node manager confirmation, task owner update, comments, attachments, image upload, terminated project, and deleted project paths.
- [x] Inspect `git diff`, `git status`, and changed-file list separately for backend and frontend; confirm all pre-existing user changes remain intact.
- [x] Perform a review pass focused on authorization bypasses, direct-ID resource leaks, deleted/terminated handling, annotation/service mismatches, and documentation/matrix consistency.
- [x] Record review findings and resolutions in the final response, distinguishing tests actually run from checks not available in the local environment.
