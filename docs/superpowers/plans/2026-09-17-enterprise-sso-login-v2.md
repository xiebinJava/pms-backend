# Enterprise SSO Login Implementation Plan v2

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

Goal: 接入独立 OIDC 身份中心，让 PMS 和 DSH 使用同一套企业登录，同时保留 PMS 的业务权限和可控应急登录。

Architecture: 身份中心负责用户、密码、MFA、OAuth/OIDC、令牌、统一会话和密钥。PMS 与 DSH 都是 OIDC Client；PMS 同时是 pms-api Resource Server。我们新增的是身份中心接入、PMS 外部身份映射、会话桥接和管理配置，不重新实现密码和 OAuth 核心。

Tech Stack: 外部 OIDC Identity Provider、Spring Boot、MySQL、Flyway、MyBatis、PMS JWT validation、PMS Vue 前端、DSH TypeScript web session。

Spec: docs/superpowers/specs/2026-09-17-enterprise-sso-login-v2.md

## Global Constraints

- 不在 PMS 或 DSH 新建第二套密码库。
- 不在 PMS 自研 OAuth 授权服务器、授权码存储、Refresh Token 存储和 MFA。
- PMS 和 DSH 使用 Authorization Code + PKCE。
- PMS 使用 issuer + subject 作为外部身份主键，邮箱只用于首次匹配。
- PMS API 必须校验 issuer、签名、audience、scope、exp 和 session 状态。
- Refresh Token 只由身份中心或应用服务端持有，不能进入浏览器 localStorage、URL 或聊天上下文。
- 现有 PMS 本地登录保留为迁移期和 break-glass 入口，不能默认与 SSO 形成两个普通入口。
- 第一阶段不改项目和任务权限模型。
- 每个任务先写失败测试，再实现最小功能，再跑任务级测试。

---

### Task 1: 确定身份中心部署和客户端注册

Files:

- Create: docs/superpowers/specs/2026-09-17-enterprise-sso-provider-registration.md
- Modify: src/main/resources/application.yml
- Modify: src/main/resources/application-mysql.yml
- Modify: pms-front/.env.example
- Modify: deepseek-harness/.env.example
- Test: src/test/java/com/brad/pms/config/SsoConfigurationTest.java

Required decisions:

- 身份中心必须支持 OIDC Discovery、Authorization Code、PKCE、JWKS、RP-Initiated Logout 和密钥轮换。
- 注册三个逻辑对象：pms-web、dsh-web、pms-api。
- pms-web 不配置 client secret；dsh-web 的 secret 只放 DSH 服务端。
- 回调地址必须按环境精确配置，开发环境和生产环境不能复用 wildcard。

- [ ] Step 1: 写配置测试。缺少 issuer、client ID、redirect URI、audience 或生产环境 HTTPS 时，启动配置校验必须失败。
- [ ] Step 2: 运行测试。

    mvn -q -Dtest=SsoConfigurationTest test

    预期：失败，因为统一 SSO 配置校验尚未存在。

- [ ] Step 3: 增加 pms.sso.provider、pms.sso.clients.pms-web、pms.sso.clients.dsh-web 和 pms.sso.api-audience 配置。
- [ ] Step 4: 添加一份不包含 secret 的注册说明，记录 issuer、redirect URI、post_logout_redirect_uri、scope 和 audience。
- [ ] Step 5: 运行测试并提交。

    mvn -q -Dtest=SsoConfigurationTest test
    git add src/main/resources/application.yml src/main/resources/application-mysql.yml src/test/java/com/brad/pms/config/SsoConfigurationTest.java docs/superpowers/specs/2026-09-17-enterprise-sso-provider-registration.md
    git commit -m "feat: define external sso provider configuration"

### Task 2: 修复 PMS 当前 OIDC Client 的安全校验

Files:

- Modify: src/main/java/com/brad/pms/auth/OidcAuthProvider.java
- Modify: src/main/java/com/brad/pms/auth/DefaultOidcTokenClient.java
- Modify: src/main/java/com/brad/pms/auth/OidcPendingAuth.java
- Modify: src/main/java/com/brad/pms/auth/OidcStateStore.java
- Create: src/main/java/com/brad/pms/auth/OidcIdTokenValidator.java
- Create: src/main/java/com/brad/pms/auth/OidcJwksClient.java
- Test: src/test/java/com/brad/pms/auth/OidcAuthProviderTest.java
- Test: src/test/java/com/brad/pms/auth/OidcIdTokenValidatorTest.java

Interfaces:

- OidcPendingAuth 必须同时保存 state、nonce、PKCE verifier、redirect URI 和过期时间。
- OidcIdTokenValidator.validate(idToken, expectedIssuer, expectedAudience, expectedNonce) 返回经过签名和 claims 校验的身份。
- OidcJwksClient 按 issuer discovery 获取 JWKS，并缓存到密钥过期时间。

- [ ] Step 1: 写失败测试。错误签名、错误 issuer、错误 audience、错误 nonce、过期 token 和缺少 subject 都必须被拒绝。
- [ ] Step 2: 运行测试。

    mvn -q -Dtest=OidcAuthProviderTest,OidcIdTokenValidatorTest test

    预期：当前实现会错误接受仅 Payload 可解析的 ID Token，测试失败。

- [ ] Step 3: 在 start 流程生成 nonce，并把 nonce 绑定到 state。
- [ ] Step 4: 使用 JWKS 验证 ID Token 签名，并校验 issuer、audience、nonce、exp、iat 和 subject。
- [ ] Step 5: 身份结果以 issuer、subject、email、emailVerified 为主，不再只返回邮箱。
- [ ] Step 6: 运行测试并提交。

    mvn -q -Dtest=OidcAuthProviderTest,OidcIdTokenValidatorTest test
    git add src/main/java/com/brad/pms/auth src/test/java/com/brad/pms/auth
    git commit -m "fix: validate oidc tokens and nonce securely"

### Task 3: 增加 PMS 外部身份映射和用户迁移

Files:

- Create: src/main/resources/db/migration/V45__pms_external_identity.sql
- Create: src/main/java/com/brad/pms/entity/PmsExternalIdentityDO.java
- Create: src/main/java/com/brad/pms/mapper/PmsExternalIdentityMapper.java
- Create: src/main/java/com/brad/pms/service/PmsExternalIdentityService.java
- Create: src/main/java/com/brad/pms/service/PmsIdentityBackfillService.java
- Create: src/main/java/com/brad/pms/controller/AdminSsoProvisioningController.java
- Test: src/test/java/com/brad/pms/service/PmsExternalIdentityServiceTest.java
- Test: src/test/java/com/brad/pms/service/PmsIdentityBackfillServiceTest.java

Interfaces:

- PmsExternalIdentityService.findByIssuerAndSubject(issuer, subject) 返回唯一 PMS user。
- PmsExternalIdentityService.link(userId, issuer, subject, emailSnapshot) 只允许幂等一对一绑定。
- PmsIdentityBackfillService.preview() 返回 matched、missingEmail、duplicateEmail、disabled 和 alreadyLinked 统计。
- PmsIdentityBackfillService.apply() 只应用无歧义映射。

- [ ] Step 1: 写 migration 和映射测试。相同 issuer + subject、同一 PMS 用户重复绑定、多个 PMS 用户绑定同一外部身份都必须被拒绝。
- [ ] Step 2: 运行测试，预期失败。
- [ ] Step 3: 创建 pms_external_identity，唯一索引为 issuer + subject 和 pms_user_id + issuer，不创建重复的 identity_user 双向外键。
- [ ] Step 4: OIDC 登录优先按 issuer + subject 匹配；首次迁移才允许使用 verified email，匹配不唯一必须转人工处理。
- [ ] Step 5: 增加管理员 preview/apply/report，不返回密码和令牌。
- [ ] Step 6: 运行测试并提交。

    mvn -q -Dtest=PmsExternalIdentityServiceTest,PmsIdentityBackfillServiceTest test
    git add src/main/resources/db/migration/V45__pms_external_identity.sql src/main/java/com/brad/pms/entity/PmsExternalIdentityDO.java src/main/java/com/brad/pms/mapper/PmsExternalIdentityMapper.java src/main/java/com/brad/pms/service src/main/java/com/brad/pms/controller/AdminSsoProvisioningController.java src/test/java/com/brad/pms/service
    git commit -m "feat: map external identities to pms users"

### Task 4: 让 PMS API 成为 OIDC Resource Server

Files:

- Modify: src/main/java/com/brad/pms/security/AuthInterceptor.java
- Modify: src/main/java/com/brad/pms/security/JwtTokenProvider.java
- Create: src/main/java/com/brad/pms/security/OidcAccessTokenValidator.java
- Create: src/main/java/com/brad/pms/security/SsoSessionStatusService.java
- Modify: src/main/resources/application.yml
- Test: src/test/java/com/brad/pms/security/OidcAccessTokenValidatorTest.java
- Test: src/test/java/com/brad/pms/security/AuthInterceptorTest.java

Interfaces:

- OidcAccessTokenValidator.validate(authorizationHeader) 返回 user identity、issuer、subject、audience、scope 和 session ID。
- SsoSessionStatusService.isActive(sessionId) 在需要即时退出的 API 上检查中央会话状态。

- [ ] Step 1: 写 audience、scope、issuer、签名、exp 和撤销 session 测试。
- [ ] Step 2: 运行测试，预期当前 PMS JWT 逻辑无法满足 audience 和外部 JWKS 校验。
- [ ] Step 3: 支持外部身份中心的 JWKS 验证，使用 RS256 或 ES256，不在多个服务之间共享 HS256 secret。
- [ ] Step 4: 对 pms-api 资源接口要求 audience=pms-api，并按 scope 和 PMS 业务权限双重判断。
- [ ] Step 5: 在需要立即撤销的接口检查 session 状态；普通查询依靠短期 Access Token。
- [ ] Step 6: 运行测试并提交。

    mvn -q -Dtest=OidcAccessTokenValidatorTest,AuthInterceptorTest test
    git add src/main/java/com/brad/pms/security src/main/resources/application.yml src/test/java/com/brad/pms/security
    git commit -m "feat: validate external oidc access tokens for pms api"

### Task 5: 接入 PMS 前端

Files:

- Modify: pms-front/src/auth/sso.ts
- Modify: pms-front/src/views/login/index.vue
- Modify: pms-front/src/views/login/oidc-callback.vue
- Modify: pms-front/src/api/auth.ts
- Modify: pms-front/src/router/index.ts
- Modify: pms-front/src/router/admin-guard.ts
- Test: pms-front/src/auth/sso.test.mjs

- [ ] Step 1: 写 state、nonce、PKCE、callback 重放、logout 和错误回调测试。
- [ ] Step 2: 实现 pms-web Authorization Code + PKCE。浏览器不保存 client secret 和 Refresh Token。
- [ ] Step 3: PMS 后端回调校验后创建 HttpOnly PMS session，前端只调用 /auth/me 和 /auth/refresh。
- [ ] Step 4: 统一退出时跳转身份中心 logout，并清理 PMS 本地 session。
- [ ] Step 5: 运行测试和构建。

    npm run test -- src/auth/sso.test.mjs
    npm run build

- [ ] Step 6: 提交。

    git add src/auth/sso.ts src/views/login src/api/auth.ts src/router src/auth/sso.test.mjs
    git commit -m "feat: connect pms frontend to external sso"

### Task 6: 接入 DSH Web 登录和 PMS API 用户授权

Files:

- Create: deepseek-harness/packages/integrations/pms-sso/src/index.ts
- Create: deepseek-harness/packages/integrations/pms-sso/src/types.ts
- Create: deepseek-harness/packages/integrations/pms-sso/tests/pkce.spec.ts
- Modify: deepseek-harness/apps/cli/src/bin.ts
- Modify: deepseek-harness/apps/web/src/main.ts
- Modify: deepseek-harness/apps/cli/tests/web-auth.e2e.ts
- Create: deepseek-harness/apps/cli/tests/pms-sso.e2e.ts

Interfaces:

- createPmsAuthorizationUrl(options) 返回 url、state、verifier 和 nonce。
- exchangePmsCode(input) 返回 issuer、subject、PMS user ID 和 DSH session expiry。
- createDshSession(identity) 创建现有 DSH HttpOnly web session。
- getPmsApiToken(userSession) 返回 audience=pms-api 的短期访问令牌。

- [ ] Step 1: 写 DSH state、PKCE、callback、Cookie 重启和 PMS API audience 测试。
- [ ] Step 2: DSH 使用 dsh-web 客户端完成服务端回调，浏览器只保存 DSH session cookie。
- [ ] Step 3: DSH 后端按需获取 PMS API 访问令牌，不使用旧的 PMS_DSH_SERVICE_KEY 冒充用户身份；该 service key 仅保留给真正的系统任务。
- [ ] Step 4: DSH logout 清理自身 session，并调用身份中心 RP-Initiated Logout。
- [ ] Step 5: 运行测试。

    pnpm exec vitest run packages/integrations/pms-sso/tests/pkce.spec.ts apps/cli/tests/web-auth.e2e.ts apps/cli/tests/pms-sso.e2e.ts

- [ ] Step 6: 提交。

    git add packages/integrations/pms-sso apps/cli/src/bin.ts apps/web/src/main.ts apps/cli/tests/web-auth.e2e.ts apps/cli/tests/pms-sso.e2e.ts
    git commit -m "feat: connect dsh web to external sso"

### Task 7: 实现单点退出、会话撤销和应急登录

Files:

- Create: src/main/java/com/brad/pms/service/SsoLogoutService.java
- Create: src/main/java/com/brad/pms/service/SsoAuditService.java
- Create: src/main/resources/db/migration/V46__sso_audit_and_session_indexes.sql
- Modify: src/main/java/com/brad/pms/controller/AuthController.java
- Modify: src/main/resources/application.yml
- Test: src/test/java/com/brad/pms/service/SsoLogoutServiceTest.java
- Test: src/test/java/com/brad/pms/service/SsoAuditServiceTest.java

- [ ] Step 1: 写退出测试。PMS 退出后本地 session 失效；DSH 退出后 DSH session 失效；重复退出幂等；密码修改和账号禁用撤销全部应用会话。
- [ ] Step 2: 实现 RP-Initiated Logout、PMS 本地 session 清理、认证审计和 session status 检查。
- [ ] Step 3: 配置本地密码登录为迁移期和 break-glass 能力，并增加管理员权限、审计和限流。
- [ ] Step 4: 运行测试并提交。

    mvn -q -Dtest=SsoLogoutServiceTest,SsoAuditServiceTest test
    git add src/main/java/com/brad/pms/service src/main/java/com/brad/pms/controller/AuthController.java src/main/resources src/test/java/com/brad/pms/service
    git commit -m "feat: add sso logout audit and break glass policy"

### Task 8: 管理配置和端到端验证

Files:

- Create: src/main/java/com/brad/pms/controller/AdminSsoConfigurationController.java
- Create: src/main/java/com/brad/pms/dto/response/SsoRuntimeStatusDTO.java
- Create: pms-front/src/views/admin/sso/index.vue
- Create: pms-front/src/api/admin-sso.ts
- Test: src/test/java/com/brad/pms/controller/AdminSsoConfigurationControllerTest.java
- Test: pms-front/src/views/admin/sso/sso-admin.test.mjs
- Create: deepseek-harness/apps/cli/tests/pms-sso-cross-app.e2e.ts

Management UI scope:

- 只展示 issuer、客户端状态、回调地址、JWKS 状态、最近同步时间、会话统计和测试连接结果。
- 不在 PMS UI 中编辑身份中心密码、MFA 或原始令牌。
- client secret 只允许通过环境变量或密钥管理系统注入，页面不得回显。

- [ ] Step 1: 写管理接口和前端测试，验证 secret 脱敏、权限控制和连接测试错误提示。
- [ ] Step 2: 实现只读状态和安全配置入口，真实 client 注册仍在身份中心管理。
- [ ] Step 3: 写端到端场景：PMS 登录后打开 DSH、DSH 调用 PMS API、双标签刷新、并发刷新、退出、禁用用户、授权码重放和错误 audience。
- [ ] Step 4: 运行全部重点验证。

    mvn -q -Dtest=OidcAuthProviderTest,OidcIdTokenValidatorTest,OidcAccessTokenValidatorTest,SsoLogoutServiceTest,EnterpriseSsoE2eTest test
    npm run test -- src/auth/sso.test.mjs src/views/admin/sso/sso-admin.test.mjs
    npm run build
    pnpm exec vitest run apps/cli/tests/pms-sso-cross-app.e2e.ts
    curl -fsS http://127.0.0.1:8080/api/health/ready
    git diff --check

- [ ] Step 5: 手动验证 PMS、DSH、DSH 内嵌 PMS、刷新、退出和重新登录。
- [ ] Step 6: 更新 README，记录 issuer、客户端、回调地址、密钥轮换、回滚和本地启动命令。
- [ ] Step 7: 提交。

    git add src/main/java/com/brad/pms/controller/AdminSsoConfigurationController.java src/main/java/com/brad/pms/dto/response/SsoRuntimeStatusDTO.java src/test/java/com/brad/pms/controller/AdminSsoConfigurationControllerTest.java README.md
    git commit -m "test: verify external sso across pms and dsh"

## 方案自审

### 是否需要新增身份认证中心前后端

需要新增一个独立的身份能力，但不建议自行实现完整认证核心。

推荐方式：

- 部署成熟的 OIDC 身份中心，身份中心自带管理后台和认证后端。
- PMS 新增 OIDC 接入、外部身份映射、API 令牌校验和管理状态页面。
- DSH 新增 OIDC 登录、自己的 Web Session 和 PMS API 令牌获取。

如果未来必须使用自研身份中心，可以另立一个 auth-center 项目，包含认证后端和管理前端，但这应是独立项目，不应直接塞进 PMS。它需要独立数据库、密钥轮换、MFA、密码策略、会话管理、审计和高可用设计。

### 回滚

    pms.auth.local-enabled=true
    pms.sso.enabled=false
    dsh.pms.sso.enabled=false

回滚只停止新的 SSO 登录，不删除外部身份映射和审计记录。普通用户关闭本地密码登录前，必须先验证 break-glass 管理员入口。
