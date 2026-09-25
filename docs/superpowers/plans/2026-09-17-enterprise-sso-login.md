# Enterprise SSO Login Implementation Plan

> 本计划已被修订版替代，请勿按本文件直接实施。新计划：docs/superpowers/plans/2026-09-17-enterprise-sso-login-v2.md

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

Goal: 在不复制 PMS 用户密码数据的前提下，为 PMS、DSH 和未来系统建立基于 OAuth2/OIDC 的统一登录与单点退出能力。

Architecture: 第一阶段以 PMS 后端为认证中心，复用现有 sys_user、sys_auth_session、登录锁定和组织权限数据；新增 identity 映射和 OAuth/OIDC 持久化边界。PMS 与 DSH 都使用 Authorization Code + PKCE，应用各自维护 HttpOnly 会话，PMS API 继续负责业务授权。未来系统稳定接入后，再将认证模块独立部署为 auth-center。

Tech Stack: Spring Boot、MySQL、Flyway、MyBatis、现有 PMS JWT/Session、OAuth2 Authorization Code、OIDC、PKCE、DSH web session cookie、PMS 前端 Vue、DSH TypeScript monorepo。

Spec: docs/superpowers/specs/2026-09-17-enterprise-sso-login.md

## Global Constraints

- 不新建第二套密码库；现有 PMS 用户密码只保留一份。
- Authorization Code + PKCE 是浏览器和 DSH 的唯一登录协议。
- Access Token 默认 10 分钟，Refresh Token 默认 30 天，授权码默认 60 秒且只能消费一次。
- 所有令牌、授权码和 PKCE 状态不得写入 URL 日志、聊天记录或 localStorage。
- PMS 业务权限仍由 PMS 的组织、角色、项目成员和权限代码判断。
- 迁移必须兼容现有 PMS /auth/login、/auth/refresh、/auth/logout 接口。
- 每个任务必须先添加失败测试，再实现最小代码，最后运行任务级测试。
- 不修改与登录无关的既有业务改动。

---

### Task 1: 固化统一登录协议和客户端配置

Files:

- Modify: src/main/java/com/brad/pms/auth/AuthProviderProperties.java
- Modify: src/main/resources/application.yml
- Modify: src/main/resources/application-mysql.yml
- Create: src/main/java/com/brad/pms/auth/SsoClientRegistration.java
- Create: src/main/java/com/brad/pms/auth/SsoProtocolProperties.java
- Test: src/test/java/com/brad/pms/auth/SsoProtocolPropertiesTest.java

Interfaces:

- SsoProtocolProperties 提供 issuer、authorization endpoint、token endpoint、userinfo endpoint、jwks endpoint、令牌 TTL 和 Cookie 设置。
- SsoClientRegistration 提供 client ID、精确 redirect URI、允许的 scopes 和 PKCE 策略。
- 初始客户端固定为 pms-web 和 dsh-web。

- [ ] Step 1: 写失败测试。验证默认 Access Token TTL 为 600 秒、Refresh Token TTL 为 30 天、授权码 TTL 为 60 秒，两个客户端都必须使用 S256 PKCE。
- [ ] Step 2: 运行测试。

    mvn -q -Dtest=SsoProtocolPropertiesTest test

    预期：失败，因为配置类型尚未存在。

- [ ] Step 3: 增加 pms.sso 配置绑定。启用客户端缺少 client ID、redirect URI 或 issuer 时，启动必须失败。
- [ ] Step 4: 再次运行同一测试，预期通过。
- [ ] Step 5: 提交。

    git add src/main/java/com/brad/pms/auth src/main/resources/application.yml src/main/resources/application-mysql.yml src/test/java/com/brad/pms/auth/SsoProtocolPropertiesTest.java
    git commit -m "feat: define enterprise sso protocol configuration"

### Task 2: 增加统一身份和 OAuth 数据表

Files:

- Create: src/main/resources/db/migration/V45__enterprise_sso_identity_and_oauth.sql
- Create: src/main/java/com/brad/pms/entity/IdentityUserDO.java
- Create: src/main/java/com/brad/pms/entity/IdentityLoginIdentifierDO.java
- Create: src/main/java/com/brad/pms/entity/OAuthAuthorizationCodeDO.java
- Create: src/main/java/com/brad/pms/entity/OAuthRefreshTokenDO.java
- Create: src/main/java/com/brad/pms/entity/OAuthClientDO.java
- Create: src/main/java/com/brad/pms/entity/IdentitySessionDO.java
- Create: src/main/java/com/brad/pms/mapper/IdentityUserMapper.java
- Create: src/main/java/com/brad/pms/mapper/OAuthAuthorizationCodeMapper.java
- Create: src/main/java/com/brad/pms/mapper/OAuthRefreshTokenMapper.java
- Create: src/main/java/com/brad/pms/mapper/OAuthClientMapper.java
- Create: src/main/java/com/brad/pms/mapper/IdentitySessionMapper.java
- Test: src/test/java/com/brad/pms/migration/EnterpriseSsoMigrationTest.java

Interfaces:

- identity_user.id 是稳定身份 ID，identity_user.pms_user_id 映射现有 sys_user.id。
- identity_login_identifier 保存规范化邮箱、用户名或外部 provider subject。
- oauth_authorization_code 只保存 code_hash、客户端、用户、redirect URI、PKCE challenge、nonce、scope、expiry 和 used_at。
- oauth_refresh_token 只保存 token_hash、token family、客户端、用户、scope、expiry、revoked_at 和 rotated_to_hash。
- identity_session 保存统一会话，不保存任何原始令牌。

- [ ] Step 1: 写迁移断言。验证唯一索引覆盖登录标识、授权码哈希和刷新令牌哈希，并确认没有明文凭据字段。
- [ ] Step 2: 运行迁移测试。

    mvn -q -Dtest=EnterpriseSsoMigrationTest test

    预期：失败，因为 V45 尚未存在。

- [ ] Step 3: 以增量方式创建 V45。给 sys_user 增加可为空的 identity_user_id，不删除现有认证表。
- [ ] Step 4: 实现 mapper 的插入、按哈希查询、条件消费、条件轮换、撤销和按用户查询方法。消费和轮换 SQL 必须包含未使用、未撤销、未过期条件。
- [ ] Step 5: 运行迁移和 mapper 测试，预期通过。
- [ ] Step 6: 提交。

    git add src/main/resources/db/migration/V45__enterprise_sso_identity_and_oauth.sql src/main/java/com/brad/pms/entity src/main/java/com/brad/pms/mapper src/test/java/com/brad/pms/migration/EnterpriseSsoMigrationTest.java
    git commit -m "feat: add enterprise sso identity persistence"

### Task 3: 实现 PMS 授权服务器核心

Files:

- Create: src/main/java/com/brad/pms/sso/SsoIdentityService.java
- Create: src/main/java/com/brad/pms/sso/SsoAuthorizationService.java
- Create: src/main/java/com/brad/pms/sso/SsoTokenService.java
- Create: src/main/java/com/brad/pms/sso/SsoSessionService.java
- Create: src/main/java/com/brad/pms/sso/SsoClaimsService.java
- Create: src/main/java/com/brad/pms/dto/request/SsoAuthorizeRequest.java
- Create: src/main/java/com/brad/pms/dto/request/SsoTokenRequest.java
- Create: src/main/java/com/brad/pms/dto/response/SsoTokenResponse.java
- Create: src/main/java/com/brad/pms/dto/response/SsoUserInfoResponse.java
- Test: src/test/java/com/brad/pms/sso/SsoAuthorizationServiceTest.java
- Test: src/test/java/com/brad/pms/sso/SsoTokenServiceTest.java
- Test: src/test/java/com/brad/pms/sso/SsoSessionServiceTest.java

Interfaces:

- SsoAuthorizationService.authorize(userId, clientId, redirectUri, scope, state, nonce, codeChallenge) 返回一次性授权码重定向。
- SsoTokenService.exchangeAuthorizationCode(code, clientId, redirectUri, codeVerifier) 返回 access token、refresh token、ID token 和 expiry。
- SsoTokenService.refresh(refreshToken, clientId) 原子轮换刷新令牌并保留统一 session ID。
- SsoSessionService.revoke(sessionId, reason) 撤销中央会话及关联令牌。

- [ ] Step 1: 写授权码测试：一次消费成功、二次消费失败、错误 client 失败、错误 redirect URI 失败、过期失败、错误 verifier 失败。
- [ ] Step 2: 运行测试。

    mvn -q -Dtest=SsoAuthorizationServiceTest,SsoTokenServiceTest,SsoSessionServiceTest test

    预期：失败，因为服务类尚未存在。

- [ ] Step 3: 生成 32 字节随机授权码，只保存 SHA-256 哈希；使用条件更新保证授权码只能被一个请求消费。
- [ ] Step 4: 只接受 S256 PKCE，使用常量时间比较验证 challenge；ID Token 必须包含 nonce，Access Token 必须包含 sub、iss、aud、scope、sid、iat 和 exp。
- [ ] Step 5: 实现刷新令牌轮换。并发刷新时只能有一个请求成功，不得像旧实现一样通过删除旧 session 造成 PMS 和 DSH 互相踢下线。
- [ ] Step 6: 运行同一组测试，预期通过。
- [ ] Step 7: 提交。

    git add src/main/java/com/brad/pms/sso src/main/java/com/brad/pms/dto src/test/java/com/brad/pms/sso
    git commit -m "feat: implement sso authorization and token services"

### Task 4: 暴露 OIDC 发现、授权、令牌、用户信息和退出接口

Files:

- Create: src/main/java/com/brad/pms/controller/SsoController.java
- Create: src/main/java/com/brad/pms/controller/SsoDiscoveryController.java
- Create: src/main/java/com/brad/pms/dto/response/OidcDiscoveryResponse.java
- Create: src/main/java/com/brad/pms/dto/response/JwksResponse.java
- Modify: src/main/java/com/brad/pms/security/AuthInterceptor.java
- Modify: src/main/java/com/brad/pms/controller/AuthController.java
- Test: src/test/java/com/brad/pms/controller/SsoControllerTest.java
- Test: src/test/java/com/brad/pms/controller/SsoDiscoveryControllerTest.java

Endpoints:

- GET /oauth2/.well-known/openid-configuration
- GET /oauth2/jwks
- GET /oauth2/authorize
- POST /oauth2/token
- GET /oauth2/userinfo
- POST /oauth2/revoke
- GET /oauth2/logout

- [ ] Step 1: 写接口契约测试，覆盖 discovery、精确 redirect URI、response_type=code、S256 PKCE、token response、userinfo、revoke 和 logout。
- [ ] Step 2: 运行接口测试。

    mvn -q -Dtest=SsoControllerTest,SsoDiscoveryControllerTest test

    预期：失败，因为接口尚未存在。

- [ ] Step 3: 实现 discovery 和 JWKS，只公开签名公钥，不公开 client secret、密码和令牌哈希。
- [ ] Step 4: 实现授权和令牌接口，使用标准 OAuth 错误码 invalid_request、invalid_client、invalid_grant、invalid_scope 和 unauthorized_client。
- [ ] Step 5: 实现 userinfo、revoke 和 logout；logout 必须校验 post_logout_redirect_uri，并终止统一身份会话。
- [ ] Step 6: 运行接口测试，预期通过。
- [ ] Step 7: 提交。

    git add src/main/java/com/brad/pms/controller src/main/java/com/brad/pms/security src/main/java/com/brad/pms/dto/response src/test/java/com/brad/pms/controller/SsoControllerTest.java src/test/java/com/brad/pms/controller/SsoDiscoveryControllerTest.java
    git commit -m "feat: expose oidc sso endpoints"

### Task 5: 迁移 PMS 现有登录并保持兼容

Files:

- Modify: src/main/java/com/brad/pms/service/AuthService.java
- Modify: src/main/java/com/brad/pms/controller/AuthController.java
- Modify: src/main/java/com/brad/pms/security/JwtTokenProvider.java
- Modify: src/main/java/com/brad/pms/entity/UserDO.java
- Modify: src/main/java/com/brad/pms/mapper/UserMapper.java
- Test: src/test/java/com/brad/pms/service/AuthServiceTest.java
- Test: src/test/java/com/brad/pms/controller/AuthControllerTest.java

- [ ] Step 1: 写兼容测试：旧 PMS 用户可以账号密码登录；OIDC 用户映射到同一个 PMS 用户；未开通用户被拒绝；禁用用户两种方式都被拒绝。
- [ ] Step 2: 运行测试。

    mvn -q -Dtest=AuthServiceTest,AuthControllerTest test

    预期：失败，因为 identity 映射还没有接入。

- [ ] Step 3: 本地登录成功时创建缺失的 identity 映射；OIDC 登录使用 verified email 或 provider subject 匹配，发现重复匹配必须报错，不能随机选择。
- [ ] Step 4: 保留现有 /auth/login、/auth/refresh、/auth/logout 和 HttpOnly pms_refresh_token Cookie；新令牌携带统一 identity_user_id 和 session ID。
- [ ] Step 5: 运行测试并提交。

    mvn -q -Dtest=AuthServiceTest,AuthControllerTest test
    git add src/main/java/com/brad/pms/service/AuthService.java src/main/java/com/brad/pms/controller/AuthController.java src/main/java/com/brad/pms/security/JwtTokenProvider.java src/main/java/com/brad/pms/entity/UserDO.java src/main/java/com/brad/pms/mapper/UserMapper.java src/test/java/com/brad/pms/service/AuthServiceTest.java src/test/java/com/brad/pms/controller/AuthControllerTest.java
    git commit -m "feat: bridge existing pms users into sso identities"

### Task 6: 接入 PMS 前端

Files:

- Modify: pms-front/src/auth/sso.ts
- Modify: pms-front/src/views/login/index.vue
- Modify: pms-front/src/views/login/oidc-callback.vue
- Modify: pms-front/src/api/auth.ts
- Modify: pms-front/src/router/index.ts
- Modify: pms-front/src/router/admin-guard.ts
- Test: pms-front/src/auth/sso.test.mjs
- Test: pms-front/src/integration/dsh-auth-bridge.test.mjs

Interfaces:

- startSsoLogin(returnPath) 生成 state、nonce、verifier 并写入短期 sessionStorage。
- completeSsoLogin(query) 验证 state 后通过 PMS 后端交换授权码。
- logoutEverywhere() 调用中央退出接口并清理本地会话。

- [ ] Step 1: 写 state 不一致、缺 code、成功 callback、callback 重放、local fallback 和 logout 测试。
- [ ] Step 2: 运行前端测试。

    npm run test -- src/auth/sso.test.mjs src/integration/dsh-auth-bridge.test.mjs

    预期：失败，因为新的 callback 逻辑尚未存在。

- [ ] Step 3: 登录时跳转 /oauth2/authorize，参数包含 client_id、response_type、redirect_uri、scope、state、nonce、code_challenge 和 code_challenge_method=S256。
- [ ] Step 4: 启动时通过 HttpOnly Cookie 调用 /auth/refresh；禁止将 Refresh Token 放入 Pinia、localStorage、URL 或 DSH 上下文。
- [ ] Step 5: logout 调用中央 logout、清空前端状态、关闭 DSH bridge 状态并回到 /login。
- [ ] Step 6: 运行测试和构建。

    npm run test -- src/auth/sso.test.mjs src/integration/dsh-auth-bridge.test.mjs
    npm run build

- [ ] Step 7: 提交。

    git add src/auth/sso.ts src/views/login src/api/auth.ts src/router src/integration/dsh-auth-bridge.test.mjs
    git commit -m "feat: connect pms frontend to unified sso"

### Task 7: 接入 DSH Web 登录

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
- exchangePmsCode(input) 返回 PMS identity_user_id、display name 和过期时间。
- createDshSession(identity) 创建现有 DSH HttpOnly web session。
- logoutPmsSession(sessionId) 调用 PMS revoke/logout。

- [ ] Step 1: 写 DSH PKCE、state、callback、Cookie 持久化和 DSH 重启测试。
- [ ] Step 2: 运行测试。

    pnpm exec vitest run packages/integrations/pms-sso/tests/pkce.spec.ts apps/cli/tests/web-auth.e2e.ts

    预期：失败，因为 PMS SSO integration package 和 callback route 尚未存在。

- [ ] Step 3: 添加 DSH 服务端 callback。浏览器只拿到 DSH session cookie，PMS client secret 和 refresh token 只在服务端处理。
- [ ] Step 4: 在 DSH session 中保存 identity_user_id、display name、session expiry 和 DSH session ID；调用 PMS API 时按需获取短期 audience=pms-api 的令牌。
- [ ] Step 5: 实现 DSH logout 的 PMS 撤销、DSH Cookie 清理和 post-logout redirect。
- [ ] Step 6: 运行测试。

    pnpm exec vitest run packages/integrations/pms-sso/tests/pkce.spec.ts apps/cli/tests/web-auth.e2e.ts apps/cli/tests/pms-sso.e2e.ts

- [ ] Step 7: 提交。

    git add packages/integrations/pms-sso apps/cli/src/bin.ts apps/web/src/main.ts apps/cli/tests/web-auth.e2e.ts apps/cli/tests/pms-sso.e2e.ts
    git commit -m "feat: integrate dsh web login with pms sso"

### Task 8: 用户迁移、单点退出和端到端验证

Files:

- Create: src/main/java/com/brad/pms/service/SsoIdentityBackfillService.java
- Create: src/main/java/com/brad/pms/controller/AdminSsoProvisioningController.java
- Create: src/main/java/com/brad/pms/service/SsoAuditService.java
- Create: src/main/resources/db/migration/V46__sso_audit_and_session_indexes.sql
- Test: src/test/java/com/brad/pms/service/SsoIdentityBackfillServiceTest.java
- Test: src/test/java/com/brad/pms/sso/SsoLogoutServiceTest.java
- Test: src/test/java/com/brad/pms/e2e/EnterpriseSsoE2eTest.java
- Create: pms-front/tests/sso.e2e.mjs
- Create: deepseek-harness/apps/cli/tests/pms-sso-cross-app.e2e.ts
- Modify: README.md

- [ ] Step 1: 先写用户预览和迁移测试。按规范化 verified email 一对一匹配；重复邮箱、无邮箱和禁用用户必须分别报告；重复执行必须幂等。
- [ ] Step 2: 实现管理员 preview/apply/report 接口，只允许 sso:provision 权限，所有映射写审计。
- [ ] Step 3: 实现统一退出。退出时撤销中央 session 和关联 refresh token family；密码修改、账号禁用也必须撤销全部会话。
- [ ] Step 4: 写端到端场景：PMS 登录后打开 DSH 不重复登录；DSH 调用 PMS 查询；两个浏览器标签同时刷新；并发 refresh 只有一个成功；任意一侧退出后另一侧失效；授权码重放失败。
- [ ] Step 5: 运行完整重点验证。

    mvn -q -Dtest=EnterpriseSsoE2eTest,AuthServiceTest,SsoAuthorizationServiceTest,SsoTokenServiceTest,SsoControllerTest test
    npm run test -- tests/sso.e2e.mjs
    npm run build
    pnpm exec vitest run apps/cli/tests/pms-sso-cross-app.e2e.ts
    curl -fsS http://127.0.0.1:8080/api/health/ready
    git diff --check

- [ ] Step 6: 手工验证 PMS 和 DSH 独立标签页、DSH 内嵌 PMS、刷新、退出和重新登录。
- [ ] Step 7: 更新 README，记录 issuer、client、redirect URI、密钥轮换、数据库备份、回滚开关和本地启动命令。
- [ ] Step 8: 提交。

    git add src/main/java/com/brad/pms/service src/main/java/com/brad/pms/controller src/main/resources/db/migration src/test/java/com/brad/pms pms-front/tests/sso.e2e.mjs deepseek-harness/apps/cli/tests/pms-sso-cross-app.e2e.ts README.md
    git commit -m "test: verify enterprise sso end to end"

## 方案自审

### 为什么第一阶段不单独做 auth-center

现在 PMS 已经有用户、密码、组织、角色、会话和登录审计。立即新建 auth-center 会产生两套用户主数据、同步失败、密码迁移和权限映射问题。先在 PMS 中建立清晰的 sso 模块和独立数据表，可以得到标准 OIDC 接口，同时避免复制账号。DSH 和其他系统稳定接入后，再将该模块整体拆成 auth-center。

### 数据最终归属

- 统一身份中心：用户身份、登录标识、组织归属、OAuth 客户端、会话、令牌和认证审计。
- PMS：项目、任务、节点、项目成员、业务角色和项目权限。
- DSH：自己的 Web session、工作区偏好和 AI 会话，不保存 PMS 密码。

### 回滚开关

    pms.auth.local-enabled=true
    pms.sso.enabled=false
    dsh.pms.sso.enabled=false

回滚不删除 identity 映射和 OAuth 记录，只停止发放新的 SSO 会话，保留审计和现有 PMS 本地登录。
