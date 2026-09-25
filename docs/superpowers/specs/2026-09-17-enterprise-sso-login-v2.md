# PMS 与 DSH 企业级 SSO 登录规格 v2

## 目标

让 PMS、DSH 和未来业务系统使用同一个企业身份。用户只登录一次，就可以访问所有已接入系统。

## 核心架构

身份认证核心由独立的 OIDC 身份中心提供。身份中心负责密码、MFA、统一会话、OAuth 客户端、令牌签发、密钥轮换和单点退出。

PMS 和 DSH 都是身份中心的 OIDC Client。PMS 同时是 PMS API 的 Resource Server，继续负责项目、任务、节点和组织权限。

PMS 不再实现自己的 OAuth 授权服务器，不保存身份中心的密码、授权码和 Refresh Token。

## 为什么要独立身份中心

当前 PMS 已经有 OIDC Client 能力，说明 PMS 当前职责是接入外部身份系统，而不是充当身份系统本身。

如果让 PMS 自己实现完整 OAuth/OIDC 服务，必须自行维护密码安全、MFA、授权码、令牌轮换、签名密钥、发现文档、注销协议和高可用状态，风险大于收益。

第一版应使用企业已有的身份系统，或者部署成熟的 OIDC 身份中心。PMS 和 DSH 只实现接入和业务身份映射。

## 数据归属

### 身份中心数据

- 用户账号和密码
- MFA 和登录策略
- 用户组织关系
- OAuth Client 注册信息
- 统一登录会话
- Access Token、Refresh Token 和授权码
- 签名密钥和 JWKS
- 登录、退出和安全审计

### PMS 数据库

- sys_user 和 PMS 展示资料
- pms_external_identity 身份映射
- PMS 角色、组织权限和项目权限
- 项目、任务、节点和业务操作日志

### DSH 数据

- DSH 自己的 HttpOnly Web Session
- DSH 工作区、Agent 和会话数据
- 不保存 PMS 密码
- 不在浏览器 localStorage 保存 PMS Refresh Token

## 身份映射

PMS 使用以下组合识别外部身份：

    issuer + subject

邮箱只用于首次绑定和人工辅助匹配，不能作为永久主键。

建议新增表：

    pms_external_identity

字段：

    id
    pms_user_id
    issuer
    subject
    email_snapshot
    provider_type
    created_at
    updated_at

唯一约束：

    unique(issuer, subject)
    unique(pms_user_id, issuer)

不同时在 sys_user 和 identity_user 中保存双向外键，避免映射漂移。

## 登录流程

1. 用户访问 PMS 或 DSH。
2. 应用发现没有本地会话，跳转身份中心 authorization endpoint。
3. 身份中心完成登录、MFA 和统一会话建立。
4. 身份中心返回一次性 authorization code。
5. 应用后端使用 code、redirect_uri 和 PKCE verifier 换取令牌。
6. 应用创建自己的 HttpOnly Session。
7. PMS 根据 issuer 和 subject 映射到 PMS 用户。
8. DSH 调用 PMS API 时使用 audience 为 pms-api 的用户访问令牌。

## 客户端划分

- pms-web：PMS 浏览器客户端，使用 Authorization Code + PKCE，不保存 client secret。
- dsh-web：DSH 服务端客户端，client secret 只保存在 DSH 服务端。
- pms-api：资源服务 audience，不是浏览器客户端。

## 令牌和退出

- Access Token 有效期 5 到 10 分钟。
- Refresh Token 由身份中心保存和轮换。
- PMS 和 DSH 只保存自己的应用会话。
- 统一退出使用 RP-Initiated Logout。
- PMS 和 DSH 同时清理本地 Session。
- 对于已经签发的 JWT，短期内不能依靠数据库撤销立即失效；如果需要立即失效，PMS API 必须增加 session 状态检查或改用 introspection。

## PMS OIDC 安全要求

PMS 必须验证：

- ID Token 签名
- issuer
- audience
- nonce
- exp 和 iat
- access token audience
- JWKS 密钥轮换

不能只解码 ID Token Payload，也不能只依赖邮箱。

## 本地登录策略

迁移期间保留 PMS 本地账号登录，但只作为：

- 身份中心不可用时的应急入口
- 初始管理员恢复入口
- 本地开发入口

SSO 稳定运行后，普通用户关闭本地密码登录，保留受控的 break-glass 管理员流程。

## 验收标准

- PMS 登录后打开 DSH，不需要再次输入密码。
- DSH 登录后打开 PMS，不需要再次输入密码。
- PMS 和 DSH 映射到同一个 PMS 用户。
- DSH 调用 PMS API 时，PMS 继续执行项目权限判断。
- 两个浏览器标签刷新不会相互踢下线。
- 一侧退出后，另一侧本地会话被清理。
- 授权码重放、错误 nonce、错误 issuer、错误 audience 都不能建立会话。
- 身份中心不可用时，PMS 的 break-glass 策略可按配置工作。
