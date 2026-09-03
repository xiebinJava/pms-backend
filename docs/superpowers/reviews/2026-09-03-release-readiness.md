# 发布准备自检与 review（2026-09-03）

## Review 范围

本次 review 覆盖当前企业版发布基线：迁移版本与文档合同、OpenAPI、CI 门禁、OceanBase
升级/备份/恢复、健康检查、前后端回归测试，以及剩余的企业环境验收项。工作保持单企业
部署模型，应用账号与迁移账号继续分权；没有改动业务流程或把凭据写入代码。

## 本次修复

1. 统一当前发布基线为 V1–V12，补齐反馈中心 API 的 OpenAPI 路由、请求体和响应模型，
   并增加 release consistency 检查，避免迁移、代码和 README 再次漂移。
2. CI 增加生产配置、隐私、OpenAPI、脚本语法和版本一致性门禁；跨仓库集成 workflow
   不再固定过期的前端提交，支持默认 `main` 与手工指定 `frontend_ref`。
3. Actuator 邮件健康检查改为显式开关：未配置 SMTP 时默认不参与 readiness；只有目标环境
   明确配置并验证 SMTP 后才设置 `PMS_MAIL_HEALTH_ENABLED=true`，避免“邮件未启用但就绪检查
   被误判为 DOWN”。
4. 补充本次 OceanBase 演练记录、发布清单和基础设施状态，保留外部企业环境的待办边界。
5. 清理前端设计示例中的个人姓名，确保独立 CI 与跨仓库集成均不携带隐私样例。
6. 修复后端镜像扫描发现的 Netty 安全依赖：将 AWS SDK 传递的 Netty `4.1.132.Final` 提升至
   `4.1.136.Final`；补丁提交 `9adbbff` 经独立 CI 和跨仓库集成复验通过。

## 自动化验证结果

| 验证项 | 结果 |
| --- | --- |
| 后端 `mvn -q test` | 206 个用例，0 失败、0 错误、1 跳过 |
| 前端 `pnpm test` | 121 项通过 |
| 前端 `pnpm typecheck` / `pnpm build` | 通过 |
| `release-consistency.test.sh` | 通过 |
| `validate-openapi.sh` | 通过 |
| `validate-production-config.test.sh` | 通过 |
| `check-privacy.sh` | 通过 |
| `bash -n scripts/*.sh docker/*.sh` | 通过 |
| 两仓库 `git diff --check` | 通过 |
| 后端独立 CI `33705799078` | 通过：206 个用例、OpenAPI/配置/隐私门禁、Netty 修复后的镜像高危扫描、SPDX SBOM |
| 跨仓库集成 CI `33705799079` | 通过：OceanBase、V1–V12 幂等、API 冒烟、登录代理、桌面/移动端 Playwright |
| 前端独立 CI `33704208291` | 通过：隐私扫描、121 项测试、类型检查、构建、镜像高危扫描、SPDX SBOM |
| 本机后端健康检查 | `/api/health/live`、`/api/health/ready` 返回 200，迁移版本 V12 |

## OceanBase 演练结果

- 在真实本机 OceanBase MySQL 兼容模式上使用 `pms_migrator` 完成 V1–V12 第一次升级，
  再次执行时所有 checksum 校验通过且无待执行迁移。
- 使用 `pms_app` 完成企业结构校验和完整性预检；应用账号没有获得迁移 DDL 权限。
- 生成 V12 逻辑备份、SHA-256 校验文件和精确行数元数据，恢复到一次性隔离空库后，
  结构、预检和全表精确行数均与备份一致。
- 对篡改备份、生产数据库名和非空目标库分别执行失败边界测试，脚本均按预期拒绝继续。
- 原业务库未删除、未清空、未重建、未被恢复覆盖。完整命令和证据见
  [`docs/operations/drill-records/2026-09-03-release-readiness.md`](../../operations/drill-records/2026-09-03-release-readiness.md)。

## CI 与浏览器验收

已确认跨仓库只读凭据配置有效；最终远程集成运行 `33705799079` 使用后端 `9adbbff` 与前端
`05f241f`，OceanBase、迁移幂等、API 冒烟、登录代理、桌面端和移动端 Playwright 均通过。
后端独立运行 `33705799078` 还通过了镜像 Trivy HIGH/CRITICAL 门禁和 SPDX SBOM，确认 Netty
安全依赖修复未引入回归。
此前失败的发布一致性检查（运行器缺少 `rg`）已改用 POSIX `grep`，手册录制用例也已对齐当前
文档导航结构；前端隐私扫描发现的个人姓名示例已替换为通用占位符。

## 剩余发布门禁

以下项目依赖目标企业的真实配置或审批，不能由本机结果替代：

- 注入强 JWT、OceanBase、SMTP、HTTPS 和受限 CORS，并确认默认密码已更换、旧令牌失效。
- 使用目标企业真实测试账号执行桌面/移动端 Playwright，验收登录、权限、组织、导入、
  项目、反馈和注销；保存验收人、时间和版本。
- 决定对象存储、分布式限流、监控告警、慢查询采集、日志保留、备份介质以及 RPO/RTO，
  并指定故障联系人。
- 按企业审批流程提交发布；本次代码与文档已推送到远程 `main`/`release`，两个仓库的
  `v1.0.4` 正式标签已创建并推送，`v1.0.3` 历史标签保持不变。

## 结论

本机代码、文档、门禁、OceanBase 恢复链路和远程集成验收已达到“可审计的发布候选”状态，
并已形成包含 Netty 安全修复的 `v1.0.4` 正式版本基线；
没有把本机演练误报为生产批准。完成上面的目标企业配置、真实账号验收和 GitHub 分支保护后，
再进入正式版本发布与生产切换。
