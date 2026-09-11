# Milestone Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove milestone exposure from active search and UI contracts, keep legacy database data safe, and preserve readable historical iteration-plan links on stories.

**Architecture:** The active search contract will contain only projects, tasks, and comments. Legacy milestone storage/services remain for data compatibility, while the milestone REST entry point and active frontend entry points are no longer exposed. Development-control responses will resolve both current and historical iteration-plan names, and saving an existing story will preserve a historical association without allowing new invalid associations.

**Tech Stack:** Spring Boot, MyBatis-Plus, JUnit 5, Vue 3, TypeScript, Node test runner, pnpm/Vite.

**Spec:** `docs/superpowers/specs/2026-09-08-iteration-plan-design.md`

## Global Constraints

- Do not drop or rewrite legacy milestone tables or historical rows.
- New story associations must reference a current confirmed iteration plan.
- Existing stories may retain an already-persisted historical iteration-plan association.
- Preserve unrelated user changes in both repositories.

---

### Task 1: Remove milestones from the active search contract

**Files:**
- Modify: `src/main/java/com/brad/pms/service/SearchService.java`
- Modify: `src/main/java/com/brad/pms/dto/response/SearchResultDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/SearchHitDTO.java`
- Modify: `src/test/java/com/brad/pms/service/SearchServiceTest.java`
- Modify: `../pms-front/src/api/search.ts`
- Modify: `../pms-front/src/types/domain.ts`
- Modify: `../pms-front/src/layout/Index.vue`
- Modify: `../pms-front/src/layout/chrome.ts`
- Modify: `../pms-front/src/layout/chrome.test.mjs`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`

- [ ] Add/update tests asserting search returns only projects, tasks, and comments and no milestone section or route.
- [ ] Run the focused backend and frontend tests and confirm they fail for the old milestone contract.
- [ ] Remove milestone query/mapping and active frontend rendering while keeping legacy storage classes untouched.
- [ ] Run the focused tests again and confirm they pass.

### Task 2: Hide the legacy milestone endpoint from the active API surface

**Files:**
- Modify: `src/main/resources/openapi/pms-api.yaml`
- Modify: `src/main/java/com/brad/pms/controller/MilestoneController.java`

- [ ] Add a regression check that the OpenAPI active search description and endpoint inventory no longer advertise milestone operations.
- [ ] Remove the legacy controller from the active Spring MVC surface without deleting milestone tables/services.
- [ ] Update the API description to use the active project/task/comment search contract.
- [ ] Run backend compilation and controller/API tests.

### Task 3: Preserve historical iteration-plan links on stories

**Files:**
- Modify: `src/main/java/com/brad/pms/service/IterationPlanService.java`
- Modify: `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`
- Modify: `src/test/java/com/brad/pms/service/NodeDevelopmentControlServiceTest.java`
- Modify: `../pms-front/src/views/project/detail/components/DevelopmentControlWorkbench.vue`
- Modify: `../pms-front/src/views/project/detail/development-control.test.mjs`

- [ ] Add a failing service test for an existing story whose iteration plan is no longer current: its response must still contain the plan name and saving it unchanged must succeed.
- [ ] Resolve referenced historical plan names in the backend response and permit only unchanged existing historical associations during save.
- [ ] Add the historical plan as a read-only editor option so opening the story does not blank the selection.
- [ ] Run the focused backend and frontend tests.

### Task 4: Clean review leftovers and verify the application

**Files:**
- Modify: `src/main/java/com/brad/pms/service/NodePlanResourceRiskService.java`
- Modify: `../pms-front/src/locales/zh-CN.ts`
- Modify: `../pms-front/src/locales/en-US.ts`
- Modify: `../pms-front/src/views/project/detail/components/DevelopmentControlWorkbench.vue`

- [ ] Remove the duplicate baseline-status assignment.
- [ ] Replace active user-facing manual/search wording that still calls the schedule concept milestones with iteration-plan wording; leave historical migration/schema compatibility references intact.
- [ ] Run backend tests, frontend tests, typecheck, build, and a Playwright smoke check for login, project detail, search, and plan/development sections.
- [ ] Review `git diff --check` and report any remaining legacy compatibility references separately.
