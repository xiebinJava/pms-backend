# 基础设施就绪状态

更新时间：2026-09-01

本文件只记录企业级基础设施状态，不代表业务功能已经完成生产验收。每完成一个基础设施任务，必须补充验证命令、结果和对应提交。

## 当前基线

| 检查项 | 当前状态 | 证据 |
| --- | --- | --- |
| OceanBase `brad_pms` | V1–V10 已执行并复核 | 使用 `pms_migrator` 连续执行两次版本升级，均完成 checksum 校验并返回无待执行版本；V7 邮箱唯一索引、V8 任务附件、V9 站内通知、V10 通知节点标识及关键表结构、外键和数据范围预检通过 |
| 后端回归 | 已通过 | `mvn -q test`，186 个用例通过（0 失败、0 错误、1 跳过），包含错误契约、鉴权和限流测试 |
| 前端回归 | 已通过 | `pnpm test`，111 项通过（当前 `main` 提交 `eb8e4e8`） |
| 前端类型与构建 | 已通过 | `pnpm typecheck`、`pnpm build` |
| 运行脚本语法 | 已通过 | `bash -n scripts/*.sh docker/*.sh` |
| 后端权限注解 | 已完成基础覆盖 | 控制器接口已扫描，受保护接口使用 `@RequirePermission` 或 `@IgnoreAuth` |
| 请求追踪/基础审计 | 已具备 | `X-Request-Id`、登录日志、操作日志；错误响应携带 requestId |
| 发布基线 | 本地已收口，远程集成待凭据 | `v1.0.0` 标签仍指向后端 `7cc2820` / 前端 `1519e887`；当前本地 `main` 为后端 `88e06d9`、前端 `eb8e4e8`，均已提交但尚未推送；远程不使用 `master` |

## 仍需完成

| 优先级 | 缺口 | 影响 |
| --- | --- | --- |
| P1 | 生产目标环境的备份介质、RPO/RTO 和故障联系人仍需确认 | 本机已完成 V1–V10 升级幂等、preflight、verify、逻辑备份、隔离空库恢复、数据比对和停库恢复演练；生产仍需按企业 RPO/RTO 保存介质与联系人 |
| P1 | CI、E2E、镜像扫描和 SBOM 已配置；本次集成流水线仍缺跨仓库只读凭据 | checkout 已更新为当前前端 `main` 提交 `1519e88`；需要在后端仓库配置 `PMS_FRONT_REPO_READ_TOKEN` 后重跑集成任务，该 Secret 不能由代码代填 |
| P2 | Java 21 升级尚未规划 | 当前保持 Java 17；Spring Boot 3.5 依赖升级已完成，Java 21 留待后续兼容性窗口 |

## 已收口能力

| 检查项 | 当前状态 | 证据 |
| --- | --- | --- |
| 错误响应合同 | 已完成 | `GlobalExceptionHandlerTest` 覆盖 400/401/403/409/422/500；响应统一包含 `code/msg/data/requestId` |
| 前端鉴权刷新 | 已完成 | Axios 仅对 HTTP 401 尝试刷新，刷新失败保留 redirect；业务错误使用后端 `msg` |
| 代理转发头 | 已完成 | `ForwardedHeaderFilter` 与 `server.forward-headers-strategy=framework` |
| 敏感接口限流 | 已完成 | 登录、刷新、重置、邀请、导入和图片上传按 IP+账号窗口限流，超限返回 429 |
| 敏感错误脱敏 | 已完成 | 鉴权解析日志不输出令牌内容；未知异常返回固定安全文案 |
| Helm / 观察栈 | 清单已落地，生产刮取待企业接入 | `deploy/helm/pms` 只部署前后端；`docker-compose.observability.yml` 本机绑 127.0.0.1；`/actuator/prometheus` 仍在默认 `127.0.0.1:8081` |

## 执行规则

1. Task 1 和 Task 2 完成前，不把服务标记为生产就绪。
2. 每个任务先测试，再实现，再执行 Review；Review 通过后才能进入下一任务。
3. 任何脚本、配置或文档不得包含真实密码、JWT、令牌或密钥。
4. 本地空密码 OceanBase 只允许作为当前开发实例特例，生产环境必须使用非空强密码。

详细步骤见 [`../superpowers/plans/2026-08-28-enterprise-infrastructure-hardening.md`](../superpowers/plans/2026-08-28-enterprise-infrastructure-hardening.md)。

## 执行记录

| 任务 | 状态 | Review 证据 |
| --- | --- | --- |
| Task 0：基线盘点与文档状态对齐 | 已完成 | 当前后端 `mvn -q test` 186 个用例（0 失败、0 错误、1 跳过）；前端 `pnpm test` 111 项、`pnpm typecheck`、`pnpm build` 通过；脚本 `bash -n` 和隐私扫描通过；本地提交为后端 `88e06d9`、前端 `eb8e4e8`。 |
| Task 1：OceanBase 运行账号隔离 | 已完成 | 配置测试通过；Compose 解析通过；本地 OceanBase 已创建 `pms_app` / `pms_migrator`；应用账号建表被拒绝、迁移账号 DDL 通过；探针表已清理。 |
| Task 2：OceanBase 备份、恢复与版本升级 | 已完成本机演练 | 升级脚本 V1–V10 checksum、逐语句检查点、命名锁、现有 `brad_pms` 二次幂等运行、精确行数逻辑备份、SHA-256、隔离空库恢复、源库/恢复库数据比对和停库恢复均通过；详见演练记录。 |
| Task 3：统一安全边界、错误响应和请求链路 | 已完成 | 后端全量测试、前端 111 项 Node 测试、`pnpm typecheck`、`pnpm build` 通过；覆盖 400/401/403/409/422/500/429、requestId、刷新失败跳转、代理转发和敏感接口限流；提交见安全边界提交。 |
| Task 4：通知服务和上传文件生产化 | 已完成 | 后端 `mvn -q test`、脚本语法、Compose 配置校验通过；上传魔数/MIME/配额/路径穿越、图片接口和邀请通知测试通过；SMTP 通知启动检查与 `pms-uploads` 持久卷已接入。 |
| Task 5：容器与 Compose 运行时加固 | 已完成 | `ContainerHardeningTest`、后端全量测试、Compose 配置、脚本语法和 diff 检查通过；后端 UID 10001、非 root Nginx、健康检查、只读根文件系统、资源上限、网络隔离和安全响应头已接入。 |
| Task 6：可观测性、审计保留和 API 合同 | 已完成（静态/自动化验证） | `mvn -q test`、OpenAPI 校验通过；存活/就绪探针、私有 Actuator 端口、有限路由指标、UTC JSON 日志、审计脱敏与无长事务批量清理任务已接入。 |
| Task 7：集成 CI/CD、安全扫描与浏览器冒烟 | 本地门禁通过，跨仓库集成待凭据 | Trivy Action 已固定为有效的 `v0.36.0`；前端 `package.json` 与 CI/集成工作流统一使用 `pnpm@9.15.9`；后端 CI `33352990972` 已通过测试、Trivy HIGH/CRITICAL 和 SPDX SBOM；本机 Playwright 桌面/390px 各 1 条通过；远程集成仍需要后端 secret `PMS_FRONT_REPO_READ_TOKEN` 读取私有前端仓库。 |
| Task 8：生产演练与开源交付 | 本机演练完成，生产注入待目标企业 | OceanBase V1–V10、备份/恢复/故障演练和本机应用验收已记录；生产 JWT、SMTP、HTTPS、CORS、对象存储与监控告警需由部署企业注入并复验。 |

## 2026-08-31 合并后收口执行记录

### 邮箱核心身份改造（当前变更）

- 新增 V7 `email_normalized` 字段及唯一索引；新增账号以邮箱登录，中文名和英文名可选。
- 旧 `username` 登录、旧导入表头和历史显示数据保留兼容；无姓名账号统一回退展示邮箱。
- 邀请与员工导入要求邮箱唯一，组织负责人、直属上级均支持邮箱标识；主归属关系与组织负责人关系不合并。
- 后端功能提交 `205ae3c`、文档收口提交 `7788787`/`fa28a67`，以及前端提交 `b50bc0f`/`2711481` 已完成本地回归并推送到对应 feature 分支。
- 本次变更已在本地目标 OceanBase 执行 V7，并重新跑 preflight/verify 全部通过；当前用户数据均已具备可归一化邮箱。V8–V10 随后补充任务附件、站内通知和通知节点标识；其他部署环境必须按完整 V1–V10 顺序执行并保存校验结果。

### Task 1：发布基线

- 后端 `v1.0.0` 标签：`7cc2820`，已合并 `codex/oceanbase-migration`；标签之后仅追加文档收口提交。
- 前端 `v1.0.0` 标签：`1519e88736d2d9fe19c1e436b8a96cd66b1457bf`，已合并 `codex/pms-design-system`，并包含邮箱身份 E2E 修正。
- 后端集成工作流已固定本次前端发布提交 `1519e88736d2d9fe19c1e436b8a96cd66b1457bf`；远程主分支名称为 `main`，没有 `master`。

### Task 2：本地门禁与远程 CI

- 后端 `mvn -q test`：通过；`./scripts/validate-openapi.sh`：通过；`bash -n scripts/*.sh docker/*.sh`：通过。
- 前端 `pnpm test`：111 项通过；`pnpm typecheck`：通过；`pnpm build`：通过。
- H2 运行配置扫描和私钥材料扫描：通过。
- 远程 CI 的失败原因已定位并修正：Trivy Action 版本固定为有效的 `v0.36.0`；前端容器构建固定 `pnpm@9.15.9`，避免 pnpm 11 忽略构建脚本；后端单仓库测试不再因未检出前端而报 `NoSuchFileException`。
- 前端 CI `33351102816` 已通过（测试、类型检查、构建、Nginx 镜像 Trivy 和 SPDX SBOM）。后端升级后 CI `33352990972` 已通过单元测试、OpenAPI、H2/密钥扫描、后端镜像 Trivy HIGH/CRITICAL 扫描和 SPDX SBOM。
- 最新集成运行 `33350901997` 在凭据预检处按设计停止。后端仓库尚未配置 `PMS_FRONT_REPO_READ_TOKEN`，不能使用个人令牌替代；本次代码已将前端 checkout 更新为当前提交，配置 Secret 后需重新运行。
- 后端镜像扫描已完成受控修复：Spring Boot 3.5.14、Spring Framework 6.2.19、Tomcat 11.0.22、MyBatis-Plus 3.5.17、Jackson 2.21.4、Micrometer 1.15.12、Logback 1.5.18、Connector/J 9.4.0，并迁移到 Jakarta Servlet/Validation；本地完整测试与远程 Trivy/SBOM 均通过。

#### 依赖安全升级 Review（2026-08-31）

- 选择 Spring Boot 3.5.14 是因为 Spring Boot 2.7/Spring Framework 5 已无法覆盖当前 Trivy 数据库中需要 Spring 6.2.x 的修复项；没有降低扫描阈值，也没有添加漏洞忽略规则。
- 为保持 Java 17，完成 `javax.validation`/`javax.servlet` 到 Jakarta API 的源码与测试迁移，并切换 `mybatis-plus-spring-boot3-starter`；MyBatis-Plus 3.5.17 分页插件显式依赖 `mybatis-plus-jsqlparser`。
- 兼容性修复：MyBatis-Plus 3.5 的 `selectCount` 返回 `Long`，服务层计数和 DTO 转换已显式处理；Mockito 对重载 `insert` 使用显式泛型 matcher，避免测试编译歧义；Jackson/Micrometer 使用 Boot 支持的 BOM 属性锁定修复版本。
- Review 结果：`mvn -q test` 通过（186 tests，0 failures、0 errors、1 skipped，退出码 0）；下一步必须在 GitHub Actions 新提交上完成跨仓库集成和镜像 Trivy 扫描。

### Task 3：生产配置核查

- `OceanbaseConfigurationTest`、`NotificationConfigurationValidatorTest`、`ContainerHardeningTest`：通过；Compose 配置解析：通过；私钥材料扫描：无命中。
- 示例 Compose 默认显式使用 `development`，避免本地示例在未配置 SMTP 时误以 `production` 启动；生产部署仍必须显式设置 `PMS_DEPLOYMENT_ENV=production`、强 JWT、正式 SMTP、受限 CORS，并关闭重置/邀请 token 回显。
- 当前未写入任何生产密钥或凭据；生产配置仍需目标企业通过密钥管理器注入，不能在本地或 Git 中代填。

### Task 4：OceanBase 备份恢复与故障演练

- 已通过现有 `pms-oceanbase` 容器内的 `obclient` 完成只读 `SELECT 1` 连通性确认。
- `enterprise-preflight.sh` 和 `verify-enterprise-migration.sh` 已在同一 OceanBase 容器内通过；使用官方镜像内 `/u01/obclient/bin/mysqldump` 完成 gzip 备份、SHA-256 校验、精确行数元数据和隔离空库恢复，源库/恢复库关键表行数一致。
- 详细证据和安全边界见 [`drill-records/2026-08-31-oceanbase-recovery.md`](drill-records/2026-08-31-oceanbase-recovery.md) 与 [`drill-records/2026-08-31-migration-v7.md`](drill-records/2026-08-31-migration-v7.md)。在具备客户端/镜像缓存和明确空目标库前，不对现有 `brad_pms` 执行恢复。

### Task 5：目标环境应用验收

- 本机后端 `8080`、前端 `57979` 已启动并使用非生产测试账号完成邮箱登录；Playwright 桌面和 390px 窄屏冒烟均通过，健康检查返回 migration=7。详细证据见 [`drill-records/2026-08-31-application-acceptance.md`](drill-records/2026-08-31-application-acceptance.md)。
- 生产目标环境仍需使用企业测试账号、正式 HTTPS/CORS/SMTP 配置复验；本地账号密码不写入文档或 CI。
