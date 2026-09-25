# PMS 与 DSH 企业级 SSO 登录规格

> 本版本已被修订版替代，请使用 docs/superpowers/specs/2026-09-17-enterprise-sso-login-v2.md。

## 目标

让 PMS、DSH 以及未来的业务系统共享同一个企业身份，而不是各自维护一套账号密码。用户只需要在统一身份中心登录一次，随后可以直接访问 PMS、DSH 和其他已接入系统。

## 本期范围

本期只处理认证和会话，不处理业务权限重构，不改变项目、任务、节点等 PMS 业务模型。

本期必须覆盖：

1. 统一登录入口。
2. PMS 本地账号与统一身份的映射。
3. DSH 和 PMS 的 OAuth2/OIDC 授权码登录。
4. Access Token、Refresh Token、授权码和登录会话的生命周期。
5. 单点退出。
6. 现有 PMS 登录接口的兼容迁移。
7. DSH 调用 PMS API 时的用户身份传递。

## 关键决策

- 不建立第二套用户密码库。
- 第一阶段复用 PMS 现有用户表和登录能力，新增 SSO 边界和 OAuth/OIDC 数据表。
- PMS 的用户 ID 作为迁移期间的业务身份主键；新增稳定的 identity_user_id 供未来拆分认证中心使用。
- 浏览器和 DSH 使用 Authorization Code + PKCE，不把 Access Token 放在 URL、localStorage 或聊天记录中。
- PMS API 只接受 audience 为 pms-api 的令牌，并继续执行 PMS 自己的角色、组织和项目权限检查。
- DSH 只保存自己的 HttpOnly 会话 Cookie；访问 PMS 时通过后端交换获得短期 PMS 令牌。
- 统一登录中心的密码、刷新令牌、授权码均不明文落库。

## 用户登录流程

1. 用户访问 DSH 或 PMS。
2. 应用发现本地没有有效会话，跳转统一授权端点。
3. 统一认证中心完成用户登录，创建统一登录会话。
4. 认证中心携带一次性 authorization code 重定向回应用。
5. 应用后端使用 code、client_id、redirect_uri 和 PKCE verifier 换取令牌。
6. 应用创建自己的 HttpOnly 会话，并将 user identity_user_id 写入会话。
7. 应用通过 userinfo 或本地映射获取用户展示信息。

## 数据边界

统一身份数据包括用户、登录标识、组织关系、OAuth 客户端、授权码、刷新令牌、统一登录会话和认证审计。

PMS 继续保存项目、任务、节点、项目成员、PMS 角色、PMS 权限和业务操作日志。

统一身份中心只回答“是谁”，PMS 继续回答“能不能操作这个项目”。

## 安全要求

- Authorization Code 只能使用一次，过期时间 60 秒。
- state、nonce、PKCE verifier 必须绑定同一次登录尝试。
- Access Token 默认有效期 10 分钟。
- Refresh Token 默认有效期 30 天，并且每次刷新轮换。
- 授权码和 Refresh Token 只保存 SHA-256 哈希。
- 登录 Cookie 使用 HttpOnly、Secure、SameSite=Lax 或生产环境明确配置的 SameSite 策略。
- 生产环境所有回调地址必须使用 HTTPS，并且必须精确匹配已注册的 redirect URI。
- 密码继续使用 BCrypt/Argon2 等单向哈希，禁止回迁明文密码。
- 登出、密码修改、账号禁用时撤销对应会话。
- 认证失败、令牌交换失败和登出操作必须写入认证审计。

## 验收标准

- 用户在 PMS 登录后，打开 DSH 不再要求重复登录。
- 用户在 DSH 登录后，打开 PMS 不再要求重复登录。
- PMS 和 DSH 使用同一个 identity_user_id。
- 一个应用刷新令牌时，不会撤销另一个应用的登录会话。
- 用户退出统一登录后，PMS 和 DSH 的会话都失效。
- 旧的 PMS 用户可以继续使用原账号登录，并能够无感完成身份映射。
- 失效、过期、重放授权码都会返回明确错误，不会建立会话。
- DSH 通过 PMS API 查询项目时，PMS 仍按用户的组织和项目权限裁决。
