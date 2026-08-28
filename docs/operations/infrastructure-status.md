# 基础设施就绪状态

更新时间：2026-08-28

本文件只记录企业级基础设施状态，不代表业务功能已经完成生产验收。每完成一个基础设施任务，必须补充验证命令、结果和对应提交。

## 当前基线

| 检查项 | 当前状态 | 证据 |
| --- | --- | --- |
| OceanBase `brad_pms` | 已迁移至 V5 | 本地实例版本历史、表结构、索引、外键和行数校验通过 |
| 后端回归 | 已通过 | `mvn -q test`，71 项通过 |
| 前端回归 | 已通过 | Node 测试 77 项通过 |
| 前端类型与构建 | 已通过 | `pnpm typecheck`、`pnpm build` |
| 运行脚本语法 | 已通过 | `bash -n scripts/*.sh docker/*.sh` |
| 后端权限注解 | 已完成基础覆盖 | 控制器接口已扫描，受保护接口使用 `@RequirePermission` 或 `@IgnoreAuth` |
| 请求追踪/基础审计 | 已具备 | `X-Request-Id`、登录日志、操作日志 |
| 工作区状态 | 已确认 | 后端和前端无业务代码未提交修改 |

## 仍需完成

| 优先级 | 缺口 | 影响 |
| --- | --- | --- |
| P0 | 业务错误体与 HTTP 状态可能不一致 | 前端可能误判 401/403 或重复提示 |
| P0 | 通知器没有默认生产实现 | 邀请和找回密码无法可靠送达 |
| P0 | 上传目录未接入持久卷和文件安全校验 | 容器重建可能丢文件，存在伪造类型风险 |
| P1 | Compose 缺少完整健康检查、资源限制和前端非 root | 故障恢复和运行隔离能力不足 |
| P1 | 无结构化日志、指标、告警和审计清理策略 | 问题定位和长期存储成本不可控 |
| P1 | 无 OpenAPI、真实 OceanBase 集成 CI、E2E、镜像扫描和 SBOM | 开源交付缺少自动质量门禁 |
| P2 | 尚未规划 Spring Boot 3/Java 21 升级 | 长期维护成本较高，但不是当前发布阻塞项 |

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
| Task 2：OceanBase 备份、恢复与版本升级 | 已完成 | 升级脚本 V1–V5 首次记录、重复执行校验、迁移锁、备份 gzip/SHA-256、隔离库恢复和 `verify-enterprise-migration.sh` 均通过；后端提交 `246fe4c`。 |
