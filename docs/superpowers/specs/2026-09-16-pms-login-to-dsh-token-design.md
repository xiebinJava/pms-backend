# PMS 登录态自动换取 DSH 短期令牌设计

日期：2026-09-16

状态：待评审

## 1. 目标与范围

### 1.1 目标

让用户在 DSH 中打开 PMS 业务工作区后，DSH 的 `dsh-pms` Agent 能够自动使用当前 PMS 登录态访问 PMS 集成接口，完成项目、节点和任务查询，不再要求用户手动配置 `PMS_DSH_ACCESS_TOKEN`，也不把 PMS 用户 Token 或 DSH 服务密钥暴露给浏览器。

### 1.2 本次范围

本次只解决 PMS 登录态到 DSH 短期令牌的安全传递和续租基础能力：

- PMS 前端在 iframe 中响应 DSH 的认证请求。
- PMS 后端基于当前登录会话签发一次性授权码。
- DSH Host 使用服务密钥把一次性授权码兑换成短期 PMS 委托令牌。
- DSH 插件按 DSH 会话缓存和更新令牌。
- PMS 页面刷新、DSH 会话切换、PMS 登出和服务不可用时有明确行为。
- 保留现有静态短期令牌和服务端用户 Token 交换方式作为本地开发兜底。

本次不扩大 PMS 的业务权限，不新增项目或任务写操作，也不把完整页面数据自动注入对话；业务事实仍由 DSH Agent 按需调用 PMS 只读工具获取。

### 1.3 实施仓库边界

本功能只修改以下三个代码库：

- `pms-backend`：授权码存储、签发、兑换和令牌安全校验。
- `pms-front`：PMS iframe 的认证消息响应和登录态请求。
- `deepseek-harness`：DSH Client 桥接、Host Remote、授权码缓存和 PMS 插件兑换。

`work-helper` 不属于本功能的实现范围，也不再作为当前工作区的协作依赖；保留其本地仓库和历史代码，不删除、不启动、不修改。

`pms-front` 中现有的 `src/api/work-helper.ts`、旧 AI 抽屉和 Agent 配置调用属于历史兼容代码。本功能不调用这些接口，也不因为移出 `worker-helper` 而直接删除它们；待 DSH 主工作区完成替代验证后，另立“旧 Work Helper 入口下线”任务，逐项移除旧 UI、API 类型和环境变量，避免本次认证改造引入无关回归。

## 2. 当前问题与现有边界

当前 DSH 插件的令牌解析顺序是：

1. 使用配置中的短期 `accessToken`。
2. 没有短期令牌时，使用 DSH Host 进程中的 `pmsUserToken + serviceKey` 调用 `/integration/dsh/v1/token`。
3. 两者都没有时返回 `PMS_AUTH_REQUIRED`，用户看到的是 `PMS access token is not configured`。

这种设计适用于 CLI 或服务端预配置，但不适用于“用户在 PMS iframe 中已经登录”的浏览器场景，因为：

- PMS 用户 Token 只存在 PMS 前端模块内存，DSH Host 进程无法直接读取。
- DSH 服务密钥只能放在 DSH Host，不能通过 URL、`postMessage` 或前端配置发送给浏览器。
- 直接把 PMS 用户 Token 传给 DSH 会扩大长期凭证泄漏范围。
- 内存中的 DSH Token 在过期后没有可靠的浏览器续租通道。

因此新增一条浏览器认证桥接链路，同时保持原有服务端兜底链路不变。

## 3. 方案选择

### 3.1 方案 A：浏览器直接把 PMS 用户 Token 传给 DSH

不采用。虽然改动最少，但会把可代表用户访问 PMS 的凭证暴露给 DSH 前端、浏览器扩展、日志和消息调试工具，且无法清晰区分长期登录凭证与 Agent 权限。

### 3.2 方案 B：PMS 前端把登录态换成一次性授权码，DSH Host 服务端兑换短期令牌

采用。PMS 前端只拿到短期、单次使用的授权码；DSH 服务密钥只存在 Host 进程；PMS 后端仍以当前 PMS 登录会话确定用户身份和数据权限。该方案符合现有 iframe + `postMessage` + DSH Remote 架构，也能支持多 DSH 会话隔离。

### 3.3 方案 C：DSH Host 反向读取 PMS Cookie 或建立共享登录中心

暂不采用。它需要改变部署域名、Cookie 归属或引入新的统一身份服务，适合未来统一 SSO，不适合当前的本地 PMS iframe 集成。

## 4. 总体架构

```text
DSH Client 浏览器                         PMS iframe 浏览器
      │                                         │
      │  pms.dsh.auth.request                   │
      │ ───────────────────────────────────────>│
      │                                         │ 当前 PMS 登录态
      │                                         │ Authorization / refresh cookie
      │                                         │
      │                                         │ POST /api/integration/dsh/v1/authorization-codes
      │                                         │ ───────────────────────────────────────>
      │                                         │<── 一次性 authorizationCode
      │  pms.dsh.auth.sync                      │
      │<────────────────────────────────────────│
      │                                         │
      │ DSH Remote.setAuthCode                   │
      │ ────────────────> DSH Host              │
      │                                         │
      │                    DSH Host + service key
      │                    POST /api/integration/dsh/v1/token/exchange
      │                    ────────────────────────────────> PMS Backend
      │                    <──── aud=dsh-pms, TTL=120s ──────
      │                                         │
      │                    X-PMS-AI-Delegation │
      │                    ────────────────────────────────> PMS read facade
```

关键边界：

- 浏览器只传一次性授权码，不传 PMS 用户 JWT，不传 DSH 服务密钥。
- PMS 后端从当前认证上下文取得 `userId` 和 PMS `sessionId`，不信任请求体中的用户身份。
- DSH Host 负责服务密钥认证、授权码兑换和短期令牌缓存。
- PMS 集成接口继续在每次请求时验证短期令牌的用户、登录会话、audience 和 scope。

## 5. API 契约

### 5.1 签发一次性授权码

`POST /api/integration/dsh/v1/authorization-codes`

调用方：PMS iframe 前端，使用 PMS 当前登录态。该接口走现有 PMS 用户鉴权拦截器。

请求：

```json
{
  "dshSessionId": "opaque-dsh-session-id",
  "agentId": "project_assistant",
  "scopes": [
    "pms:project:read",
    "pms:task:read",
    "pms:workspace:embed"
  ]
}
```

响应 `data`：

```json
{
  "authorizationCode": "single-use-code",
  "expiresInSeconds": 90,
  "scopes": [
    "pms:project:read",
    "pms:task:read",
    "pms:workspace:embed"
  ]
}
```

规则：

- `userId`、PMS 登录 `sessionId` 从 `UserContext` 取得。
- `dshSessionId` 最大 128 字符，不能为空；`agentId` 最大 64 字符，必须匹配 `[a-zA-Z0-9_-]+`。
- `agentId` 和 `scopes` 是浏览器请求参数，只能作为请求意图，不能直接决定最终权限。后端必须先确认 Agent 已注册且启用，再按服务端 Agent 权限策略计算最终 scope；第一版只允许 `project_assistant` 使用项目/任务只读和工作区嵌入 scope。
- scope 只能来自服务端 allowlist，默认使用当前 Agent 的全部只读/嵌入 scope；浏览器不能通过传入未来的写权限 scope 提升权限。
- 授权码使用密码学安全随机值生成，数据库只保存 SHA-256 哈希，不保存明文。
- 授权码有效期 90 秒，使用一次后立即标记 `usedAt`，过期或已使用均不可兑换。
- 响应中的授权码只存在 PMS 前端内存和 DSH Host 的会话内存中，不写 URL、localStorage、sessionStorage 或日志。

### 5.1.1 Agent 与 scope 服务端策略

第一版不从浏览器或 DSH 请求体直接读取权限配置，新增集中策略对象（名称可按现有项目规范落地）：

```text
project_assistant
  allowed scopes:
    pms:project:read
    pms:task:read
    pms:workspace:embed
```

签发授权码时，后端按 `agentId` 查找该策略；Agent 不存在、未启用或请求 scope 超出策略时直接拒绝。兑换时再次按同一策略校验，并要求授权码记录中的规范化 scope 集合与兑换请求完全一致。后续增加 Agent 或写能力时，必须先增加服务端策略和对应测试，不能仅修改前端配置。

### 5.2 兑换 DSH 短期令牌

`POST /api/integration/dsh/v1/token/exchange`

调用方：DSH Host 进程。该接口不接受浏览器调用，使用 `X-DSH-Service-Key` 完成服务认证，并通过 `@IgnoreAuth` 绕过普通用户 Bearer 鉴权后执行专用服务校验。

请求：

```json
{
  "authorizationCode": "single-use-code",
  "dshSessionId": "opaque-dsh-session-id",
  "agentId": "project_assistant",
  "scopes": [
    "pms:project:read",
    "pms:task:read",
    "pms:workspace:embed"
  ]
}
```

响应复用现有 `DshTokenExchangeResponse`：

```json
{
  "token": "short-lived-jwt",
  "expiresInSeconds": 120,
  "audience": "dsh-pms",
  "scopes": [
    "pms:project:read",
    "pms:task:read",
    "pms:workspace:embed"
  ]
}
```

兑换时必须同时满足：

1. `X-DSH-Service-Key` 使用常量时间比较并与 PMS 配置值匹配。
2. 授权码哈希存在、未使用且未过期。
3. 请求中的 `dshSessionId`、`agentId` 和 scope 集合与授权码记录完全匹配。
4. 授权码绑定的 PMS 用户存在、处于启用状态，且绑定的 PMS 登录会话仍未撤销、未过期。
5. 在同一事务中锁定授权码记录、标记 `usedAt`，再签发令牌，防止并发重放。

错误码至少包括：

- `PMS_DSH_SERVICE_AUTH_FAILED`：服务密钥无效。
- `PMS_DSH_AUTH_CODE_INVALID`：授权码不存在、已使用、过期或绑定不匹配。
- `PMS_DSH_SESSION_INVALID`：授权码关联的 PMS 登录会话已失效。
- `PMS_DSH_SCOPE_FORBIDDEN`：请求权限超出允许范围。

### 5.3 保留现有接口

现有 `POST /api/integration/dsh/v1/token` 保留，用于：

- CLI 或服务端显式配置 `pmsUserToken + serviceKey` 的本地兜底。
- 既有自动化测试和迁移期部署。

新的浏览器登录态链路只调用 `/token/exchange`，不改变已有调用方的请求格式。

## 6. 授权码存储

新增 Flyway migration：`V44__dsh_authorization_codes.sql`。

表：`pms_dsh_authorization_code`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT AUTO_INCREMENT` | 主键 |
| `code_hash` | `CHAR(64)` | SHA-256 哈希，唯一 |
| `user_id` | `BIGINT` | PMS 用户 |
| `pms_session_id` | `BIGINT` | 签发时的 PMS 登录会话 |
| `dsh_session_id` | `VARCHAR(128)` | DSH 会话绑定 |
| `agent_id` | `VARCHAR(64)` | Agent 绑定 |
| `scope_json` | `LONGTEXT` | 规范化后的 scope 集合 |
| `expires_at` | `DATETIME` | 过期时间 |
| `used_at` | `DATETIME NULL` | 首次兑换时间 |
| `created_at` | `DATETIME` | 创建时间 |

索引：

- `UNIQUE(code_hash)`，避免授权码碰撞。
- `(dsh_session_id, expires_at)`，便于会话清理。
- `(expires_at, used_at)`，便于定期清理已使用或已过期记录。

清理策略：兑换时拒绝失效记录；后续可复用现有定时任务清理超过保留窗口的记录。第一版不依赖异步清理保证安全性。

## 7. DSH Host 与客户端桥接

### 7.1 Remote 接口

在 `PmsContextController` 增加两个 Host Remote 方法：

```ts
setAuthCode(
  sessionId: SessionId,
  payload: { code: string; expiresAt: number },
): void

clearAuthCode(sessionId: SessionId): void
```

新增 `PmsAuthStore`，按 DSH session 保存：

- 最近一次授权码。
- 授权码过期时间。
- 不保存 PMS 用户 Token 和服务密钥。

`PmsIntegrationClient` 增加 `PmsAuthStore` 依赖，令牌解析顺序调整为：

1. 配置的短期 `accessToken`。
2. 当前 DSH session 的浏览器授权码兑换。
3. 旧的 `pmsUserToken + serviceKey` 服务端兑换。
4. 返回 `PMS_AUTH_CONTEXT_MISSING`，提示 PMS 登录态未同步。

短期令牌按 DSH session 缓存，距离过期不足 5 秒时重新兑换。授权码兑换请求使用服务端 `serviceKey`，不经过浏览器。

### 7.2 `postMessage` 协议

在现有 PMS context 消息旁增加独立消息类型，版本仍为 `1`：

请求：

```json
{
  "source": "dsh",
  "type": "pms.dsh.auth.request",
  "version": 1,
  "requestId": "request-id",
  "dshSessionId": "opaque-dsh-session-id",
  "agentId": "project_assistant",
  "scopes": [
    "pms:project:read",
    "pms:task:read",
    "pms:workspace:embed"
  ]
}
```

响应：

```json
{
  "source": "pms",
  "type": "pms.dsh.auth.sync",
  "version": 1,
  "requestId": "request-id",
  "authorizationCode": "single-use-code",
  "expiresInSeconds": 90
}
```

安全规则：

- DSH 只接受来自 iframe `contentWindow` 且 `event.origin` 等于配置 PMS origin 的消息。
- PMS 只响应 `event.source === window.parent` 且 `event.origin` 等于从 `document.referrer` 解析出的 DSH origin 的请求。
- PMS 在无法确定父窗口 origin 时不发送授权码，不使用 `*` 作为认证消息目标。
- DSH 校验 `requestId`、授权码非空、TTL 为正数，并把授权码交给 Host Remote；不把授权码写入 React 状态、URL 或日志。
- context locator 与 auth message 分离，授权码不会进入 Agent 上下文或历史消息。

### 7.3 自动续租节奏

- iframe 加载完成时立即请求一次授权码。
- 之后每 60 秒请求一次新授权码，覆盖 DSH session 中尚未使用的旧授权码。
- DSH Token TTL 为 120 秒，授权码 TTL 为 90 秒，保证正常连续对话时始终存在可兑换授权码。
- 续租请求失败时保留当前仍未过期的授权码，并按 5 秒、15 秒、30 秒退避重试；只有授权码过期且重试仍失败时，才将状态显示为登录态未同步。
- PMS 登录失效时，授权码签发或兑换失败；DSH 工具返回明确的“PMS 登录态未同步或已失效”，不再返回含义模糊的 access token 配置错误。
- iframe 卸载时调用 `clearAuthCode` 和现有 `clear`，避免旧 DSH session 继续引用内存中的授权码。

## 8. 安全与权限

1. `PMS_DSH_SERVICE_KEY` 只配置在 PMS 后端和 DSH Host 进程，禁止出现在前端环境变量、iframe URL、`postMessage` 和错误消息。
2. PMS 用户 JWT 只用于 PMS iframe 调用授权码接口；不会发送到 DSH Host，也不会进入 Work Helper 请求体。
3. 授权码是随机值、90 秒有效、单次使用、数据库只存哈希。
4. DSH 短期令牌使用 `aud=dsh-pms`、唯一 `jti`、PMS `sid`、DSH session、Agent 和 scope；PMS 每次集成请求仍检查用户状态和登录会话。
5. scope 采用服务端 allowlist，不能由前端扩大。
6. 令牌和授权码不写入 URL、localStorage、sessionStorage、普通日志或错误响应。
7. 授权码兑换接口必须限制为已配置服务密钥的服务端调用；没有服务密钥时安全失败。
8. 现有静态 `PMS_DSH_ACCESS_TOKEN` 只作为本地开发/测试兜底，文档中标明不应使用长期管理员 Token。

## 9. 错误处理与可观测性

DSH UI 状态至少区分：

- `同步中`：正在向 PMS 申请授权码。
- `已连接`：已收到授权码并可由 Host 兑换。
- `未登录`：PMS 授权码接口返回未登录或登录会话失效。
- `连接失败`：PMS 服务不可用、来源校验失败或令牌兑换失败。

错误消息对用户使用稳定的中文提示；服务日志只记录 `requestId`、DSH session 的不可逆摘要、Agent ID、错误码和耗时，不记录授权码、用户 JWT 或服务密钥。

保留 `X-Request-Id` 贯穿：PMS 前端授权码请求、DSH Remote 记录、Host 兑换和后续 PMS facade 查询，便于定位但避免敏感信息进入日志。

## 10. 兼容与配置

新增/确认配置：

```text
PMS_DSH_SERVICE_KEY       # PMS backend + DSH Host，必须保持一致
DSH_CLIENT_PMS_AGENT_ID   # DSH client，默认 project_assistant
DSH_CLIENT_PMS_WORKSPACE_URL
DSH_CLIENT_PMS_WORKSPACE_ORIGINS
```

DSH Host 的 profile patch 必须显式注入服务密钥，但只在 Host 进程读取：

```yaml
- id: pms
  config:
    serviceKey: !!js process.env.PMS_DSH_SERVICE_KEY ?? ''
```

该配置不能出现在 DSH Client bundle、浏览器环境变量、iframe URL 或 `postMessage` payload 中。

生产部署还必须确认 PMS refresh cookie 对嵌入场景可用：DSH 与 PMS 使用同站点部署时沿用现有 Cookie 策略；跨站点部署时需要显式配置安全的 `SameSite=None; Secure`，并接受浏览器第三方 Cookie 策略可能导致 iframe 无法自动恢复登录态，此时 UI 必须引导用户在 PMS 顶层页面登录。

本地开发启动顺序：

1. PMS 后端设置 `PMS_JWT_SECRET` 和 `PMS_DSH_SERVICE_KEY`。
2. DSH Host 设置同一个 `PMS_DSH_SERVICE_KEY`；不设置 `PMS_DSH_ACCESS_TOKEN` 也可以走浏览器自动换取。
3. PMS 前端和 DSH 分别启动后，打开 DSH 的 PMS 业务工作区。
4. DSH iframe 请求授权码，Host 在首次 PMS 工具调用时兑换短期令牌。

如果 DSH 以 CLI 方式运行、没有 PMS iframe，则继续使用原有 `accessToken` 或 `pmsUserToken + serviceKey` 配置。

## 11. 测试与验收

### PMS 后端

- 授权码只能由已登录 PMS 用户签发。
- 签发的授权码响应包含 TTL 和规范化 scope，数据库只保存哈希。
- 正确服务密钥可以兑换一次，第二次兑换失败。
- 过期授权码、错误 DSH session、错误 Agent、scope 扩大、错误服务密钥均失败。
- 未注册 Agent、禁用 Agent 或仅通过修改浏览器 scope 请求来扩大权限均失败。
- 两个并发兑换请求最多一个成功。
- PMS 用户登出、会话撤销、用户禁用后不能签发或兑换。
- 现有 `/integration/dsh/v1/token` 和所有只读 facade 测试继续通过。

### PMS 前端

- 只响应来自父窗口且来源匹配的 auth request。
- 未登录或无法确定父来源时不发送授权码。
- 不把授权码写入 URL、浏览器存储或 page context。
- 页面刷新后能重新响应请求；定时续租会覆盖旧码。

### DSH

- 只接受来自指定 PMS iframe 的 auth sync 消息。
- 不匹配的来源、窗口、版本或 requestId 被忽略。
- Host 按 session 隔离授权码和短期令牌。
- 优先使用浏览器授权码，授权码缺失时再走旧配置兜底。
- Token 过期前并发查询只产生一次兑换请求。
- PMS 未登录时提示登录态未同步，而不是提示手动配置 access token。
- 续租短暂失败时保留现有有效授权码并重试，不因一次网络错误立即清空连接状态。

### 联调验收

1. 不配置 `PMS_DSH_ACCESS_TOKEN`，设置 PMS 与 DSH Host 的同一服务密钥。
2. 在 PMS 登录后从 DSH 打开 PMS 业务工作区。
3. 在 DSH 发送“列出当前用户可见项目”，确认调用 `pms_project_list` 成功。
4. 等待令牌接近过期或刷新 iframe，继续查询，确认自动续租成功。
5. PMS 登出后继续查询，确认返回登录态失效并且不使用旧权限。
6. 切换两个 DSH 会话/标签，确认授权码和查询结果互不串用。
7. 检查浏览器 URL、消息调试信息和服务日志，确认不出现 PMS 用户 Token、授权码明文和 DSH 服务密钥。

## 12. 后续扩展

这套桥接协议只负责身份和短期权限，不绑定具体 PMS 页面。以后普通修改 PMS 页面不需要重新集成；只有增加新的 Agent scope、Remote 消息版本或 DSH 工具能力时，才需要同步更新协议和联调测试。
