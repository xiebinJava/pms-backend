# PMS AI Conversation Intent Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 PMS 项目助手的多轮意图理解，让任务创建请求在会话历史、当前项目/节点、当前用户和确认执行之间形成闭环。

**Architecture:** PMS 继续是数据、身份和权限的唯一权威；Work Helper 负责会话历史、待处理意图和 Agent 工具编排；前端只负责展示流式回答、预览和确认。页面上下文只作为定位信息，工具调用前由 PMS 再解析和校验真实项目、节点、用户及版本。

**Tech Stack:** Vue 3 + TypeScript、FastAPI/Python、AgentScope、SQLAlchemy/Alembic、Spring Boot + MyBatis-Plus、JUnit 5、pytest、Node test。

**Spec:** `docs/superpowers/specs/2026-09-15-pms-ai-conversation-intent-repair.md`

## Global Constraints

- PMS 是项目数据、当前用户、权限和版本号的唯一权威来源。
- Work Helper 不得直连 PMS 数据库，不得执行任意 Shell，不得把浏览器传入的 userId 当成可信身份。
- 只读查询可以自动执行；所有写操作仍必须先预览，再由用户明确确认。
- 现有 `pms.context.inspect`、`pms.command.preview` 和 operation execute API 保持兼容。
- 会话按 `user_id + agent_id + session_id` 隔离；项目切换只更新 `ChatContextTag`。
- 不修改与本问题无关的未提交代码，不删除已有历史会话数据。

---

### Task 1: 固化失败场景和跨服务契约

**Files:**
- Create: `docs/superpowers/specs/2026-09-15-pms-ai-conversation-intent-repair.md`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_chat_service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_agent_providers.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_pms_tools.py`
- Modify: `src/test/java/com/brad/pms/ai/AiCommandContractTest.java`
- Modify: `src/test/java/com/brad/pms/controller/AiCommandControllerTest.java`

**Interfaces:**
- Work Helper runtime 接受 `history: list[ChatMessage]`，并将当前用户消息与历史消息分离，避免当前消息重复发送。
- Work Helper 的任务预览工具接受语义化引用 `current_project`、`current_node`、`current_user`，不要求模型填写 PMS 数字 ID。
- PMS 继续接受现有单任务 `task.create` preview；多任务由 Work Helper 维护 `previews[]` 并逐个执行，保持旧接口兼容。

- [ ] **Step 1: 写会话历史失败测试。**

  在 `test_chat_service.py` 增加测试：第一次请求写入“创建两个任务”，第二次请求只发送“确认”，断言 Runtime 收到的 history 中包含第一轮 user 和 assistant 消息，当前消息只出现一次；不同 user 不能读取同一 session。

- [ ] **Step 2: 写工具参数失败测试。**

  在 `test_pms_tools.py` 增加测试：任务预览工具拒绝未知字段、空任务标题和不支持的引用；使用 `current_project/current_node/current_user` 时转交解析上下文，而不是把字符串直接作为 `projectId/nodeId/assigneeId`。

- [ ] **Step 3: 写 PMS 权限和版本测试。**

  在 `AiCommandContractTest` 和 `AiCommandControllerTest` 覆盖：项目不可读、节点不属于项目、当前用户不可作为负责人、项目或节点版本过期时均不能产生可执行预览。

- [ ] **Step 4: 运行测试确认当前实现失败。**

  ```bash
  cd /Users/fs/Desktop/Project/work-helper
  uv run pytest tests/test_chat_service.py tests/test_agent_providers.py tests/test_pms_tools.py -q
  cd /Users/fs/Desktop/Project/pms-backend
  mvn -q -Dtest=AiCommandContractTest,AiCommandControllerTest test
  ```

  预期失败点是：当前 Runtime 没有 history 参数、任务预览仍接受任意 arguments、PMS 尚无当前用户/当前节点的任务预览解析契约。

### Task 2: 实现 Work Helper 的会话历史恢复

**Files:**
- Modify: `/Users/fs/Desktop/Project/work-helper/app/chat/models.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/chat/service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/sessions/service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/sessions/store.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/runtime/runtime.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/providers/openai.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/providers/dashscope.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/providers/mock.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_chat_service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_agent_providers.py`

**Interfaces:**
- `ChatService` 在调用 `prepare_turn` 前得到 `prior_messages`；当前请求保存后只把 `prior_messages` 传给 Runtime。
- `AgentRuntime.stream_reply(..., history: list[ChatMessage] | None = None)` 将 history 透传给 provider。
- Provider 将最近 24 条 user/assistant 消息转换为 AgentScope `Msg`，写入 `AgentState.context`；旧消息按时间从最早处裁剪。

- [ ] **Step 1: 写 provider history 测试。**

  使用 mock provider/AgentScope stub，断言历史顺序为 user → assistant → user，当前消息不会在 context 和 `reply_stream` 入参中重复；空历史仍保持现有单轮行为。

- [ ] **Step 2: 增加安全的会话快照接口。**

  在 `SessionService` 增加返回 `(session_id, prior_messages)` 的准备方法，校验 session 的 `user_id` 和 `agent_id`；不要让 API 请求中的 userId 覆盖已存在 session 的归属。

- [ ] **Step 3: 把历史接入 Runtime 和两个真实 Provider。**

  为 OpenAI、DashScope 和 Mock provider 统一增加 `history` 参数；只把用户/助手正文转成模型消息，不把 UI `context_tag` 当作事实消息，不把委托 token、数据库错误或内部 tool payload 暴露给模型。

- [ ] **Step 4: 修复重新生成路径。**

  regeneration 使用被重新生成的 user message 之前的历史作为 context，并保证不会出现重复 assistant 消息。

- [ ] **Step 5: 运行 Work Helper focused tests。**

  ```bash
  cd /Users/fs/Desktop/Project/work-helper
  uv run pytest tests/test_chat_service.py tests/test_agent_providers.py tests/test_conversation_persistence.py -q
  ```

### Task 3: 增加持久化的待处理意图和确认状态

**Files:**
- Create: `/Users/fs/Desktop/Project/work-helper/app/chat/intent.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/chat/models.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/sessions/models.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/sessions/store.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/sessions/service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/db/models.py`
- Create: `/Users/fs/Desktop/Project/work-helper/migrations/versions/0007_pending_actions.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_conversation_persistence.py`
- Create: `/Users/fs/Desktop/Project/work-helper/tests/test_chat_intent.py`

**Interfaces:**
- `TaskCreateDraft`：`project_ref`、`node_ref`、`tasks[]`；每个 task 含 `title`、`assignee_ref`、`due_date`。
- `PendingAction`：`kind`、`status`、`draft`、`previews`、`created_at`、`expires_at`。
- `SessionService.save_pending_action(session_id, action)`、`get_pending_action(session_id)`、`clear_pending_action(session_id)`。

- [ ] **Step 1: 写状态机测试。**

  覆盖 `EMPTY → DRAFT → NEEDS_CONFIRMATION → EXECUTED/CANCELLED/EXPIRED`；补充“确认”“取消”“修改截止日期”“就在当前节点”对草稿的合并测试，确认不完整的任务标题会进入澄清而不是调用 PMS。

- [ ] **Step 2: 增加 SQLite/内存存储字段。**

  在 `ChatSessionRow` 增加可空 `pending_action_json`，迁移只新增字段，不重写既有会话和消息；`InMemorySessionStore` 使用相同的数据模型，保证 fallback 行为一致。

- [ ] **Step 3: 实现意图合并器。**

  合并优先级为：结构化 pending action > 当前页面定位 > 新消息中的明确字段；“确认”只改变状态，不清空已解析字段；项目/节点/负责人引用保留为语义引用，最终由 PMS 解析。

- [ ] **Step 4: 增加过期和幂等规则。**

  预览过期后清除 operationId 并要求重新预览；执行成功后清除 pending action；同一个 operationId 和 idempotency key 不重复执行。

- [ ] **Step 5: 运行持久化测试。**

  ```bash
  cd /Users/fs/Desktop/Project/work-helper
  uv run pytest tests/test_chat_intent.py tests/test_conversation_persistence.py tests/test_migrations.py -q
  ```

### Task 4: 收紧任务预览工具并绑定权威 PMS 上下文

**Files:**
- Modify: `/Users/fs/Desktop/Project/work-helper/app/tools/pms.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/chat/service.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/registry.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/app/agents/prompts.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_pms_tools.py`
- Modify: `/Users/fs/Desktop/Project/work-helper/tests/test_agents.py`

**Interfaces:**
- 新增注册工具 `pms.task.create.preview`，输入只允许：

  ```json
  {
    "project_ref": "current_project",
    "node_ref": "current_node",
    "tasks": [
      {"title": "任务1", "assignee_ref": "current_user", "due_date": "2026-09-25"}
    ]
  }
  ```

- 工具返回 `{status, resolved_context, previews, clarification}`；每个 preview 保留 PMS 返回的 `operationId`、`contextVersion` 和 `refreshScopes`。

- [ ] **Step 1: 写工具 schema 和安全测试。**

  断言工具拒绝 `projectId`、`nodeId`、`assigneeId` 等模型直接注入字段；拒绝空标题、非法日期、超过 20 个任务和混合项目引用；工具为 read-only preview，不允许直接 execute。

- [ ] **Step 2: 将当前 page_context 绑定到本次请求。**

  在 ChatService → tool registry 的调用边界建立 request-scoped locator；它只包含页面类型、route、projectId、nodeId 和筛选信息，不包含可伪造的权限结论。工具调用 `pms.context.inspect` 获得权威 project/node/version，再调用现有 `pms.command.preview`。

- [ ] **Step 3: 更新 Agent 工具说明。**

  明确规定：任务创建必须调用 `pms.task.create.preview`；“当前项目/当前节点/我”使用语义引用；拿不到权威解析时只询问缺失字段；预览前不能声称任务已创建。

- [ ] **Step 4: 保留旧工具兼容路径。**

  `pms.command.preview` 继续支持现有内部调用，但只允许受控的 command-specific schema；对 `task.create` 缺少 `projectId/nodeId/title` 时在 Work Helper 本地直接返回可读错误，避免无效请求打到 PMS。

- [ ] **Step 5: 运行工具和 Agent 测试。**

  ```bash
  cd /Users/fs/Desktop/Project/work-helper
  uv run pytest tests/test_pms_tools.py tests/test_agents.py tests/test_extensions.py -q
  ```

### Task 5: 在 PMS 后端实现身份、权限和版本的最终校验

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/context/ProjectDetailContextAssembler.java`
- Modify: `src/main/java/com/brad/pms/ai/command/task/CreateTaskCommand.java`
- Modify: `src/main/java/com/brad/pms/ai/command/CommandArgumentReader.java`
- Modify: `src/main/java/com/brad/pms/security/AuthInterceptor.java`
- Modify: `src/test/java/com/brad/pms/ai/context/PageContextServiceTest.java`
- Modify: `src/test/java/com/brad/pms/ai/AiCommandContractTest.java`
- Modify: `src/test/java/com/brad/pms/controller/AiDelegationControllerTest.java`

**Interfaces:**
- 权威 context inspect 返回 `viewer` 的稳定用户标识/显示名、当前项目、当前节点及版本；不返回委托 token。
- `task.create` 支持 `assigneeScope=current_user`，由 PMS `UserContext.userId()` 解析为当前登录用户；普通 `assigneeId` 仍做项目成员和权限校验。
- `CreateTaskCommand.preview/execute` 对项目、节点、负责人、版本使用同一套校验规则。

- [ ] **Step 1: 写当前用户和权限测试。**

  覆盖 `current_user` 不受请求体 userId 影响；当前用户不是项目成员时返回明确业务错误；跨项目 nodeId、不可读项目、不可管理节点均不能预览。

- [ ] **Step 2: 增加 context viewer 和版本字段。**

  在现有项目详情上下文中加入最小 viewer 信息和 `projectVersion/nodeVersion`；保持已有字段兼容，不把敏感权限细节返回给模型。

- [ ] **Step 3: 收紧参数读取。**

  `requiredLong`、日期和标题校验在 command 层返回稳定中文错误；不允许把字符串数字、负数、布尔值静默转换成 ID。

- [ ] **Step 4: 运行 PMS AI focused tests。**

  ```bash
  cd /Users/fs/Desktop/Project/pms-backend
  mvn -q -Dtest=AiCommandContractTest,AiCommandControllerTest,AiDelegationControllerTest,PageContextServiceTest test
  ```

### Task 6: 支持多预览展示、自然语言确认和页面刷新恢复

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/AiProjectAssistantDrawer.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/work-helper.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/components/ai/page-context.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/components/ai/page-context.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/project-detail-context.test.mjs`

**Interfaces:**
- 前端将 `pendingPreview` 改为 `pendingPreviews`，保留旧单预览 SSE payload 的兼容解析。
- `confirmPreview()` 以不同 idempotency key 顺序执行全部 operationId；任意一个失败时保留失败项和结果，不重复执行已成功项。
- 当 pending preview 存在且用户输入确认词时，前端直接进入确认执行路径；没有 pending preview 时，确认词正常作为聊天消息发送。

- [ ] **Step 1: 写多预览解析测试。**

  覆盖单 preview、`previews[]`、部分执行失败、取消、过期等 SSE payload；确认 UI 不会把 operationId 或内部参数展示成用户不可理解的内容。

- [ ] **Step 2: 实现 pending preview 恢复。**

  抽屉打开或 session 列表加载时读取 Work Helper 返回的 pending action；如果 operation 已过期，显示“需要重新生成预览”，不显示可执行按钮。

- [ ] **Step 3: 实现 typed confirmation。**

  仅匹配明确确认词（确认、确认执行、执行吧）和取消词（取消、不要执行）；其他自然语言继续交给 Agent，避免误执行。

- [ ] **Step 4: 保持流式输出和机器人图标。**

  继续消费 SSE `delta`，不要等待完整回答后再渲染；assistant 消息使用机器人图标，工具/预览状态用独立卡片展示。

- [ ] **Step 5: 运行前端测试和构建。**

  ```bash
  cd /Users/fs/Desktop/Project/pms-front
  node --test src/components/ai/page-context.test.mjs src/views/project/detail/project-detail-context.test.mjs
  pnpm build
  ```

### Task 7: 端到端验收和可观测性

**Files:**
- Modify: `/Users/fs/Desktop/Project/work-helper/README.md`
- Modify: `/Users/fs/Desktop/Project/pms-backend/README.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/AiProjectAssistantDrawer.vue` only if acceptance telemetry needs a UI marker.

- [ ] **Step 1: 增加不含密钥的链路日志。**

  每次任务预览记录 `requestId/sessionId/agentId/projectId/nodeId/operationId/count` 和结果状态；禁止记录 delegation token、完整 prompt 和用户隐私内容。

- [ ] **Step 2: 执行固定手工场景。**

  1. 在项目 22 当前节点发送“创建任务1、任务2，负责人都是我，截止 9 月 25 日”。
  2. 检查 Agent 只询问一次必要澄清，并展示两个预览。
  3. 输入“确认”，确认两个任务都创建且只执行一次。
  4. 刷新页面，确认消息和未完成状态仍在。
  5. 切换到项目 24，确认历史会话仍属于同一用户，消息只增加项目标签。
  6. 用不可管理节点、过期版本和无权限用户重复测试，确认不会产生 operation。

- [ ] **Step 3: 运行完整相关测试。**

  ```bash
  cd /Users/fs/Desktop/Project/work-helper
  uv run pytest tests/test_chat.py tests/test_chat_service.py tests/test_chat_intent.py tests/test_pms_tools.py tests/test_conversation_persistence.py -q
  cd /Users/fs/Desktop/Project/pms-backend
  mvn -q -Dtest=AiCommandContractTest,AiCommandControllerTest,AiDelegationControllerTest,PageContextServiceTest test
  cd /Users/fs/Desktop/Project/pms-front
  node --test src/components/ai/page-context.test.mjs src/views/project/detail/project-detail-context.test.mjs
  pnpm build
  ```

- [ ] **Step 4: 更新运行文档。**

  明确 Work Helper 未启动、PMS 委托失效、上下文解析失败、预览过期和执行失败的用户提示；补充启动顺序和验收账号的测试流程。

## Rollback and rollout

先发布 Work Helper 的历史恢复和只读校验，再发布 PMS 当前用户/版本校验，最后发布前端多预览确认。每一步都保持旧 SSE 事件和旧单任务 preview 兼容；若新任务预览失败，可暂时关闭 `pms.task.create.preview`，保留只读查询和旧的显式预览路径。

## Plan self-review

- 会话历史问题由 Task 2 覆盖。
- “确认/当前节点/我”的意图状态由 Task 3 覆盖。
- 无效 ID 和权限绕过由 Task 4–5 覆盖。
- 两个任务的预览和执行由 Task 4、Task 6 覆盖。
- 刷新、换项目、过期和幂等由 Task 3、Task 6–7 覆盖。
- 查询仍只读、写操作仍需确认，未扩大 Agent 权限。
