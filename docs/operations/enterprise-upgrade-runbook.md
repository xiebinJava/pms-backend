# PMS 企业版升级与上线手册

本文面向单企业本地部署。每个企业独立部署一套 PMS，不使用 `tenant_id`，组织、人员、权限和项目数据只属于本实例。

## 1. 上线前原则

- 生产数据库使用 MySQL 8 或 OceanBase MySQL 兼容模式；H2 仅用于开发、演示和自动化测试。
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

1. 停止应用写入，完成全量备份并记录备份校验值。
   2. 在目标库执行只读预检：

   ```bash
   ./scripts/enterprise-preflight.sh
   ```

   预检会检查英文登录名唯一性、组织/角色/项目引用完整性和组织路径。出现 `FAIL` 时先修复数据，不要跳过。

   预检还会确认登录审计、会话表及其索引已经就绪，并要求所有项目具备组织归属且数据库中只有一个有效根组织。

3. 使用应用同版本启动迁移。Flyway 会按 `V1__baseline_project_schema.sql`、`V2__enterprise_identity_org_rbac.sql` 顺序执行；`spring.sql.init.mode` 已关闭，不会重复执行 `schema.sql`：

   ```bash
   mvn -DskipTests package
   java -jar target/pms-backend-0.1.0.jar \
     --spring.profiles.active=mysql
   ```

   OceanBase 使用 `--spring.profiles.active=oceanbase`，并提供 `OCEANBASE_HOST/PORT/DATABASE/USER/PASSWORD`。

4. 迁移完成后执行只读验收：

   ```bash
   ./scripts/verify-enterprise-migration.sh
   ```

   验收包括企业表、关键列和索引、唯一活动根组织、内置 RBAC 角色以及项目组织归属。

## 4. 首次启动与账号安全

持久化环境没有共享默认账号。首次空库启动前注入一次性管理员：

```bash
export PMS_BOOTSTRAP_ADMIN_USERNAME=brad.xie
export PMS_BOOTSTRAP_ADMIN_NAME_ZH='谢斌'
export PMS_BOOTSTRAP_ADMIN_PASSWORD='至少 12 位的随机密码'
```

管理员登录后通过“人员与权限”邀请或导入员工。所有界面统一展示 `中文名（English.Name）`，登录英文名不区分大小写；英文名归一化字段由数据库唯一索引保护。

生产环境建议关闭开发用的复制链接返回：

```bash
export PMS_PASSWORD_RESET_EXPOSE_TOKEN=false
```

生产环境还应显式设置允许访问前端的来源，多个来源用逗号分隔：

```bash
export PMS_CORS_ALLOWED_ORIGINS='https://pms.example.com'
```

`PMS_JWT_SECRET` 至少需要 32 字节随机值；缺失或过短时后端会拒绝启动。

关闭后必须提供 `PasswordResetNotifier` Spring Bean，将重置链接投递到企业邮箱、飞书或企业微信；当前仓库只提供通知扩展点，不内置 SMTP 凭据。

## 5. 组织、权限与导入上线顺序

1. 在“组织架构”创建/校准根组织、业务群（BG）、部门/项目组，并为人员设置一个主归属和可选的兼职/项目归属。
2. 在“角色管理”确认内置角色与六类数据范围（本人、组织、本组织及下级、本人及下属、自定义组织、全公司）。自定义组织角色必须绑定至少一个有效组织。
3. 在“人员与权限”邀请少量管理员和业务负责人，验证禁用账号会结束职位、撤销会话且保留历史审计。
4. 批量导入时先上传 Excel/CSV，检查预览、错误行和 manager 英文名解析结果；确认无误后再提交。导入采用全有或全无事务，不要绕过预览直接写库。

## 6. 回滚与故障处理

- Flyway 迁移不提供自动 destructive rollback。若验收失败，停止应用，保留失败日志，使用迁移前备份恢复到新数据库并切换连接；不要直接删除生产表。
- 恢复后重新执行预检，再启动旧版本应用。确认登录、刷新会话、组织树和项目读写均正常后再决定是否重试迁移。
- 如只需撤销账号，使用后台“禁用”而不是删除记录；该操作会写审计并撤销 refresh session，历史项目与日志保持可追溯。

## 7. 运行检查清单

- [ ] 数据库备份与恢复演练完成
- [ ] `enterprise-preflight.sh` 通过
- [ ] Flyway 迁移日志无错误
- [ ] `verify-enterprise-migration.sh` 通过
- [ ] 管理员已替换开发密钥/密码
- [ ] 登录大小写不敏感、邀请激活、密码重置通知验证通过
- [ ] 组织拖拽/停用、主归属 + 兼职归属、RBAC 数据范围验证通过
- [ ] Excel/CSV 预览、错误下载、提交与失败回滚验证通过

## 8. 容器化与健康检查

开源试用可从 `docker-compose.example.yml` 启动 OceanBase、后端和前端。示例仅适用于新建
空库：`schema-init` 会执行 `schema.sql` 及 V2–V4 企业迁移，并写入一次性标记；已有生产库
必须使用本手册的备份、预检和升级流程。

后端提供无需登录的 `GET /api/health` 和 `GET /api/healthz`：数据库可用返回 HTTP 200 与
`{"status":"UP","database":"UP"}`，数据库不可用返回 HTTP 503 与 `DOWN`。响应不包含
连接串、SQL、密码或令牌。所有 API 响应都会带 `X-Request-Id`，该值也会写入操作审计日志，
可用于串联一次请求的前后端日志。

发布前的完整代码、迁移、安全、浏览器冒烟与回滚清单见
[`release-checklist.md`](release-checklist.md)。
