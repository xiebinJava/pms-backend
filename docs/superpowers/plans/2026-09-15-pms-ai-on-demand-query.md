# PMS AI On-Demand Query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace automatic full-page PMS context injection with lightweight page hints plus authoritative, on-demand PMS query tools, starting with current-user task queries.

**Architecture:** The PMS frontend sends only page type, route, project/node identifiers, and active filters as `page_context`. Work Helper exposes registered read-only tools to the Agent; the Agent decides when to call `pms.context.inspect` or `pms.task.query`. PMS remains the authority: it authenticates the short-lived delegation token, applies the logged-in user's data scope, performs the query, and returns structured results. Existing write commands remain preview-only and unchanged.

**Tech Stack:** Vue 3 + TypeScript frontend, FastAPI/Python Work Helper, AgentScope tool registry, Spring Boot + MyBatis-Plus PMS backend, JUnit 5/Mockito, pytest.

**Spec:** `docs/superpowers/specs/2026-09-15-pms-ai-on-demand-query.md`

## Global Constraints

- Page context is a locator, not an authoritative business-data source.
- The model must not receive arbitrary shell execution or database credentials.
- PMS AI delegation tokens remain short-lived, scoped, and out-of-band from model-visible context.
- `scope=mine` always resolves the assignee from the authenticated PMS user; callers cannot select another user.
- Read queries may execute automatically; data mutations continue to require preview and explicit confirmation.
- Existing routes and the existing `pms.context.inspect` and `pms.command.preview` contracts remain backwards compatible.
- Do not commit or reset unrelated dirty-worktree changes.

### Task 1: Lock the query contract with failing tests

**Files:**
- Create: `src/test/java/com/brad/pms/ai/query/AiTaskQueryServiceTest.java`
- Modify: `src/test/java/com/brad/pms/controller/AiCommandControllerTest.java` or create `src/test/java/com/brad/pms/controller/AiQueryControllerTest.java`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_pms_tools.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_chat_service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_agents.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_extensions.py`

**Interfaces:**
- Backend will provide `AiTaskQueryService.query(AiTaskQueryRequest)` and `POST /ai/query/tasks`.
- Work Helper will provide `PmsApiClient.task_query(arguments, token)` and registered tool `pms.task.query`.
- Agent `project_assistant` will allow `pms.context.inspect`, `pms.task.query`, and `pms.command.preview`.

- [ ] **Step 1: Write backend service tests first.**

  Cover: `scope=mine` uses `UserContext.userId()` rather than a request user ID; `due=today` excludes non-today tasks; `status=open` excludes done tasks; `scope=project` rejects an unreadable project; response carries `dataScope`, `authoritative`, `timezone`, `asOfDate`, `total`, and paginated task fields.

- [ ] **Step 2: Write backend controller and delegation-scope tests first.**

  Assert `POST /ai/query/tasks` delegates to the service and that the issued AI delegation scopes include `ai:query:read`. Assert an AI delegation token is accepted only for the query route when it contains that scope.

- [ ] **Step 3: Write Work Helper transport and registry tests first.**

  Assert the request goes to `/api/ai/query/tasks` with the delegation header, the registry marks `pms.task.query` read-only, and its schema contains only `scope`, `due`, `status`, `project_id`, `node_id`, `page`, and `page_size`.

- [ ] **Step 4: Write Agent policy tests first.**

  Assert the PMS Agent catalog exposes the task query tool, the PMS project plugin scopes it, and the project system instructions say to call a read tool before answering PMS facts.

- [ ] **Step 5: Run the focused tests and verify they fail for the missing contract.**

  Run:

  ```bash
  mvn -q -Dtest=AiTaskQueryServiceTest,AiQueryControllerTest test
  cd /Users/fs/Desktop/Project/work-helper && uv run pytest tests/test_pms_tools.py tests/test_chat_service.py tests/test_agents.py tests/test_extensions.py -q
  ```

  Expected: compilation/import or assertion failures because the query service, endpoint, tool, and new Agent policy do not exist yet.

### Task 2: Implement the PMS read-only task query bridge

**Files:**
- Create: `src/main/java/com/brad/pms/ai/query/AiTaskQueryRequest.java`
- Create: `src/main/java/com/brad/pms/ai/query/AiTaskQueryResult.java`
- Create: `src/main/java/com/brad/pms/ai/query/AiTaskQueryService.java`
- Create: `src/main/java/com/brad/pms/controller/AiQueryController.java`
- Modify: `src/main/java/com/brad/pms/controller/AiDelegationController.java`
- Modify: `src/main/java/com/brad/pms/security/AuthInterceptor.java`
- Test: `src/test/java/com/brad/pms/ai/query/AiTaskQueryServiceTest.java`
- Test: `src/test/java/com/brad/pms/controller/AiQueryControllerTest.java`

**Interfaces:**
- `AiTaskQueryRequest` is a record with `scope`, `due`, `status`, `projectId`, `nodeId`, `page`, and `pageSize`.
- `AiTaskQueryService.query(AiTaskQueryRequest)` returns `AiTaskQueryResult`.
- `AiQueryController.queryTasks(@RequestBody AiTaskQueryRequest)` is `POST /ai/query/tasks`.

- [ ] **Step 1: Implement request normalization and bounds.**

  Normalize blank values to defaults: `scope=mine`, `due=any`, `status=open`, `page=1`, `pageSize=50`; reject unknown enum values and clamp/reject page sizes above 100 with a clear business error.

- [ ] **Step 2: Implement permission-first query assembly.**

  Use `UserContext.userId()` for `mine`. Obtain readable project IDs via `ProjectService.listReadableIds()`. Add project/node/date/status predicates to a MyBatis-Plus `LambdaQueryWrapper<ProjectTaskDO>`, then query only those rows with `ProjectTaskMapper.selectPage`.

- [ ] **Step 3: Enrich only returned rows.**

  Resolve project summaries through `ProjectService.listReadableByIds`, node names through the existing node service, and assignee display names through `UserService`. Return no descriptions, attachments, comments, tokens, or records outside the requested page.

- [ ] **Step 4: Add explicit result metadata.**

  Return `dataScope="task-query"`, `authoritative=true`, `timezone="Asia/Shanghai"`, `asOfDate`, total/page/pageSize/totalPage, and each task's readable status/priority labels. Use `TaskStatus.labelOf` and `Priority.labelOf`; keep numeric codes alongside labels.

- [ ] **Step 5: Add the scoped controller route.**

  Add `ai:query:read` to the normal AI delegation scopes. In `AuthInterceptor.allowedAiDelegationRoute`, allow only `POST /ai/query/tasks` with that scope. Do not allow the delegation token to access ordinary task/project APIs.

- [ ] **Step 6: Run the focused backend tests and verify they pass.**

  Run:

  ```bash
  mvn -q -Dtest=AiTaskQueryServiceTest,AiQueryControllerTest,PageContextServiceTest test
  ```

### Task 3: Register on-demand tools in Work Helper and stop ChatService prefetch

**Files:**
- Modify: `/Users/fs/Desktop/Project/work-helper/app/tools/pms.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/extensions.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/registry.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/prompts.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/chat/context.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/chat/service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_pms_tools.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_chat_service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_agents.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_extensions.py`

**Interfaces:**
- `PmsApiClient.task_query(arguments: dict[str, Any], token: str) -> dict[str, object]` posts to `/api/ai/query/tasks`.
- `pms_task_query(arguments: dict[str, Any]) -> dict[str, object]` validates allowed values and uses the bound delegation context.
- `ToolDefinition(name="pms.task.query", read_only=True, ...)` exposes the tool to AgentScope.

- [ ] **Step 1: Implement the API client and registry tool.**

  Map Python snake_case inputs to the backend's camelCase request keys in one place, preserve stable PMS error messages, and mark the tool read-only so no confirmation is requested.

- [ ] **Step 2: Expand the PMS project plugin and Agent allow-list.**

  Add `pms.task.query` to `pms-project-tools` and `project_assistant.allowed_tools`. Update the skill instruction to choose `pms.task.query` for task questions and `pms.context.inspect` for page/project snapshot questions.

- [ ] **Step 3: Remove automatic context inspection from `ChatService.stream`.**

  Delete the prefetch call and the appended full snapshot block. Keep page context in the prompt as a small locator, explicitly label it non-authoritative, and let AgentScope invoke the registered tools when needed.

- [ ] **Step 4: Update the project Agent prompt.**

  Require a tool call before answering PMS facts, require the tool result's `authoritative` and `dataScope` metadata to be respected, and require “未读取到/无法确认” when a tool fails or a field is absent.

- [ ] **Step 5: Run the focused Work Helper tests and verify they pass.**

  Run:

  ```bash
  cd /Users/fs/Desktop/Project/work-helper
  uv run pytest tests/test_pms_tools.py tests/test_chat_service.py tests/test_agents.py tests/test_extensions.py -q
  ```

### Task 4: Keep frontend context lightweight and verify end-to-end behavior

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/components/ai/page-context.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/list/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/AiProjectAssistantDrawer.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/layout/Index.vue`
- Create/modify: frontend context contract tests next to the affected views.

**Interfaces:**
- The drawer continues to pass `page_context` to Work Helper, but its payload contains only `pageType`, `route`, `projectId`, `nodeId`, and minimal `pageState` locator fields.
- List locator fields: `view`, `keyword`, `status`, organization/manager/level/attention/current-node filters, page and page size.
- Detail locator fields: `activeSection`, project ID, current node ID, and version hints.

- [ ] **Step 1: Add an explicit lightweight-context marker.**

  Add `contextMode: "locator"` to the page state and document that no row, task, member, or node collections are placed in the drawer request.

- [ ] **Step 2: Keep the global FAB from replacing page-published locator context.**

  Preserve the existing page context when opening the drawer globally; only fall back to `inferPageContext(route)` when the page has not published a locator.

- [ ] **Step 3: Add source-level tests for list/detail locator payloads.**

  Assert the payload includes identifiers and filters, and does not embed `dataSource`, `nodes`, `members`, `followers`, or `tasks` collections.

- [ ] **Step 4: Run frontend tests and production build.**

  Run:

  ```bash
  cd /Users/fs/Desktop/Project/pms-front
  node --test src/views/project/list/project-list-context.test.mjs src/views/project/detail/project-detail-context.test.mjs src/layout/index.test.mjs
  pnpm build
  ```

### Task 5: Review the full tool contract and document the test flow

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-backend/README.md`
- Modify: `/Users/fs/Desktop/Project/work-helper/README.md`
- Test/verify: all focused suites from Tasks 2–4.

- [ ] **Step 1: Document the runtime distinction.**

  Explain that PMS owns data and permissions, Work Helper owns Agent/tool orchestration, and the tool is an HTTP-backed registered capability rather than arbitrary shell execution.

- [ ] **Step 2: Add manual acceptance cases.**

  Record these exact checks: “我今天有几个任务需要完成？” (calls `pms.task.query` with `mine/today/open`); “当前页面有哪些优先级为紧急的项目？” (calls `pms.context.inspect` and checks `priorityLabel`); “帮我创建任务” (calls preview only and waits for confirmation).

- [ ] **Step 3: Run all focused tests, then run the normal builds.**

  Backend focused tests must pass. If the full Maven suite requires unavailable Docker/Testcontainers, report that environmental blocker separately instead of treating it as a feature failure.

## Self-Review Checklist

- **Spec coverage:** The spec's on-demand context behavior is covered by Tasks 3–4; the task-query contract and permission boundary by Tasks 1–2; write-safety compatibility by Task 3; manual acceptance by Task 5.
- **No direct DB/model shell access:** The Agent sees only registry tools, and the PMS endpoint re-applies user scope.
- **No stale page facts:** ChatService no longer injects a stale full snapshot before every turn; `pms.context.inspect` remains available when the Agent explicitly needs it.
- **Pagination honesty:** Both context snapshots and task queries return totals and data scope so the Agent cannot claim a page as a full result.
- **Type consistency:** `pms.task.query` is the same name in the backend endpoint adapter, Work Helper registry, Agent allow-list, and plugin scope; its request fields are consistently snake_case in Work Helper and camelCase over HTTP.
- **Review result:** The plan is bounded to read-only query MVP plus removal of prefetch. Project writes, arbitrary CLI execution, and database access remain out of scope.
