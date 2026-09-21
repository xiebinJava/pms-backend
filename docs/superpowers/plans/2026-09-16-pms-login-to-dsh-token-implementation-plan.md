# PMS 登录态自动换取 DSH 短期令牌实施计划
> **For agentic workers:** REQUIRED SUB-SKILL: follow this plan task by task, keep the red-green verification loop, and stop at review checkpoints.

**Goal:** 在 PMS iframe 与 DSH Host 之间建立一次性授权码桥接，让 DSH 的 dsh-pms Agent 自动兑换短期 PMS 委托令牌；不向浏览器暴露 PMS 用户 JWT 或 DSH 服务密钥，并保持现有服务端兜底链路。

**Architecture:** PMS 前端通过 postMessage 请求授权码，PMS 后端用当前登录态签发并持久化哈希后的单次授权码；DSH Client 接收授权码并通过 Host Remote 写入会话内存；DSH Host 以 service key 兑换 120 秒委托 JWT，并按 DSH session 缓存和续租。业务查询仍然只通过 PMS 的只读集成工具完成。

**Tech Stack:** Spring Boot、MyBatis-Plus、Flyway、JUnit/MockMvc；Vue 3、TypeScript、Axios、Node test runner；DSH TypeScript、Vitest、tsc、tsdown；浏览器 iframe 与 postMessage。

**Spec:** docs/superpowers/specs/2026-09-16-pms-login-to-dsh-token-design.md

## Global Constraints

- 只修改 pms-backend、pms-front、deepseek-harness 中本计划列出的文件；不启动、不修改、不删除 work-helper。
- 任何生产代码前必须先添加一个能证明缺陷或缺失能力的失败测试；每个任务执行 red、green、回归三步。
- 浏览器只允许传递一次性 authorizationCode；PMS 用户 JWT、DSH service key、兑换后的 PMS delegation JWT 不进入 URL、localStorage、sessionStorage、postMessage 或日志。
- 授权码和短期令牌都按 DSH session 隔离；PMS 登录会话撤销或过期后不得继续兑换。
- 所有 scope 由 PMS 后端 Agent 策略决定，浏览器请求中的 scope 只表达意图；第一版只开放 project_assistant 的项目/任务只读和 workspace embed。
- 保留现有 accessToken 与 pmsUserToken + serviceKey 兜底，但浏览器会话优先使用授权码链路。
- 不重置、覆盖或清理仓库已有的用户改动；提交时只暂存本任务新增或明确修改的文件。

---

## Task 1: 固化 Agent scope 策略和授权码契约

**Files:**

- Add: src/main/java/com/brad/pms/integration/dsh/security/DshAgentScopePolicy.java
- Add: src/main/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeIssueRequest.java
- Add: src/main/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeIssueResponse.java
- Add: src/main/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeExchangeRequest.java
- Add: src/test/java/com/brad/pms/integration/dsh/security/DshAgentScopePolicyTest.java
- Add: src/test/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeContractTest.java

**Steps:**

1. 先写 DshAgentScopePolicyTest：project_assistant 的三个允许 scope 返回稳定排序集合；未知 agent、空 agent、包含 pms:project:write 的请求分别抛出明确错误；重复 scope 规范化为去重后的排序集合。
2. 运行 mvn -q -Dtest=DshAgentScopePolicyTest,DshAuthorizationCodeContractTest test，确认新测试先失败，因为策略和 DTO 尚不存在。
3. 实现策略对象和四个 DTO。请求 DTO 使用 Jakarta 校验约束限制 dshSessionId 128、agentId 64 和 scopes 数量；响应只包含 authorizationCode、expiresInSeconds、scopes；兑换 DTO 不包含 userId。
4. 重新运行同一组测试，确认 green；再运行现有 DshTokenExchangeControllerTest，确保旧 token 接口契约没有被改坏。
5. 只提交本任务新增文件：git add -- src/main/java/com/brad/pms/integration/dsh/security/DshAgentScopePolicy.java src/main/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeIssueRequest.java src/main/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeIssueResponse.java src/main/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeExchangeRequest.java src/test/java/com/brad/pms/integration/dsh/security/DshAgentScopePolicyTest.java src/test/java/com/brad/pms/integration/dsh/api/DshAuthorizationCodeContractTest.java && git commit -m "feat: define dsh authorization code contract"

## Task 2: 增加一次性授权码持久化和生命周期服务

**Files:**

- Add: src/main/resources/db/migration/V44__create_dsh_authorization_codes.sql
- Add: src/main/java/com/brad/pms/entity/DshAuthorizationCodeDO.java
- Add: src/main/java/com/brad/pms/mapper/DshAuthorizationCodeMapper.java
- Add: src/main/java/com/brad/pms/integration/dsh/service/DshAuthorizationCodeService.java
- Add: src/test/java/com/brad/pms/integration/dsh/service/DshAuthorizationCodeServiceTest.java

**Steps:**

1. 先写服务测试，覆盖：生成码只返回明文而 mapper 只收到 SHA-256 哈希；签发绑定当前 userId、PMS sessionId、dshSessionId、agentId 和规范化 scopes；已使用、已过期、绑定不一致、用户停用、PMS session 撤销或过期都拒绝；并发兑换只能成功一次。
2. 运行 mvn -q -Dtest=DshAuthorizationCodeServiceTest test，确认测试先失败。
3. 用 Flyway V44 创建 dsh_authorization_codes：id、code_hash 唯一索引、user_id、pms_session_id、dsh_session_id、agent_id、scopes_json、expires_at、used_at、created_at；字段采用项目现有 DATETIME、InnoDB、utf8mb4 规范，并为 pms_session_id、expires_at 建索引。
4. 实现 mapper 的按 hash 加锁查询和 used_at 条件更新。服务使用 SecureRandom 生成 URL-safe 授权码，数据库只存 SHA-256；兑换过程在事务内先锁定并校验记录，再校验 UserMapper 和 AuthSessionMapper 的 live session，最后原子标记 used_at。
5. 将服务错误映射为稳定的 PMS_DSH_AUTH_CODE_INVALID、PMS_DSH_SESSION_INVALID、PMS_DSH_SCOPE_FORBIDDEN 语义；不要把授权码、hash、JWT 或 service key 放进异常日志。
6. 运行 mvn -q -Dtest=DshAuthorizationCodeServiceTest test 和 mvn -q -Dflyway.validateMigrationNaming=true test，确认 green；再运行已有认证和迁移相关测试。
7. 只提交本任务文件，提交信息为 git commit -m "feat: persist one-time dsh authorization codes"。

## Task 3: 暴露签发和兑换接口并接入安全边界

**Files:**

- Add: src/main/java/com/brad/pms/controller/DshAuthorizationCodeController.java
- Add: src/main/java/com/brad/pms/controller/DshAuthorizationCodeExchangeController.java
- Add: src/test/java/com/brad/pms/controller/DshAuthorizationCodeControllerTest.java
- Add: src/test/java/com/brad/pms/controller/DshAuthorizationCodeExchangeControllerTest.java
- Modify: src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java only if the new exchange route needs an explicit service-only branch

**Steps:**

1. 先写 MockMvc/controller 测试：正常 PMS Bearer 可签发；未登录不能签发；签发请求 scope 超出策略返回拒绝；错误 service key 返回 403；缺失、重复使用或绑定不匹配的授权码不能兑换；有效兑换返回 token、audience dsh-pms、120 秒 TTL 和最终 scopes。
2. 运行 mvn -q -Dtest=DshAuthorizationCodeControllerTest,DshAuthorizationCodeExchangeControllerTest test，确认先失败。
3. 实现 POST /integration/dsh/v1/authorization-codes，使用现有 UserContext、AuthSession 和 DshAuthorizationCodeService；不从请求体读取 userId 或 PMS sessionId。
4. 实现 POST /integration/dsh/v1/token/exchange，标记为 IgnoreAuth，仅允许 X-DSH-Service-Key；使用常量时间比较；复用 JwtTokenProvider.createDshDelegationToken，并让服务在单事务内完成授权码消费。
5. 对错误返回既有 ResponseResult 结构和 HTTP 语义：服务认证失败 403、授权码无效 401/403、PMS session 无效 401、scope 越权 403；响应消息不得回显 code_hash 或内部异常。
6. 运行两组新测试，再运行 mvn -q -Dtest=DshTokenExchangeControllerTest,DshCapabilityControllerTest test；最后执行 mvn -q test，记录已有失败与本任务失败的区别。
7. 只提交本任务新增或明确修改文件，提交信息为 git commit -m "feat: add dsh authorization code endpoints"。

## Task 4: PMS 前端实现 iframe 认证桥和授权码请求

**Files:**

- Add: pms-front/src/api/dsh-auth.ts
- Add: pms-front/src/components/ai/dsh-auth-bridge.ts
- Add: pms-front/src/api/dsh-auth.test.mjs
- Add: pms-front/src/components/ai/dsh-auth-bridge.test.mjs
- Modify: pms-front/src/layout/Index.vue

**Steps:**

1. 先写 Node tests：API 请求只发送当前 PMS cookie/内存 Bearer，不发送 dsh service key；bridge 只响应 parent window 且 origin 等于 document.referrer origin；source、type、version、requestId 不匹配时不响应；没有 referrer 时不响应；请求成功返回 authorizationCode，失败只返回安全错误。
2. 运行 pnpm exec node --test src/api/dsh-auth.test.mjs src/components/ai/dsh-auth-bridge.test.mjs，确认先失败。
3. 实现 dsh-auth.ts：调用 /integration/dsh/v1/authorization-codes，沿用现有 HTTP 插件的 token/cookie 行为；默认 agentId project_assistant 和三个只读 scope；不把 code 写入 storage 或 URL。
4. 实现独立于 page-context 的 dsh-auth-bridge.ts：监听 dsh 的 pms.dsh.auth.request，校验 parent、origin 和 payload，调用 API，并向同一个 sourceOrigin 回传 pms.dsh.auth.sync；授权码失败只回传 requestId 和可展示的失败状态，不回传内部异常。
5. 在 Index.vue 的 onMounted 安装 bridge，在 onBeforeUnmount 移除；保持现有 PMS 上下文响应器不变，禁止把认证消息混入 pms.context.sync。
6. 运行两组新测试和现有 pnpm exec node --test src/components/ai/page-context.test.mjs src/api/work-helper.test.mjs；再执行 pnpm build -- --mode development。
7. 只提交 pms-front 任务文件，提交信息为 git commit -m "feat: bridge pms login to dsh"。

## Task 5: DSH Host 插件增加授权码接收、兑换和 session cache

**Files:**

- Add: deepseek-harness/packages/pms/dsh-pms/src/auth/pms-auth-store.ts
- Add: deepseek-harness/packages/pms/dsh-pms/src/auth/pms-auth-store.test.ts
- Modify: deepseek-harness/packages/pms/dsh-pms/src/config.ts
- Modify: deepseek-harness/packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts
- Modify: deepseek-harness/packages/pms/dsh-pms/src/remote.ts
- Modify: deepseek-harness/packages/pms/dsh-pms/src/index.ts
- Modify: deepseek-harness/packages/pms/dsh-pms/tests/dsh-pms.spec.ts
- Modify: deepseek-harness/packages/pms/dsh-pms/README.md

**Steps:**

1. 先写 Vitest：PmsAuthStore 按 sessionId 保存和清理 code；不同 session 互不读取；设置新 code 会替换旧 code；client 在 code 存在时调用 /integration/dsh/v1/token/exchange 并发送 X-DSH-Service-Key；兑换 token 缓存按 expiresAt 提前续租；无 code 时仍走 static accessToken 或 legacy pmsUserToken fallback。
2. 运行 cd deepseek-harness && pnpm exec vitest run packages/pms/dsh-pms/tests/dsh-pms.spec.ts packages/pms/dsh-pms/src/auth/pms-auth-store.test.ts，确认新测试先失败。
3. 实现 PmsAuthStore 为内存 Map，值只包含 authorizationCode、agentId、scopes、receivedAt；不写文件、环境变量或日志。扩展 PmsIntegrationClient options 增加 authStore，并将 browser auth code 作为最高优先级但低于显式 accessToken。
4. 增加 token/exchange 请求：body 包含 authorizationCode、当前 dshSessionId、agentId、scopes；serviceKey 仅由 Host 进程配置；响应 token 只进入 session cache。兑换失败返回 PMS_AUTH_CONTEXT_MISSING、PMS_AUTH_CODE_EXCHANGE_FAILED 等安全错误，不打印 code 或 token。
5. 在 PmsContextController 增加 setAuthCode(sessionId, payload) 和 clearAuthCode(sessionId) Remote；在插件 index 创建并注入 authStore，确保 tool handler 使用调用方 sessionId 查询正确的令牌。
6. 运行专门 Vitest 和现有 dsh-pms 全量测试；再运行 pnpm exec tsc -b packages/pms/dsh-pms/tsconfig.host.json --force && pnpm exec tsdown --config packages/pms/dsh-pms/tsdown.config.ts。
7. 更新 README 的配置优先级、service key 位置、认证桥接和 legacy fallback；只提交本任务文件，提交信息为 git commit -m "feat: exchange pms auth codes in dsh host"。

## Task 6: DSH 浏览器 iframe bridge、自动续租、Host 配置和端到端验证

**Files:**

- Add: deepseek-harness/packages/client/ui-pms-workspace/src/client/pms-auth-bridge.ts
- Add: deepseek-harness/packages/client/ui-pms-workspace/tests/pms-auth-bridge.client.spec.ts
- Modify: deepseek-harness/packages/client/ui-pms-workspace/src/client/PmsWorkspace.tsx
- Modify: deepseek-harness/packages/client/ui-pms-workspace/src/client/index.ts
- Modify: deepseek-harness/packages/client/ui-pms-workspace/src/client/workspace-url.ts only if auth origin must reuse its allowlist helper
- Modify: ~/.dsh/profiles/web/cordis.patch.yml

**Steps:**

1. 先写 client test：iframe load 后发送 pms.dsh.auth.request；只接受来自 iframe contentWindow 且 origin 精确匹配 PMS origin 的 pms.dsh.auth.sync；requestId 不匹配的响应忽略；收到 code 后调用 bridge.setAuthCode；每 60 秒重新请求；unmount 时清理 listener、timer、authStore；换 PMS tab/session 时旧 session code 被 clear。
2. 运行 cd deepseek-harness && pnpm exec vitest run packages/client/ui-pms-workspace/tests/pms-auth-bridge.client.spec.ts packages/client/ui-pms-workspace/tests/pms-context-bridge.client.spec.ts，确认新测试先失败。
3. 实现 pms-auth-bridge.ts，生成 requestId，按 DSH sessionId、agentId 和只读 scopes 发 request；解析同步消息时校验 source pms、type pms.dsh.auth.sync、version 1、requestId、code 非空和 TTL 合法；保持旧 code 直到新兑换成功。
4. 接入 PmsWorkspace：复用现有 PMS origin allowlist；将 auth bridge 的 setAuthCode/clearAuthCode 传给 dsh-pms Remote；与现有 page context bridge 分离，任何 auth message 不进入 locator store。
5. 在 cordis.patch.yml 的 pms config 增加 serviceKey: process.env.PMS_DSH_SERVICE_KEY ?? ''；确认该值只由 DSH Host 读取，浏览器 URL 和页面配置不出现 service key。
6. 运行 client 测试、dsh-pms 全量测试、pms-front bridge 测试和 backend controller/service 测试；执行三端构建：PMS backend mvn -q -DskipTests package、PMS front pnpm build -- --mode development、DSH pnpm exec tsc -b packages/pms/dsh-pms/tsconfig.host.json --force && pnpm exec tsdown --config packages/pms/dsh-pms/tsdown.config.ts。
7. 做真实联调前只检查服务状态，不打印秘密：确认 PMS backend /api/health/ready 返回 database UP、migration 44；确认 PMS front 5174 和 DSH 5173 可访问；使用进程环境分别注入 PMS_JWT_SECRET、PMS_DSH_SERVICE_KEY，日志只输出 configured/empty。
8. 用测试账号登录 PMS，在 DSH 打开 PMS workspace，验证 iframe load 请求、PMS authorization-codes 200、DSH token/exchange 200、pms.projects.list 返回与当前用户权限一致；等待续租窗口后再次查询，确认未出现 PMS_AUTH_REQUIRED。
9. 验证负面路径：刷新 PMS 页面后重新获取 code；切换 DSH session 后旧 code 不可用；退出 PMS 后兑换返回 session invalid；篡改 origin、requestId、agentId 或 scope 时桥接不响应或后端拒绝；不在浏览器控制台、DSH UI 或日志中出现 token/service key。
10. 最后执行 git diff --check，并逐仓库检查 git status --short，只确认本任务文件和已有用户改动；使用 git diff --stat -- <本任务文件列表> 做最终 review，不执行 reset、checkout 或 broad cleanup。

## Review Checkpoint

完成 Task 1-3 后先做后端契约 review：确认授权码单次消费在数据库事务和锁内完成，scope 来自服务端策略，普通 Bearer 与 service key 边界没有混淆。

完成 Task 4-6 后做跨端 review：确认四段消息的 source/type/version/requestId 一致，PMS origin 校验是精确匹配，DSH service key 只在 Host，session cache 与 iframe lifecycle 对齐。

端到端完成标准：有效 PMS 登录态能在 DSH 中查询项目；PMS 用户 JWT 不离开 PMS；授权码不能重放；短期令牌能按 session 续租；刷新、切换项目、退出登录和服务不可用都有明确且可观测的失败行为；三端测试和构建命令均有本轮新鲜输出。
