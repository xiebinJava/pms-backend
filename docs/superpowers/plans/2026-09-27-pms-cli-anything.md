# PMS CLI with CLI-Anything Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 CLI-Anything 的生成方法和测试规范，构建一个类似飞书 CLI/GitHub CLI 的可安装 `pms` CLI，让本地 AI 工作台通过自然语言操作 PMS 的全部已授权业务能力。

**Architecture:** PMS 后端继续作为唯一业务真源，现有能力目录、查询门面和命令执行服务提供动态 Schema、流程上下文和安全校验。独立连接器仓库发布一个进入 PATH 的 `pms` CLI 和一个可被 Codex/Claude Code/WorkBuddy 读取的 PMS Skill；CLI-Anything 只用于生成初始骨架、命令设计、测试矩阵和 Skill 草稿，不作为运行时依赖。

**Tech Stack:** PMS：Spring Boot 3.5、Java 17、MyBatis-Plus、Flyway、JUnit 5；CLI：TypeScript、Node.js 22+、pnpm、Commander、Zod、undici、keytar、Vitest；认证：PKCE loopback 浏览器授权码流程、系统凭据管理器；生成与评审：CLI-Anything。

**Spec:** `pms-backend/docs/superpowers/specs/2026-09-27-pms-cli-only-design.md`

## Global Constraints

- 第一阶段不保留 MCP Server 和 OpenCLI Plugin。
- 对外命令名必须是 `pms`，安装后必须进入 PATH。
- CLI-Anything 只作为 CLI 生成、命令设计、Skill 生成和测试方法论，不作为 PMS 运行时依赖。
- PMS 后端是业务数据、权限、流程模板、动态字段、幂等和审计的唯一真源。
- CLI 不访问数据库，不复制 PMS 业务规则，不使用管理员身份代替当前用户。
- 能力、流程节点、组件和字段必须通过 PMS 能力目录动态发现，不得硬编码具体节点名称。
- 所有写操作必须携带用户身份、clientId、requestId 和 idempotencyKey；高风险操作支持 dry-run/确认。
- 结构化输出必须稳定；成功结果写 stdout，错误结果写 stderr，并使用非零退出码。
- access token 只保存在进程内存；refresh token 只能写入操作系统凭据管理器；不得保存密码或服务密钥。
- CLI 只能调用现有 `/api/integration/ai/v1` 集成门面；不得绕过能力目录直接调用项目、专题、故事等领域 Controller。
- 新 CLI 的来源标识统一为 `pms-cli`；现有 `mcp`/`opencli` 标识不作为新 CLI 请求值，旧 DSH 流程继续按现有兼容策略运行。
- CLI 登录使用 PKCE loopback 授权码流程；不得把 DSH service key 或管理员 Token 放到本地客户端。
- 第一阶段只保证本地具备终端能力的 Codex、Claude Code 和 WorkBuddy；ChatGPT 网页版远程调用不在本计划范围内。

## Review Focus

- 动态流程：模板节点、组件或字段移动后，CLI 必须通过 capabilities/context 读取新 Schema；由 Task 5 覆盖。
- 认证安全：过期 Token、撤销 refresh token、登出和多账号切换不得泄露凭据；由 Task 3 覆盖。
- 写操作幂等：相同 idempotencyKey 重试不能重复创建业务对象；由 Task 6 覆盖。
- 命令与 API 脱钩：后端新增或调整动态字段不能要求重新编译 CLI；由 Task 5 和 Task 8 覆盖。
- AI 可用性：命令帮助、JSON、错误码和 Skill 示例必须足够让 Agent 自主完成查询与安全写入；由 Task 7 和 Task 9 覆盖。

## 文件与模块边界

### PMS 后端

- `pms-backend/src/main/java/com/brad/pms/controller/CliAuthController.java`：CLI 浏览器授权码流程。
- `pms-backend/src/main/java/com/brad/pms/ai/connector/AiConnectorCapabilityService.java`：继续复用现有能力目录和动态 Schema。
- `pms-backend/src/main/java/com/brad/pms/ai/connector/AutomaticCommandExecutionService.java`：继续复用现有自动执行、幂等、版本和回读链路。
- `pms-backend/src/main/java/com/brad/pms/integration/cli/api/`：CLI 请求、响应、错误和认证 DTO。
- `pms-backend/src/main/resources/db/migration/V__cli_auth.sql`：仅在现有认证会话不能支持 CLI client/redirect/revoke 约束时新增授权码表，版本号以实际最新迁移为准。
- `pms-backend/src/main/java/com/brad/pms/controller/AiConnectorController.java`：移除 MCP/OpenCLI 适配器注释，保持 `/integration/ai/v1` 业务门面。
- `pms-backend/src/main/java/com/brad/pms/ai/connector/AutomaticOperationRequest.java` 和 `pms-backend/src/main/java/com/brad/pms/integration/ai/security/AiConnectorScopePolicy.java`：把新来源标识统一为 `pms-cli`。

### `pms-ai-connector` 仓库

- `apps/pms-cli/src/main.ts`：`pms` CLI 入口和退出码处理。
- `apps/pms-cli/src/auth/`：浏览器登录、凭据存取、Token 刷新和多账号。
- `apps/pms-cli/src/client/PmsClient.ts`：统一请求、requestId、业务错误解析、有限查询重试。
- `apps/pms-cli/src/capabilities/CapabilityResolver.ts`：能力目录、context 和动态字段解析。
- `apps/pms-cli/src/commands/core/`：auth、setup、doctor、capabilities、search、get、context、operation、workflow。
- `apps/pms-cli/src/commands/shortcuts/`：project、requirement、topic、story、task、iteration、dashboard 快捷命令。
- `apps/pms-cli/src/output/`：table、json、ndjson、error envelope 和稳定序列化。
- `packages/pms-contracts/src/`：CLI 与 PMS 后端共享的请求、响应、能力和错误 Schema。
- `skills/pms-project-management/SKILL.md`：Agent 使用规则、命令选择、动态能力读取和确认策略。
- `scripts/install-skill.*`：按 Codex/Claude Code/WorkBuddy 支持的目录安装 Skill。
- `tests/unit/`、`tests/contract/`、`tests/e2e/`：CLI、合约和真实 PMS 闭环测试。
- `package.json`、`pnpm-lock.yaml`：发布 `pms` 可执行入口和 Node 22+ 约束。

### Task 1: 固化 CLI-only 合约和命令设计

**Files:**
- Create: `pms-backend/docs/superpowers/specs/2026-09-27-pms-cli-only-design.md`
- Create: `pms-ai-connector/docs/cli-command-contract.md`
- Create: `pms-ai-connector/docs/cli-coverage-matrix.md`
- Modify: `pms-backend/src/main/java/com/brad/pms/ai/connector/AutomaticOperationRequest.java`
- Modify: `pms-backend/src/main/java/com/brad/pms/integration/ai/security/AiConnectorScopePolicy.java`
- Test: `pms-ai-connector/tests/contract/command-contract.test.ts`
- Test: `pms-backend/src/test/java/com/brad/pms/ai/connector/AutomaticOperationRequestTest.java`

**Interfaces:**
- Produces `CommandContract`, `OutputEnvelope`, `ErrorEnvelope`, `OperationRequest` 和 `CapabilityDescriptor` 的字段约定，后续任务不得自行改名。

- [ ] **Step 1: 写命令契约测试**：锁定 `pms capabilities --format json`、`pms operation preview`、`pms operation execute` 的 JSON 字段、退出码和错误输出位置。
- [ ] **Step 2: 运行契约测试并确认失败**：`pnpm vitest run tests/contract/command-contract.test.ts`，预期因 CLI 包和 Schema 尚未创建而失败。
- [ ] **Step 3: 编写 CLI 合约文档和共享 Schema**：定义 `ok/data/meta/error` 信封、requestId、auditId、refreshScopes、idempotencyKey、contractId/version、`clientId=pms-cli`，并固定 CLI 调用 `/api/integration/ai/v1`。
- [ ] **Step 3a: 建立功能覆盖矩阵**：至少覆盖需求、项目、项目节点、专题、专题节点、故事、故事节点、任务、子任务、迭代计划、流程模板、用户、组织架构、角色权限、批量导入、审计日志和企业项目看板；每一行标记 query/create/update/delete/workflow-action 是否已注册和测试。
- [ ] **Step 4: 修改后端请求记录和 scope policy**：允许并仅允许新 CLI 使用 `pms-cli`，同时保留现有 DSH UI 使用的旧路径，不让未知 clientId 绕过校验。
- [ ] **Step 5: 运行契约测试**：预期所有契约测试通过。
- [ ] **Step 6: 提交**：`git add docs tests packages && git commit -m "docs: define pms cli contracts"`。

### Task 2: 使用 CLI-Anything 生成初始 CLI Harness

**Files:**
- Create: `pms-ai-connector/.cli-anything/README.md`
- Create: `pms-ai-connector/.cli-anything/command-map.json`
- Create: `pms-ai-connector/.cli-anything/test-matrix.md`
- Create: `pms-ai-connector/skills/pms-project-management/SKILL.md`
- Test: `pms-ai-connector/tests/contract/harness-output.test.ts`

**Interfaces:**
- Consumes: Task 1 的命令契约、PMS 后端源码/API 文档和真实只读能力目录。
- Produces: 仅作为后续实现输入的命令组、参数说明、Skill 草稿和测试清单；不得直接成为运行时业务层。

- [ ] **Step 1: 在隔离分支安装 CLI-Anything Skill，并对 PMS 仓库运行 `/cli-anything`**：输入 PMS 源码路径和连接器文档，要求生成 API-backed CLI，不生成浏览器点击脚本。
- [ ] **Step 2: Review 生成结果**：删除任何数据库访问、固定节点名称、管理员 Token、重复业务规则和 GUI 自动化代码。
- [ ] **Step 3: 将可复用的命令分组、JSON 约定、Skill 示例和测试场景整理到 `.cli-anything/` 与 `skills/`**。
- [ ] **Step 4: 运行 Harness 输出测试**：确认每个核心命令都有自然语言示例、参数来源、失败处理和验证步骤。
- [ ] **Step 5: 提交**：`git add .cli-anything skills tests && git commit -m "chore: bootstrap pms cli harness from cli-anything"`。

### Task 3: 实现浏览器授权和凭据管理

**Files:**
- Create: `pms-backend/src/main/java/com/brad/pms/controller/CliAuthController.java`
- Create: `pms-backend/src/main/java/com/brad/pms/integration/cli/api/CliAuthDtos.java`
- Modify: `pms-front/src/router/index.ts`
- Modify: `pms-front/src/auth/sso.ts`
- Create: `pms-front/src/api/cli-auth.ts`
- Create: `pms-front/src/views/auth/cli-authorize.vue`
- Create: `pms-ai-connector/apps/pms-cli/src/auth/AuthStore.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/auth/BrowserAuth.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/auth.ts`
- Test: `pms-backend/src/test/java/com/brad/pms/controller/CliAuthControllerTest.java`
- Test: `pms-front/src/views/auth/cli-authorize.test.mjs`
- Test: `pms-ai-connector/tests/unit/auth.test.ts`

**Interfaces:**
- `GET http://localhost:5173/cli/authorize?client_id=pms-cli&redirect_uri=...&state=...&code_challenge=...&code_challenge_method=S256`：展示 PMS 浏览器授权页；未登录时回到登录页，登录后回到授权页。
- `POST /api/integration/cli/v1/authorize/approve`：当前 PMS 浏览器会话同意后，校验 client、loopback redirect 和 PKCE 参数，再回调一次性 code。
- `POST /api/integration/cli/v1/token`：支持 `authorization_code` + `code_verifier` 换取短期 access token/refresh token，也支持受保护的 `refresh_token` 刷新。
- `POST /api/integration/cli/v1/revoke`：撤销当前 CLI refresh token，返回 `{ revoked: true }`。
- `AuthStore.getActiveAccount(): Promise<Account | null>`、`AuthStore.save(account)`、`AuthStore.remove(accountId)`。

- [ ] **Step 1: 写后端 PKCE 授权码、loopback redirect allowlist、过期、重复消费、code_verifier 不匹配和撤销测试**。
- [ ] **Step 2: 运行后端测试并确认失败**。
- [ ] **Step 3: 实现 PMS `/cli/authorize` 授权页和 `/authorize/approve` 接口；实现一次性 authorization code、PKCE、state、loopback redirect allowlist、refresh token rotation 和 revoke；复用 PMS 登录、AuthSession 和权限体系，不接受密码参数，不复用 DSH service key exchange**。
- [ ] **Step 4: 写 CLI 凭据测试**：验证 access token 不落盘，refresh token 只通过 keyring 存储，登出后凭据不可继续使用。
- [ ] **Step 5: 实现 `BrowserAuth.ts` 的 loopback callback、PKCE code exchange 和 `pms auth login|status|logout|list|switch`**。
- [ ] **Step 6: 运行后端和 CLI 测试**：预期通过。
- [ ] **Step 7: 提交**：`git add ... && git commit -m "feat: add browser auth for pms cli"`。

### Task 4: 建立统一 PMS Client 和输出协议

**Files:**
- Create: `pms-ai-connector/apps/pms-cli/src/client/PmsClient.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/client/Errors.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/output/OutputWriter.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/output/envelopes.ts`
- Test: `pms-ai-connector/tests/unit/pms-client.test.ts`

**Interfaces:**
- `PmsClient.request<T>(request: PmsRequest): Promise<T>`。
- `OutputWriter.success(data, meta): void`。
- `OutputWriter.failure(error): void`，写 stderr 并设置非零退出码。
- 默认开发地址为 `http://localhost:8080/api`；生产地址必须由 `pms setup` 或配置文件显式设置。

- [ ] **Step 1: 写业务 HTTP 200 + 非零业务 code、HTTP 401/403/409/422/5xx、超时和 JSON 损坏响应测试**。
- [ ] **Step 2: 实现统一请求头：Bearer、x-request-id、`client-id=pms-cli`、idempotency-key、contract 绑定和 trace 信息**。
- [ ] **Step 3: 实现查询/能力读取有限重试；禁止写操作自动重试**。
- [ ] **Step 4: 实现 table/json/ndjson 输出，确保成功 stdout、错误 stderr**。
- [ ] **Step 5: 运行测试并提交**：`git commit -m "feat: add pms cli client and output protocol"`。

### Task 5: 实现动态能力、流程上下文和通用操作命令

**Files:**
- Modify: `pms-backend/src/main/java/com/brad/pms/controller/AiConnectorController.java`
- Create: `pms-backend/src/main/java/com/brad/pms/integration/ai/api/AiOperationPreviewRequest.java`
- Create: `pms-backend/src/main/java/com/brad/pms/integration/ai/api/AiOperationPreviewDTO.java`
- Create: `pms-ai-connector/apps/pms-cli/src/capabilities/CapabilityResolver.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/capabilities.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/search.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/get.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/context.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/operation.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/workflow.ts`
- Test: `pms-ai-connector/tests/contract/capabilities.test.ts`
- Test: `pms-ai-connector/tests/unit/dynamic-fields.test.ts`

**Interfaces:**
- `CapabilityResolver.list(scope?): Promise<CapabilityCatalog>`。
- `CapabilityResolver.context(resourceType, resourceId): Promise<ResourceContext>`。
- `POST /api/integration/ai/v1/operations/preview`：复用现有 `CommandPreviewService`，只生成差异和校验结果，不写业务数据。
- `pms operation preview <operation> --arguments-json <json>`。
- `pms operation execute <operation> --arguments-json <json> --idempotency-key <key>`。
- `pms workflow action <resource-type> <id> <action> --arguments-json <json>`。

- [ ] **Step 1: 写流程节点移动、字段类型变化、无权限动作过滤、旧 contractVersion 冲突测试**。
- [ ] **Step 2: 实现 `capabilities`、`search`、`get`、`context`，调用现有 `/api/integration/ai/v1/capabilities|query|context`，不在 CLI 中硬编码节点名称和字段名**。
- [ ] **Step 3: 在后端增加只读 `operations/preview` 门面并复用 `CommandPreviewService`；CLI 实现 `operation preview` 和 `operation execute`，参数由能力目录 Schema 校验，最终以 PMS 后端校验为准**。
- [ ] **Step 4: 实现 workflow action，转换为现有 `operations/execute` 能力调用，要求携带 contextId/contextVersion/contractId/contractVersion（若能力目录要求）**。
- [ ] **Step 5: 回读执行对象并输出 auditId、refreshScopes 和 warnings**。
- [ ] **Step 6: 运行动态能力测试并提交**：`git commit -m "feat: add dynamic pms operations"`。

### Task 6: 实现资源快捷命令

**Files:**
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/resourceCommands.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/project.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/requirement.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/topic.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/story.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/task.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/iteration.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/dashboard.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/shortcuts/admin.ts`
- Test: `pms-ai-connector/tests/unit/shortcut-commands.test.ts`

**Interfaces:**
- 快捷命令必须映射到 Task 5 的 `search/get/context/operation`，不得直接复制后端业务逻辑。
- 示例：`pms project list --status in_progress --format json`、`pms story list --topic 82`、`pms dashboard summary`。
- 管理类资源优先提供 `pms admin users|org|roles|audit|workflow-templates` 查询快捷命令；批量导入默认只提供能力目录驱动的预览和执行，不把文件解析规则复制到 CLI。

- [ ] **Step 1: 为每个资源写命令到通用操作的映射测试**。
- [ ] **Step 2: 实现只读快捷命令和 dashboard summary**。
- [ ] **Step 3: 实现创建/修改快捷命令，写操作统一支持 `--dry-run` 和 `--idempotency-key`**。
- [ ] **Step 4: 验证动态字段参数不会被快捷命令静态覆盖**。
- [ ] **Step 5: 运行测试并提交**：`git commit -m "feat: add pms resource shortcuts"`。

### Task 7: 完成 `pms` 安装器、Skill 和 Agent 适配

**Files:**
- Modify: `pms-ai-connector/package.json`
- Create: `pms-ai-connector/apps/pms-cli/src/main.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/setup.ts`
- Create: `pms-ai-connector/apps/pms-cli/src/commands/core/doctor.ts`
- Create: `pms-ai-connector/skills/pms-project-management/SKILL.md`
- Create: `pms-ai-connector/scripts/install-skill.ps1`
- Create: `pms-ai-connector/scripts/install-skill.sh`
- Create: `pms-ai-connector/docs/installation.md`
- Test: `pms-ai-connector/tests/e2e/install.test.ts`

**Interfaces:**
- `npm install -g @xiebinjava/pms-cli` 后提供 `pms`。
- `pms setup` 检查 Node、PMS URL、凭据和 Skill 状态。
- `pms doctor --format json` 返回可机器读取的诊断结果。
- `pms skill install --global` 安装 `SKILL.md` 到目标工作台的 Skill 目录。

- [ ] **Step 1: 写安装后 PATH、`pms --help`、`pms doctor --format json` 和 Skill 内容检查测试**。
- [ ] **Step 2: 创建 package bin、Windows PowerShell 和 macOS/Linux 安装脚本**。
- [ ] **Step 3: 编写 Skill：先调用 capabilities/context，查询优先，写入先预览，所有写入带幂等键，禁止猜节点和字段**。
- [ ] **Step 4: 使用 CLI-Anything 的 Skill/命令发现方式核验 Codex 可发现性，并补充 Claude Code/WorkBuddy 的安装说明**。
- [ ] **Step 5: 运行全新临时用户目录安装测试，验证无需源码目录即可使用 `pms`**。
- [ ] **Step 6: 提交**：`git commit -m "feat: package pms cli and agent skill"`。

### Task 8: 真实 PMS 只读和写入闭环

**Files:**
- Create: `pms-ai-connector/tests/e2e/scenarios/readonly.json`
- Create: `pms-ai-connector/tests/e2e/scenarios/write-isolated.json`
- Create: `pms-ai-connector/tests/e2e/pms-cli.e2e.test.ts`
- Create: `pms-ai-connector/docs/e2e-report.md`

**Interfaces:**
- 测试只使用短期用户 Token、隔离项目和能力目录实际返回的 operation/field/context。
- 清理必须调用 PMS CLI/后端业务命令，不允许 SQL。

- [ ] **Step 1: 运行 `pms capabilities`、搜索、详情、流程上下文、dashboard summary，以及用户、组织、角色、流程模板和审计只读闭环**。
- [ ] **Step 2: 运行隔离项目/专题/故事的创建、修改、回读和清理闭环**。
- [ ] **Step 3: 重放相同幂等键，验证不会重复创建**。
- [ ] **Step 4: 使用无权限用户和过期 Token，验证 CLI 不绕过 PMS 拒绝结果**。
- [ ] **Step 5: 在真实流程模板变更后再次运行动态字段用例**。
- [ ] **Step 6: 生成报告并提交**：`git commit -m "test: verify pms cli against real pms"`。

### Task 9: 生成发布包、文档和最终 Review

**Files:**
- Modify: `pms-ai-connector/README.md`
- Create: `pms-ai-connector/CHANGELOG.md`
- Create: `pms-ai-connector/docs/feishu-cli-alignment.md`
- Create: `pms-ai-connector/docs/compatibility-matrix.md`
- Create: `pms-ai-connector/.github/workflows/ci.yml`
- Test: `pms-ai-connector/tests/e2e/installed-cli.test.ts`

**Interfaces:**
- 发布包必须提供 Windows、macOS/Linux 的安装说明，并明确 ChatGPT 网页版不支持本地 CLI 直连。
- CI 必须运行 typecheck、unit、contract、installed-cli smoke 和隔离只读 E2E；真实写入 E2E 仅手动触发。

- [ ] **Step 1: 运行 `pnpm test`、`pnpm typecheck`、`pnpm build` 和安装后 smoke test**。
- [ ] **Step 2: 以空临时目录安装发布包，验证 `pms --version`、`pms auth status`、`pms doctor` 和 JSON 输出**。
- [ ] **Step 3: Review 飞书 CLI 的三层命令、JSON 信封、dry-run、认证、Skill 和系统凭据存储是否都有对应实现**。
- [ ] **Step 4: Review CLI-Anything 生成内容是否残留 GUI 点击、固定字段、重复业务规则或运行时依赖**。
- [ ] **Step 5: 运行旧适配器引用扫描，确认新 CLI、Skill、CI 和文档不再依赖 MCP/OpenCLI；为现有旧目录创建归档标签后再移除运行时依赖和适配器测试**。
- [ ] **Step 6: 运行安全检查：secret scan、依赖审计、Token 日志扫描、错误堆栈脱敏**。
- [ ] **Step 7: 创建发布候选版本并提交**：`git commit -m "release: publish agent-native pms cli"`。

### Task 10: 退役旧 MCP/OpenCLI 运行入口

**Files:**
- Delete or archive after the CLI release tag: `pms-ai-connector/apps/mcp-server/**`
- Delete or archive after the CLI release tag: `pms-ai-connector/apps/opencli-plugin/**`
- Delete or archive after the CLI release tag: `pms-ai-connector/tests/mcp/**`, `pms-ai-connector/tests/opencli/**`
- Modify: `pms-ai-connector/package.json`, `pms-ai-connector/README.md`, CI workflow and compatibility matrix
- Test: `pms-ai-connector/tests/e2e/legacy-entrypoint-scan.test.ts`

**Interfaces:**
- 旧适配器只保留 Git 历史或独立归档标签，不进入新 CLI 安装包和运行依赖。
- PMS 后端 `/integration/ai/v1`、现有 DSH UI 流程和业务能力服务不删除；它们是 CLI 的后端能力基础。

- [ ] **Step 1: 创建旧适配器归档标签并记录最后兼容版本**。
- [ ] **Step 2: 移除 MCP/OpenCLI runtime dependencies、启动脚本、构建脚本和 CI job**。
- [ ] **Step 3: 将 `AutomaticOperationRequest.clientId` 和 scope policy 的新 CLI 路径固定为 `pms-cli`，并验证不会意外开放未知来源**。
- [ ] **Step 4: 运行安装包依赖扫描和入口扫描，确认发布包只包含 `pms` CLI、共享 Client、contracts、capabilities 和 Skill**。
- [ ] **Step 5: 提交**：`git commit -m "refactor: retire mcp and opencli adapters"`。

## 交付顺序

第一阶段只交付以下可用闭环：

```text
安装 pms
  → 浏览器登录
  → capabilities
  → project/topic/story/task 查询
  → context
  → operation preview
  → operation execute
  → 回读和审计
```

随后再扩展资源快捷命令和全部管理功能。这样即使后续某个业务模块仍未覆盖，也不会阻塞 CLI 核心能力上线。

## 自审结果

- 需求覆盖：飞书 CLI 的安装、认证、三层命令、JSON、dry-run、Skill、系统凭据和测试，分别由 Task 3、4、5、6、7、8、9 覆盖。
- 动态流程覆盖：Task 5 的能力目录和 Task 8 的真实模板变更测试覆盖。
- 全功能覆盖：Task 1 的覆盖矩阵要求业务、配置、治理和看板资源都有能力目录条目或明确记录为未支持，不能用“命令数量”假设功能已完成。
- CLI-Anything 使用边界：Task 2 明确生成器只产出骨架、文档和测试输入，Task 9 检查不残留运行时依赖。
- MCP/OpenCLI 排除：全局约束、架构、安装文档和兼容矩阵均明确第一阶段不包含。
- 后端复用：Task 1、4、5、10 明确复用现有 `/integration/ai/v1`、`AiConnectorCapabilityService`、`AutomaticCommandExecutionService` 和 DSH 存量流程，不重复建设业务门面。
- 认证一致性：Task 3 使用 PKCE loopback 授权码，不再设计与现有 DSH service-key exchange 冲突的 device polling 方案。
- 来源标识一致性：Task 1、4、10 将新 CLI 的 `clientId` 固定为 `pms-cli`，并覆盖旧 `mcp/opencli` 限制。
- 发布闭环：Task 7 负责 PATH/Skill 安装，Task 8 负责真实业务，Task 9 负责验证和发布，Task 10 负责旧适配器归档。
