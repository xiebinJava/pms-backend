# Release readiness drill record（2026-09-03）

本记录只描述本机已经执行的证据，不代表目标企业生产环境签字。所有连接均使用本机现有
OceanBase CE 4.3.5 本地容器；密码、JWT 和备份内容不写入记录。

## 结果摘要

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| OceanBase 运行状态 | 通过 | 容器运行中，端口 2881 可用 |
| 账号分权 | 通过 | `pms_app` 仅有 brad_pms DML；`pms_migrator` 有 brad_pms 迁移 DDL |
| V1–V12 第一次升级 | 通过 | V1–V11 checksum 校验，执行 V12 feedback_center |
| V1–V12 第二次升级 | 通过 | 输出 `No pending migrations. All migration checksums verified.` |
| 企业结构校验 | 通过 | `verify-enterprise-migration.sh` 全部通过 |
| 数据完整性预检 | 通过 | `enterprise-preflight.sh` 全部通过，未改数据 |
| 备份完整性失败边界 | 通过 | 临时篡改副本被 `verify-backup.sh` 拒绝 |
| 生产库恢复保护 | 通过 | `restore-oceanbase.sh` 拒绝目标 `brad_pms` |
| 非空目标保护 | 通过 | `restore-oceanbase.sh` 拒绝 `information_schema` |
| 当前 V12 逻辑备份 | 通过 | 复用 OceanBase CE 容器内置的兼容 `mysqldump`，生成 `.sql.zst`、SHA-256 和精确行数元数据 |
| 隔离空库恢复 | 通过 | 管理员创建一次性隔离库，迁移账号仅获该库权限；恢复后结构、预检和精确行数均通过 |

## 已执行命令

```bash
# 只读结构和数据范围预检（使用 pms_app）
bash scripts/verify-enterprise-migration.sh
bash scripts/enterprise-preflight.sh

# 真实 OceanBase V1–V12 双次升级（使用 pms_migrator）
PMS_OCEANBASE_VERIFY=true bash scripts/verify-oceanbase.sh

# 备份、恢复与行数比对（目标库为一次性隔离库）
bash scripts/backup-oceanbase.sh
bash scripts/verify-backup.sh <v12-backup>
bash scripts/restore-oceanbase.sh --allow-empty-target <v12-backup>
bash scripts/verify-enterprise-migration.sh
bash scripts/enterprise-preflight.sh

# 失败边界（临时副本、生产库保护、非空目标保护）
bash scripts/verify-backup.sh <tampered-temporary-backup>
OCEANBASE_DATABASE=brad_pms bash scripts/restore-oceanbase.sh --allow-empty-target <backup>
OCEANBASE_DATABASE=information_schema bash scripts/restore-oceanbase.sh --allow-empty-target <backup>
```

实际执行通过的命令输出未包含任何密码或令牌。`brad_pms` 未被删除、重建、清空或恢复覆盖。

## 阻塞项与解除方式

1. 目标企业可直接使用 OceanBase 官方客户端/容器中的 `mysqldump`；若使用独立工具镜像，需提前在
   内网镜像仓库缓存并通过 `PMS_MYSQL_TOOL_IMAGE` 指定，避免发布时依赖公网下载。
2. 由 OceanBase 管理员创建唯一隔离库（例如 `pms_restore_<run-id>`），仅授予迁移账号该库权限，
   再按 `docs/operations/oceanbase-backup-restore.md` 完成恢复、行数比对和恢复后 V1–V12 校验。
3. 目标企业执行前确认 `.env` 中 `OCEANBASE_ROOT_PASSWORD` 非空，并将本地/生产环境文件权限设为 600；
   本机当前运行库已使用现有账号完成应用级验证，但不应据此重跑 Compose 账号初始化。

## 结论

迁移幂等、V12 结构和完整性门禁，以及 V12 备份、校验、隔离库恢复和精确行数比对，已在真实本机
OceanBase 通过。目标企业仍需使用自己的备份介质、空目标库、RPO/RTO 和审批流程重新演练；本记录不等同于
生产签字。
