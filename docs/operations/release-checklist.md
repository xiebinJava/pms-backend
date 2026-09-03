# PMS 企业版发布验收清单

适用于单企业本地部署的正式发布。发布前复制本清单，在目标环境逐项记录结果。

## 2026-09-03 发布准备当前状态

- [x] 生产配置静态门禁已加入：`bash scripts/validate-production-config.test.sh` 通过；不会打印密钥。
- [x] 本机 OceanBase V1–V12 幂等升级、迁移校验和完整性预检通过，反馈中心表结构已落库。
- [x] 当前代码回归：后端 `mvn -q test`（206 个用例，0 失败、0 错误、1 跳过）、前端 `pnpm test`（121 项）、类型检查和构建通过。
- [x] 本机最新后端 JAR 已使用 OceanBase profile 重启；`/api/health/live` 与 `/api/health/ready` 返回 200，ready 迁移版本为 V12。
- [x] OpenAPI、生产配置门禁、脚本语法和当前发布一致性检查通过；后端 `29392be`、前端 `577899d` 已推送至远程 `main`/`release`。
- [ ] 目标企业仍需注入真实 JWT、OceanBase/SMTP 凭据、HTTPS、受限 CORS、对象存储和监控，并记录 RPO/RTO 与故障联系人。
- [x] 本机 V12 备份生成、SHA-256/元数据校验和隔离空库恢复已完成；恢复后结构、预检和精确行数比对通过。不得以历史 V5 备份代替 V12 演练。
- [x] 跨仓库 GitHub Actions 已确认 `PMS_FRONT_REPO_READ_TOKEN` 可读 sibling `pms-front`；远程运行 `33701569446` 的 OceanBase、API、桌面/移动端 Playwright 全通过。
- [ ] 目标企业仍需使用真实测试账号和自己的配置重新执行桌面/移动端 Playwright，并由企业验收人签字。

## 2026-09-01 本机目标实例执行快照

以下是本次本机 OceanBase `brad_pms` 演练的实际结果，不替代目标企业生产签字：

- [x] 后端 `mvn -q test`：187 个用例通过，0 失败、0 错误、1 跳过。
- [x] 前端 `pnpm test`：112 项通过；`pnpm typecheck`、`pnpm build` 通过。
- [x] OceanBase V1–V10 两次幂等升级、企业迁移校验和完整性预检通过。
- [x] v10 逻辑备份、压缩流、SHA-256 和全表精确行数元数据通过；恢复到隔离空库并完成 5 张关键表行数比对。
- [x] 后端 liveness/readiness 均返回 200；readiness `database=UP`、`migration=10`。
- [x] 本地邮箱登录、Playwright 桌面主流程、390px 窄屏和使用手册录制通过。
- [x] 跨仓库 CI：后端运行 `33596876132` 已通过 OceanBase、迁移幂等、API 冒烟、浏览器代理和桌面/移动端 Playwright。
- [ ] 生产环境注入强 JWT、OceanBase/SMTP 凭据、HTTPS、受限 CORS、对象存储、监控告警，并记录 RPO/RTO 和故障联系人。

详细命令、校验值和行数证据见 [`drill-records/2026-09-01-release-acceptance.md`](drill-records/2026-09-01-release-acceptance.md)。

## 代码与构建

- [ ] 后端使用 Java 17，执行 `mvn -q test` 全部通过。
- [ ] 前端执行 `pnpm typecheck` 和 `pnpm build` 全部通过。
- [ ] CI 的后端测试、前端检查和密钥扫描均通过。
- [ ] CI 的 OceanBase 集成冒烟、Playwright 桌面/窄屏测试、Trivy 高危扫描和 SPDX SBOM 均通过。
- [ ] OpenAPI 合同通过 `./scripts/validate-openapi.sh` 校验。
- [ ] 发布版本、数据库迁移脚本和回滚说明已标记并归档。

## OceanBase 与迁移

- [ ] 已确认目标 OceanBase MySQL 兼容模式、字符集 `utf8mb4` 和时区。
- [ ] 已完成数据库备份与恢复演练，并保存校验值。
- [ ] 备份文件包含 schema 版本、`.sha256` 校验文件和元数据，恢复仅使用显式空目标库。
- [ ] 执行 `scripts/enterprise-preflight.sh` 通过。
- [x] 按 V1–V12 顺序执行迁移，确认 V5 的 `deleted`、`version`、关键唯一索引和外键、V6 的审计保留索引、V7 的 `email_normalized` 唯一索引、V8 的任务附件、V9 的站内通知、V10 的通知节点标识、V11 的组织历史/导入失败字段以及 V12 的反馈工单/历史表已落库。
- [x] 执行迁移后 `scripts/verify-enterprise-migration.sh` 和 `scripts/enterprise-preflight.sh` 通过。
- [ ] 生产应用只使用 `oceanbase` profile；H2 仅存在于测试配置。
- [ ] 迁移失败时不直接删除业务表，按升级手册恢复到新数据库。

## 安全配置

- [ ] `PMS_JWT_SECRET` 为随机值且至少 32 字节，不在日志、镜像或仓库中出现。
- [ ] `OCEANBASE_USER` 使用最小必要权限，密码通过密钥管理或运行环境注入。
- [ ] `PMS_CORS_ALLOWED_ORIGINS` 只填写正式前端来源，不使用 `*`。
- [ ] 关闭 `PMS_PASSWORD_RESET_EXPOSE_TOKEN` 和 `PMS_INVITATION_EXPOSE_TOKEN`，并验证通知适配器。
- [ ] `PMS_DEPLOYMENT_ENV=production`，通知启动校验开启；HTTPS 反代来自可信代理网段。
- [ ] 已创建正式管理员并更换所有演示账号密码，不使用共享默认密码。
- [ ] 已验证禁用、改密、注销和刷新令牌轮换会立即结束旧会话。

## 功能冒烟

- [ ] 邮箱登录不区分大小写；中文名和英文名可选，页面优先展示 `中文名（English.Name）`，缺少姓名时展示邮箱。
- [ ] 组织画布可以缩放、拖拽，节点连线、负责人和组织层级正确。
- [x] 员工主归属与组织负责人关系独立，兼职/项目归属可追溯；组织变更历史可查询。
- [ ] 角色权限和数据范围在页面与直接 API 调用中均生效。
- [x] Excel/CSV 导入可预览、下载服务端错误报告、事务提交且重复提交幂等；失败批次整体回滚。
- [ ] 项目列表与详情的项目经理、业务线、成员、周期、状态和进度一致。
- [ ] 项目终止后写操作被拒绝，任务删除、节点排期和业务线负责人联动正常。

## 运行与观测

- [ ] `GET /api/health` 和 `GET /api/healthz` 在数据库正常时返回 200/UP。
- [ ] 数据库不可用时健康检查返回 503/DOWN，不暴露 SQL 或连接密码。
- [ ] `/api/health/live` 与 `/api/health/ready` 可分别用于存活/就绪探针，Actuator 端点仅允许内网监控访问。
- [ ] API 响应包含 `X-Request-Id`，审计日志保存相同 request id。
- [ ] 审计页可按动作、资源、操作人和时间分页检索，敏感字段已脱敏。
- [ ] 已配置日志保留、磁盘空间、数据库连接池和告警负责人。
- [ ] 若启用 SMTP，已设置 `PMS_MAIL_HEALTH_ENABLED=true` 并验证邮件健康检查；未启用 SMTP 时保持默认 `false`。

## 发布后

- [ ] 登录、人员、组织、角色、导入、项目列表、项目详情和注销完成浏览器冒烟。
- [ ] 首个工作日观察错误率、健康检查和审计写入，无异常后关闭旧版本入口。
- [ ] 发布记录包含版本号、迁移结果、验证人、时间和回滚联系人。
