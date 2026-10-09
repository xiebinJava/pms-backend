# PMS CLI-Only 设计方案

## 目标

为 PMS 提供一个类似 GitHub CLI 和飞书 CLI 的独立 `pms` 命令行工具，使 Codex、Claude Code、WorkBuddy 等具有终端能力的工作台可以通过自然语言调用 PMS 的业务能力。

## 产品决策

1. 第一阶段不保留 MCP Server 和 OpenCLI Plugin。
2. 对外产品名称为 `pms` CLI，安装后命令必须进入 PATH。
3. CLI-Anything 只作为 CLI 生成、命令设计、Skill 生成和测试方法论，不作为 PMS 运行时依赖。
4. PMS 后端仍然是业务数据、权限、流程模板、动态字段、幂等和审计的唯一真源。
5. CLI 不访问数据库，不复制 PMS 业务规则，不使用管理员身份代替当前用户。
6. CLI 同时提供人类友好输出和稳定 JSON 输出，默认支持 `--help`、`--format json`、`--dry-run`。
7. 第一阶段支持本地 Codex、Claude Code、WorkBuddy 等可执行终端命令的工作台；ChatGPT 网页版远程调用不属于本阶段范围。
8. CLI 统一调用现有 `/api/integration/ai/v1` 业务门面；不直接调用项目、专题、故事等领域 Controller。
9. 新 CLI 的来源标识统一为 `pms-cli`；现有 DSH 登录和历史适配器可以继续服务存量功能，但不再作为新 CLI 的入口。

## 用户体验

```bash
npm install -g @xiebinjava/pms-cli
pms setup
pms auth login
pms auth status
pms project list --format json
```

用户可以直接对工作台说：

> 查询所有延期的 S 级项目，并列出项目经理。

Agent 通过 Skill 发现并执行 `pms` 命令。动态流程节点和字段通过能力目录读取，不需要为每个字段或节点重新发布 CLI。

## CLI 分层

### 固定核心命令

- `pms auth login|status|logout`
- `pms setup|doctor`
- `pms capabilities`
- `pms search`
- `pms get`
- `pms context`
- `pms operation preview|execute`
- `pms workflow capabilities|action`

### 常用快捷命令

- `pms project list|get|create|update`
- `pms requirement list|get|create|update`
- `pms topic list|get|create|update`
- `pms story list|get|create|update`
- `pms task list|get|create|update`
- `pms iteration list|get|create|update`
- `pms dashboard summary`

能力目录还必须覆盖配置和治理类资源：用户、组织架构、角色权限、批量导入、审计日志和流程模板。它们不一定都需要单独快捷命令，但必须可以通过 `capabilities`、`search`、`get`、`context` 和 `operation` 在权限允许时调用。

快捷命令只负责参数整理和用户体验，实际业务校验仍由 PMS 的能力目录和后端命令服务完成。

## 认证方案

CLI 使用浏览器授权，不接收或保存 PMS 密码：

1. `pms auth login` 生成 PKCE verifier、state 和本地回调地址。
2. CLI 打开 PMS 授权页，用户完成已有 PMS 登录。
3. PMS 仅允许已登记的 loopback redirect URI，并返回一次性 authorization code。
4. CLI 使用 code 和 verifier 换取短期 access token 和可撤销 refresh token。
5. refresh token 写入操作系统凭据管理器，access token 只保存在进程内存。
6. `pms auth logout` 撤销 refresh token 并清理本地凭据。

CLI 不携带 DSH service key，也不复用只允许服务端交换的管理员 Token 流程；授权仍复用现有 PMS 用户、会话和权限体系。

不得通过 CLI 保存邮箱密码、永久 Token、服务密钥或管理员 Token。

## 操作闭环

```text
自然语言
  ↓
Skill 引导 Agent 读取 capabilities/context
  ↓
CLI 解析参数并请求 PMS
  ↓
PMS 后端权限/业务/版本/幂等校验
  ↓
预览或执行
  ↓
回读资源并返回 refreshScopes、auditId
```

对于创建、修改、删除、完成节点、发布模板等高风险操作，默认要求 `--dry-run` 或明确确认；不可通过 Skill 绕过服务端权限。

## 技术路线

- CLI-Anything：生成第一版命令分组、`--json` 输出、REPL 可选结构、Skill 草稿和测试矩阵。
- 最终 CLI：保留在独立 `pms-ai-connector` 仓库中，产品命令为 `pms`。
- 实现语言：优先复用现有 TypeScript PMS Client 和 contracts；CLI-Anything 生成的 Python 代码仅作为设计和测试参考，不复制业务逻辑。
- PMS 后端：继续复用 `PmsCommandRegistry`、能力目录、查询门面、执行服务、版本校验和审计链路。

## 非目标

- 不为每个 HTTP 接口生成一个一对一 CLI 命令。
- 不让 CLI 直接执行 SQL。
- 不通过浏览器点击替代 PMS 主 API 链路。
- 不承诺 ChatGPT 网页版在没有远程桥接的情况下调用本机 CLI。
- 不把 CLI-Anything 生成器本身打包进用户运行环境。
