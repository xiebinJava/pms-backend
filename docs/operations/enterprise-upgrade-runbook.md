# PMS 企业版升级与上线手册

本文面向单企业本地部署。每个企业独立部署一套 PMS，不使用 `tenant_id`，组织、人员、权限和项目数据只属于本实例。

## 1. 上线前原则

- 运行时数据库固定使用 OceanBase MySQL 兼容模式；H2 仅用于自动化测试和一次性历史迁移快照，不作为本地或生产运行回退。
- 迁移前必须备份数据库，并在副本上演练。本文脚本全部只读，不会打印密码，也不会自动删除或覆盖业务数据。
- 固定使用一个业务数据库账号，授予迁移所需 DDL/DML 权限；应用运行账号按企业安全规范收敛权限。
- 生产环境必须设置随机的 `PMS_JWT_SECRET`（至少 32 字节），不要使用仓库里的开发默认值。

## 2. 配置数据库连接

脚本优先读取 `PMS_DB_*`，也兼容 `MYSQL_*` / `OCEANBASE_*`：

```bash
export PMS_DB_HOST=127.0.0.1
export PMS_DB_PORT=3306                 # OceanBase 通常为 2881
export PMS_DB_NAME=pms
export PMS_DB_USER=pms_migrator
export PMS_DB_PASSWORD='仅在当前 shell 注入，不要提交到仓库'
```

## 3. 迁移步骤

1. 停止应用写入，使用 [`oceanbase-backup-restore.md`](oceanbase-backup-restore.md) 的 `backup-oceanbase.sh` 完成全量备份并记录 SHA-256 校验值。
2. 在目标库执行只读预检：

   ```bash
   ./scripts/enterprise-preflight.sh
   ```

   预检会检查邮箱唯一性、旧英文名兼容字段唯一性、组织/角色/项目引用完整性和组织路径。出现 `FAIL` 时先修复数据，不要跳过。

   预检还会确认登录审计、会话表及其索引已经就绪，并要求所有项目具备组织归属且数据库中只有一个有效根组织。

3. 使用发布账号执行版本化升级。`scripts/oceanbase-upgrade.sh` 会按 V1–V12 迁移脚本顺序校验并只执行缺失版本；其中 V8 增加任务附件、V9 增加站内通知、V10 增加通知节点标识、V11 增加组织变更历史和导入失败状态字段、V12 增加反馈工单及追加式处理历史。`spring.sql.init.mode` 已关闭，不会重复执行 `schema.sql`：

   ```bash
   export OCEANBASE_USER=pms_migrator
   export OCEANBASE_PASSWORD="$PMS_MIGRATOR_PASSWORD"
   ./scripts/oceanbase-upgrade.sh
   ```

   启动应用时使用 `--spring.profiles.active=oceanbase`，并提供 `OCEANBASE_HOST/PORT/DATABASE/USER/PASSWORD`。应用运行时使用 `pms_app`，不要使用 `pms_migrator` 或 `root`。

4. 迁移完成后执行只读验收：

   ```bash
   ./scripts/verify-enterprise-migration.sh
   ```

   如需回滚，恢复到新建临时库后再执行上述验收；恢复命令和生产库保护参数见 [`oceanbase-backup-restore.md`](oceanbase-backup-restore.md)。

   验收包括企业表、关键列和索引、唯一活动根组织、内置 RBAC 角色以及项目组织归属。

## 4. 首次启动与账号安全

持久化环境没有共享默认账号。首次空库启动前注入一次性管理员：

```bash
export PMS_BOOTSTRAP_ADMIN_EMAIL='admin@example.com'
# Optional display names; keep the legacy username only when old clients need it.
export PMS_BOOTSTRAP_ADMIN_NAME_ZH='张伟'
export PMS_BOOTSTRAP_ADMIN_USERNAME='alex.zhang'
export PMS_BOOTSTRAP_ADMIN_PASSWORD='至少 12 位的随机密码'
```

管理员登录后通过“人员与权限”邀请或导入员工。邮箱是唯一核心身份，登录邮箱不区分大小写；中文名和英文名可选，界面优先展示 `中文名（English.Name）`，缺少姓名时展示邮箱。邮箱归一化字段由数据库唯一索引保护，旧英文名登录仅在兼容期内保留。

生产环境建议关闭开发用的复制链接返回：

```bash
export PMS_PASSWORD_RESET_EXPOSE_TOKEN=false
```

生产环境还应显式设置允许访问前端的来源，多个来源用逗号分隔：

```bash
export PMS_CORS_ALLOWED_ORIGINS='https://pms.example.com'
```

`PMS_JWT_SECRET` 至少需要 32 字节随机值；缺失或过短时后端会拒绝启动。

关闭后必须提供 `PasswordResetNotifier` Spring Bean，将重置链接投递到企业邮箱、飞书或企业微信；当前仓库提供 SMTP 实现和通知扩展点，不内置 SMTP 凭据。生产 SMTP 必须使用 HTTPS 的 `PMS_PUBLIC_BASE_URL`，并在启动检查中验证主机、端口、认证凭据和发件人。

## 5. 组织、权限与导入上线顺序

1. 在“组织架构”创建/校准根组织、业务群（BG）、部门/项目组，并为人员设置一个主归属和可选的兼职/项目归属。
2. 在“角色管理”确认内置角色与六类数据范围（本人、组织、本组织及下级、本人及下属、自定义组织、全公司）。自定义组织角色必须绑定至少一个有效组织。
3. 在“人员与权限”邀请少量管理员和业务负责人，验证禁用账号会结束职位、撤销会话且保留历史审计。
4. 批量导入时先上传 Excel/CSV，检查预览、错误行和直属上级邮箱解析结果（兼容旧英文名）；确认无误后再提交。导入采用全有或全无事务，不要绕过预览直接写库。

## 6. 回滚与故障处理

- Flyway 迁移不提供自动 destructive rollback。若验收失败，停止应用，保留失败日志，使用迁移前备份恢复到新数据库并切换连接；不要直接删除生产表。
- 恢复后重新执行预检，再启动旧版本应用。确认登录、刷新会话、组织树和项目读写均正常后再决定是否重试迁移。
- 如只需撤销账号，使用后台“禁用”而不是删除记录；该操作会写审计并撤销 refresh session，历史项目与日志保持可追溯。

## 7. 运行检查清单

- [ ] 数据库备份与恢复演练完成（含 `verify-backup.sh` 校验）
- [ ] `enterprise-preflight.sh` 通过
- [ ] Flyway 迁移日志无错误
- [ ] `verify-enterprise-migration.sh` 通过
- [ ] 管理员已替换开发密钥/密码
- [ ] 登录大小写不敏感、邀请激活、密码重置通知验证通过
- [ ] 组织拖拽/停用、主归属 + 兼职归属、RBAC 数据范围验证通过
- [ ] Excel/CSV 预览、错误下载、提交与失败回滚验证通过

## 8. 容器化与健康检查

开源试用可从 `docker-compose.example.yml` 启动 OceanBase、后端和前端。示例仅适用于新建
空库和已有库的 `schema-init` 都调用 `scripts/oceanbase-upgrade.sh`，按 V1–V12 逐版本、逐语句记录检查点；不再单独执行 `schema.sql` 或一次性 bootstrap 标记。已有生产库
必须使用本手册的备份、预检和升级流程；检测到旧 Flyway 历史时，需先核对发布包并显式设置 `PMS_ACCEPT_FLYWAY_BASELINE=true`。

后端提供无需登录的 `GET /api/health` 和 `GET /api/healthz`：数据库可用返回 HTTP 200 与
`{"status":"UP","database":"UP"}`，数据库不可用返回 HTTP 503 与 `DOWN`。响应不包含
连接串、SQL、密码或令牌。另有 `/api/health/live`（仅进程存活）和 `/api/health/ready`（数据库与迁移就绪），
以及仅绑定管理地址/端口（默认容器内 `127.0.0.1:8081`）的 `/actuator/health`、`/actuator/metrics`、`/actuator/prometheus`。它们不会经过前端 `/api/**` 代理。本机可用 `docker-compose.observability.yml` 叠一层 Prometheus/Grafana（只绑 127.0.0.1）；集群用 Helm 的 ClusterIP 管理口，需要时再开 ServiceMonitor，不要把 8081 挂到 Ingress。所有 API 响应都会带 `X-Request-Id`，该值也会写入操作审计日志，
可用于串联一次请求的前后端日志。

未启用 SMTP 时，Actuator 默认关闭邮件健康检查（`PMS_MAIL_HEALTH_ENABLED=false`），避免空的
JavaMailSender 把就绪状态误判为 DOWN。启用正式 SMTP 后，可将该变量设为 `true`，再把 SMTP
连通性纳入监控告警。

发布前的完整代码、迁移、安全、浏览器冒烟与回滚清单见
[`release-checklist.md`](release-checklist.md)。

容器运行时默认使用后端 UID 10001、非 root Nginx、只读根文件系统、独立上传卷和
`no-new-privileges`；后端与前端只有在健康检查通过后才被 Compose 视为可用。生产部署应
根据机器容量调整 `mem_limit`/`cpus`，并通过反向代理提供 HTTPS。示例 Compose 将数据库/迁移服务
放在 `pms-data` 私网、前端与后端放在独立的 `pms-edge` 私网；Nginx 默认只信任 loopback，
不会把整个 Compose 服务网段当作代理。外层 TLS 终止代理必须使用明确的可信 IP/CIDR，并在
`nginx.conf` 的 `set_real_ip_from`/`geo` 中按部署网络显式加入；其它转发头会被清空，不可信客户端的
伪造头会被忽略。生产环境必须设置
`PMS_DEPLOYMENT_ENV=production`，启用通知启动校验并关闭重置/邀请 token 回显。

## 9. 私有仓库集成测试凭据

后端的 `.github/workflows/integration.yml` 会检出同一 GitHub Owner 下的 `pms-front` 并执行 OceanBase + Playwright
集成测试。由于 GitHub Actions 的默认 `GITHUB_TOKEN` 只能读取当前仓库，必须在
后端仓库的 Settings → Secrets and variables → Actions 中创建仓库级 secret
`PMS_FRONT_REPO_READ_TOKEN`。该 Token 只授予同 Owner 下 `pms-front` 的 Contents: Read 权限，
不要复用管理员个人 Token，也不要把 Token 写入 workflow、日志或 `.env`。未配置该 secret 时，
工作流会在检出前给出明确错误并停止，不会误报为 OceanBase 或应用故障。

设置完成后，在后端仓库进入 Actions → `integration-and-e2e`，点击 **Run workflow**，
选择 `main`（或输入已经审核过的前端分支、标签或提交 SHA）后运行。普通 push/PR 会自动使用
前端远程 `main`，手动发布验收可以通过 `frontend_ref` 固定不可变提交。工作流成功后，重点查看
`oceanbase-and-browser` 作业中的迁移幂等、API 冒烟、登录代理和桌面/移动端 Playwright 步骤；
不要仅凭“工作流已启动”判断通过。
