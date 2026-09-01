# 2026-08-28 企业版生产准备演练记录

## 结论

代码、配置和自动化门禁已完成静态收口；本机已尝试执行 Compose/OceanBase 从零演练，但 Docker 拉取公共镜像时因当前运行环境无法访问 Docker Hub 超时，因此真实容器、OceanBase、备份恢复和浏览器登录链路不能在本机宣称通过。该项应在具备 Docker socket、镜像代理和 OceanBase 资源的 CI/部署机补执行，完成后再将发布清单中的阻塞项改为通过。

## 已执行并通过

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 后端回归 | 通过 | `mvn -q test` |
| 前端回归 | 通过 | `pnpm test`，79 项通过 |
| 前端类型/构建 | 通过 | `pnpm typecheck && pnpm build` |
| OpenAPI | 通过 | `./scripts/validate-openapi.sh` |
| Shell 与差异检查 | 通过 | `bash -n scripts/*.sh docker/*.sh`、`git diff --check` |
| Compose 静态解析 | 通过 | `docker compose ... config --quiet`（注入临时示例变量） |
| 现有 OceanBase 只读预检 | 通过 | 使用本地 `pms-oceanbase` 实例执行 `enterprise-preflight.sh` 与 `verify-enterprise-migration.sh`；未写入业务数据 |
| 现有 `brad_pms` V6 迁移 | 通过 | 使用 `pms_migrator` 应用 `V6__audit_retention_indexes.sql`，第二次执行输出 `No pending migrations`；未删除或覆盖业务数据 |
| CI YAML 解析 | 通过 | Ruby YAML parser 检查 backend/frontend workflow 与 Dependabot |
| 浏览器用例编译 | 通过/跳过 | Playwright 用例可加载；无测试凭据时 2 项按设计跳过 |

## 未完成与原因

| 演练 | 状态 | 下一步 |
| --- | --- | --- |
| OceanBase 从零启动与 V1–V6 双次迁移 | 阻塞 | 在可访问 Docker Hub/镜像代理的 CI 执行 `integration.yml` |
| 备份、恢复与 RPO/RTO 计时 | 阻塞 | 使用真实 `brad_pms` 副本执行 `backup-oceanbase.sh`、`restore-oceanbase.sh`、`verify-backup.sh` |
| backend/frontend 故障重启 | 阻塞 | 在部署机停止容器，验证健康探针、卷数据和自动恢复 |
| Playwright 登录与页面链路 | 阻塞 | 在集成环境注入一次性管理员凭据后执行桌面/390px 用例 |

## 本机 Docker 证据

已使用临时非生产变量运行：

```text
docker compose ... up -d --build frontend
failed to resolve reference "docker.io/library/mysql:8.4"
... dial tcp ...:443: i/o timeout
```

随后 `docker compose ... ps -a` 无残留服务。未对现有 OceanBase、`brad_pms` 或业务数据执行删除、重建或覆盖操作。

## 复核人和发布门槛

- 代码复核：已完成静态审查，后续以 CI 的测试、Trivy、SBOM、OceanBase 集成和 Playwright 报告为准。
- 发布前必须补齐上表四项阻塞演练，并把结果、耗时、版本、验证人写回本文件。
- 在阻塞项完成前，不宣称“生产就绪”，仅可作为开发/演示版本使用。
