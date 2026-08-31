# 基础设施就绪状态

更新时间：2026-08-28

本文件只记录企业级基础设施状态，不代表业务功能已经完成生产验收。每完成一个基础设施任务，必须补充验证命令、结果和对应提交。

## 当前基线

| 检查项 | 当前状态 | 证据 |
| --- | --- | --- |
| OceanBase `brad_pms` | 已迁移至 V6 | 本地实例版本历史、表结构、索引、外键和行数校验通过；V6 审计清理索引已应用并完成二次幂等校验 |
| 后端回归 | 已通过 | `mvn -q test`，包含错误契约、鉴权和限流测试 |
| 前端回归 | 已通过 | `pnpm test`，79 项通过 |
| 前端类型与构建 | 已通过 | `pnpm typecheck`、`pnpm build` |
| 运行脚本语法 | 已通过 | `bash -n scripts/*.sh docker/*.sh` |
| 后端权限注解 | 已完成基础覆盖 | 控制器接口已扫描，受保护接口使用 `@RequirePermission` 或 `@IgnoreAuth` |
| 请求追踪/基础审计 | 已具备 | `X-Request-Id`、登录日志、操作日志；错误响应携带 requestId |
| 发布基线 | 已对齐 | 后端 `origin/main`=`c86f45e`、前端 `origin/main`=`bc2aab1` 均已包含功能分支提交；远程不使用 `master` |

## 仍需完成

| 优先级 | 缺口 | 影响 |
| --- | --- | --- |
| P1 | 真实 OceanBase 集成、备份恢复和故障演练尚未在本机完成 | 当前 Docker Hub 网络不可用，必须在 CI/部署机执行后才能发布 |
| P1 | CI、E2E、镜像扫描和 SBOM 已配置；本次集成流水线仍缺跨仓库只读凭据 | 需要在后端仓库配置 `PMS_FRONT_REPO_READ_TOKEN` 后重跑集成任务 |
| P2 | 尚未规划 Spring Boot 3/Java 21 升级 | 长期维护成本较高，但不是当前发布阻塞项 |

## 已收口能力

| 检查项 | 当前状态 | 证据 |
| --- | --- | --- |
| 错误响应合同 | 已完成 | `GlobalExceptionHandlerTest` 覆盖 400/401/403/409/422/500；响应统一包含 `code/msg/data/requestId` |
| 前端鉴权刷新 | 已完成 | Axios 仅对 HTTP 401 尝试刷新，刷新失败保留 redirect；业务错误使用后端 `msg` |
| 代理转发头 | 已完成 | `ForwardedHeaderFilter` 与 `server.forward-headers-strategy=framework` |
| 敏感接口限流 | 已完成 | 登录、刷新、重置、邀请、导入和图片上传按 IP+账号窗口限流，超限返回 429 |
| 敏感错误脱敏 | 已完成 | 鉴权解析日志不输出令牌内容；未知异常返回固定安全文案 |

## 执行规则

1. Task 1 和 Task 2 完成前，不把服务标记为生产就绪。
2. 每个任务先测试，再实现，再执行 Review；Review 通过后才能进入下一任务。
3. 任何脚本、配置或文档不得包含真实密码、JWT、令牌或密钥。
4. 本地空密码 OceanBase 只允许作为当前开发实例特例，生产环境必须使用非空强密码。

详细步骤见 [`../superpowers/plans/2026-08-28-enterprise-infrastructure-hardening.md`](../superpowers/plans/2026-08-28-enterprise-infrastructure-hardening.md)。

## 执行记录

| 任务 | 状态 | Review 证据 |
| --- | --- | --- |
| Task 0：基线盘点与文档状态对齐 | 已完成 | 后端 `mvn -q test` 通过；前端 Node 测试 77 项、`pnpm typecheck`、`pnpm build` 通过；脚本 `bash -n` 通过；后端提交 `98fe24d`，前端提交 `aa2864d`。 |
| Task 1：OceanBase 运行账号隔离 | 已完成 | 配置测试通过；Compose 解析通过；本地 OceanBase 已创建 `pms_app` / `pms_migrator`；应用账号建表被拒绝、迁移账号 DDL 通过；探针表已清理。 |
| Task 2：OceanBase 备份、恢复与版本升级 | 已完成 | 升级脚本 V1–V6 首次记录、逐语句检查点、命名锁、备份 gzip/SHA-256、现有 `brad_pms` V6 应用、二次幂等运行和 `verify-enterprise-migration.sh` 均通过；提交 `246fe4c`、复核修正 `b318a9a`。 |
| Task 3：统一安全边界、错误响应和请求链路 | 已完成 | 后端全量测试、前端 79 项 Node 测试、`pnpm typecheck`、`pnpm build` 通过；覆盖 400/401/403/409/422/500/429、requestId、刷新失败跳转、代理转发和敏感接口限流；提交见安全边界提交。 |
| Task 4：通知服务和上传文件生产化 | 已完成 | 后端 `mvn -q test`、脚本语法、Compose 配置校验通过；上传魔数/MIME/配额/路径穿越、图片接口和邀请通知测试通过；SMTP 通知启动检查与 `pms-uploads` 持久卷已接入。 |
| Task 5：容器与 Compose 运行时加固 | 已完成 | `ContainerHardeningTest`、后端全量测试、Compose 配置、脚本语法和 diff 检查通过；后端 UID 10001、非 root Nginx、健康检查、只读根文件系统、资源上限、网络隔离和安全响应头已接入。 |
| Task 6：可观测性、审计保留和 API 合同 | 已完成（静态/自动化验证） | `mvn -q test`、OpenAPI 校验通过；存活/就绪探针、私有 Actuator 端口、有限路由指标、UTC JSON 日志、审计脱敏与无长事务批量清理任务已接入。 |
| Task 7：集成 CI/CD、安全扫描与浏览器冒烟 | 本地门禁通过，远程集成待凭据后重跑 | Trivy Action 已固定为有效的 `v0.36.0`；前端 `package.json` 与 CI/集成工作流统一使用 `pnpm@9.15.9`；后端跨仓库容器检查在未检出前端时安全跳过，由集成流水线覆盖；后端 CI、前端 CI 正在重跑。远程集成工作流仍需要后端 secret `PMS_FRONT_REPO_READ_TOKEN` 读取私有前端仓库；本机 Playwright 无凭据按设计跳过。 |
| Task 8：生产演练与开源交付 | 文档完成，演练阻塞 | [`drill-records/2026-08-28-production-readiness.md`](drill-records/2026-08-28-production-readiness.md) 记录了通过项和 Docker Hub 超时证据；真实部署/备份恢复/故障演练待 CI 或部署机。 |

## 2026-08-31 合并后收口执行记录

### Task 1：发布基线

- 后端远程 `main`：`c86f45e`，已包含 `codex/oceanbase-migration` 的 `eb5c6a8`。
- 前端远程 `main`：`bc2aab1`，已包含 `codex/pms-design-system` 的 `2ea1730`。
- 后端集成工作流已固定前端提交 `bc2aab1`；远程主分支名称为 `main`，没有 `master`。

### Task 2：本地门禁与远程 CI

- 后端 `mvn -q test`：通过；`./scripts/validate-openapi.sh`：通过；`bash -n scripts/*.sh docker/*.sh`：通过。
- 前端 `pnpm test`：79 项通过；`pnpm typecheck`：通过；`pnpm build`：通过。
- H2 运行配置扫描和私钥材料扫描：通过。
- 远程 CI 的失败原因已定位并修正：Trivy Action 版本固定为有效的 `v0.36.0`；前端容器构建固定 `pnpm@9.15.9`，避免 pnpm 11 忽略构建脚本；后端单仓库测试不再因未检出前端而报 `NoSuchFileException`。
- 最新后端 CI `33350718216`、前端 CI `33350720703` 已触发验证；最新集成运行 `33350718220` 在凭据预检处按设计停止。后端仓库尚未配置 `PMS_FRONT_REPO_READ_TOKEN`，不能使用个人令牌替代。
