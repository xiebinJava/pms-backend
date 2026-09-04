# PMS 企业版发布验收清单

适用于单企业本地部署的正式发布。发布前复制本清单，在目标环境逐项记录结果。

## 2026-09-04 发布准备当前状态

- [x] 生产配置静态门禁已加入：`bash scripts/validate-production-config.test.sh` 通过；不会打印密钥。
- [x] 本机目标 OceanBase 已执行并复核 V18；V18 增加方案设计、三类评审和方案决策结构，V17 的需求澄清范围基线、V16 的项目等级字段和 V15 的通知提醒去重结构保持不变。
- [x] 当前代码回归：后端 `mvn -q test`（268 个用例，0 失败、0 错误、1 跳过）、前端 `pnpm test`（174 项，0 失败）、类型检查和构建通过。
- [x] 本机最新后端 JAR 已在 V18 迁移后使用 OceanBase profile 重启并复核 readiness；返回 `database=UP`、`migration=18`。
- [x] OpenAPI、生产配置门禁、脚本语法和当前发布一致性检查通过；前后端代码与文档已推送至远程 `main`/`release`。
- [ ] 目标企业仍需注入真实 JWT、OceanBase/SMTP 凭据、HTTPS、受限 CORS、对象存储和监控，并记录 RPO/RTO 与故障联系人。
- [ ] 本机 V18 备份生成、SHA-256/元数据校验和隔离空库恢复待补；历史 V12 备份演练不能替代当前 V18 备份演练。
- [x] 后端独立 CI `33705799078` 已通过单元测试、OpenAPI、配置/隐私门禁、Netty 依赖修复后的镜像 HIGH/CRITICAL 扫描和 SPDX SBOM。
- [x] 跨仓库 GitHub Actions 已确认 `PMS_FRONT_REPO_READ_TOKEN` 可读 sibling `pms-front`；最终远程运行 `33705799079` 的 OceanBase、API、桌面/移动端 Playwright 全通过，使用后端提交 `9adbbff` 与前端提交 `05f241f`。
- [x] 前端独立 CI `33704208291` 已通过隐私扫描、121 项测试、类型检查、构建、镜像高危扫描和 SPDX SBOM。
- [x] 已在两个仓库当前发布基线上创建并推送 `v1.0.4` 标签；`v1.0.3` 及更早历史标签保持不变。
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
- [ ] 按 V1–V18 顺序执行迁移，确认 V5 的 `deleted`、`version`、关键唯一索引和外键、V6 的审计保留索引、V7 的 `email_normalized` 唯一索引、V8 的任务附件、V9 的站内通知、V10 的通知节点标识、V11 的组织历史/导入失败字段、V12 的反馈工单/历史表、V13 的项目图片访问边界、V14 的审计上下文/结果字段和查询索引、V15 的通知去重键和唯一索引、V16 的 `project.project_level` 字段、V17 的需求澄清范围基线表以及 V18 的方案包/评审/决策表已落库。
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
- [ ] 通知中心要求 `project:read`，支持全部/未读/临期/逾期互斥筛选、分页、批量已读和项目/任务深链；筛选切换回到第 1 页。
- [ ] 默认关闭任务提醒；启用前验证临期只在 `dueDate=today+N`、逾期只在 `dueDate=today-1` 的 09:00 触发，不回填历史、不补发漏跑、不走 Webhook。
- [ ] 普通员工为任务负责人时，在对应边界日收到自己的站内提醒；换负责人后只通知新负责人，旧通知不撤回；DONE、无负责人、停用员工和已删除/终止项目任务不生成新提醒。
- [ ] 通知列表、分页、顶部预览和未读数在 SQL 中排除已删除项目；终止项目历史通知仍可打开，删除项目不出现在列表、未读数或普通深链。

## 运行与观测

- [ ] `GET /api/health` 和 `GET /api/healthz` 在数据库正常时返回 200/UP。
- [ ] 数据库不可用时健康检查返回 503/DOWN，不暴露 SQL 或连接密码。
- [ ] `/api/health/live` 与 `/api/health/ready` 可分别用于存活/就绪探针，Actuator 端点仅允许内网监控访问。
- [ ] API 响应包含 `X-Request-Id`，审计日志保存相同 request id。
- [ ] 审计页可按动作、资源、操作人和时间分页检索，敏感字段已脱敏。
- [ ] 审计页可按项目、结果和请求 ID检索，详情可显示字段级差异；`project:read` 不能替代 `admin:audit:read`。
- [ ] 创建/更新项目、变更角色权限或数据范围后，均能在审计页按项目或动作找到记录，且不含密码、令牌、正文、来源 URL、幂等键和二进制。
- [ ] 审计保留任务 dry-run 不删除数据，正式运行按每批 500 条清理并留下 `AUDIT_RETENTION_RUN`。
- [ ] 已配置日志保留、磁盘空间、数据库连接池和告警负责人。
- [ ] 若启用 SMTP，已设置 `PMS_MAIL_HEALTH_ENABLED=true` 并验证邮件健康检查；未启用 SMTP 时保持默认 `false`。

## 发布后

- [ ] 登录、人员、组织、角色、导入、项目列表、项目详情和注销完成浏览器冒烟。
- [ ] 首个工作日观察错误率、健康检查和审计写入，无异常后关闭旧版本入口。
- [ ] 发布记录包含版本号、迁移结果、验证人、时间和回滚联系人。
