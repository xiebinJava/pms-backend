# MySQL 备份、恢复与版本升级

本文是单企业本地部署的运维手册。每个企业只维护一个 `pms` 业务库。

## 账号与工具

- `MYSQL_USER`：应用运行账号，后端和 Flyway 使用它连接 `pms`。
- MySQL `root`：仅用于初始化、逻辑备份和创建隔离恢复库，不写入应用配置。
- 脚本优先使用宿主机 `mysql` / `mysqldump`。没有客户端时，使用固定版本的 `mysql:8.4` Docker 工具镜像。

所有密码通过环境变量注入，不能写入仓库、镜像或命令行参数。生产环境禁止使用空密码。

## 升级现有数据库

升级前停止应用写入，并完成逻辑备份：

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DB=pms
export MYSQL_USER=pms
export MYSQL_PASSWORD="$MYSQL_PASSWORD"

./scripts/enterprise-preflight.sh
./scripts/backup-mysql.sh
```

然后启动新版本后端。Flyway 只会执行缺失的版本。完成后：

```bash
./scripts/verify-enterprise-migration.sh
```

## 备份

```bash
./scripts/backup-mysql.sh
```

默认输出到仓库 `backups/`，文件名类似 `pms-<UTC时间>-v<schema版本>.sql.gz`（本机有 `zstd` 时为 `.sql.zst`），并附带 SHA-256 和精确行数元数据。

```bash
./scripts/verify-backup.sh /path/to/pms-<UTC时间>-v41.sql.gz
```

## 恢复

恢复只写入已经存在的空库。目标名为 `pms` 时必须额外设置 `PMS_ALLOW_PRODUCTION_RESTORE=true`，默认拒绝。

```bash
export MYSQL_DB=pms_restore_YYYYMMDD
./scripts/restore-mysql.sh --allow-empty-target /path/to/pms-<UTC时间>-v41.sql.gz
```

恢复完成后必须通过企业表、索引、外键、根组织和项目归属校验，再决定是否切换应用连接。
