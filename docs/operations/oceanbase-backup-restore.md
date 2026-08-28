# OceanBase 备份、恢复与版本升级

本文是单企业本地部署的运维手册。每个企业只维护一个 `brad_pms` 业务库，应用账号和迁移账号分离；本文不包含多租户数据隔离方案。

## 账号与工具

- `pms_app`：应用运行账号，只授予业务库的增删改查权限，不能建表或修改数据库结构。
- `pms_migrator`：发布/升级账号，授予迁移需要的 DDL 权限；不授予 `DROP`，避免脚本误删业务对象。
- `root@sys`：仅用于初始化账号、创建空的恢复库和 DBA 操作，不写入应用配置。
- 脚本优先使用宿主机 `mysql`/`obclient`。没有客户端时，备份和恢复可使用固定版本的 `mysql:8.4` Docker 工具镜像；OceanBase 官方镜像自带的 `/u01/obclient/bin/mysqldump` 也可用于离线演练。

所有密码通过环境变量注入，不能写入仓库、镜像或命令行参数。生产环境禁止使用空密码。

## 升级现有数据库

升级前停止应用写入，并确认迁移账号已经准备好：

```bash
export OCEANBASE_HOST=127.0.0.1
export OCEANBASE_PORT=2881
export OCEANBASE_DATABASE=brad_pms
export OCEANBASE_USER=pms_migrator
export OCEANBASE_PASSWORD="$PMS_MIGRATOR_PASSWORD"
```

执行只读预检后运行升级：

```bash
./scripts/enterprise-preflight.sh
./scripts/oceanbase-upgrade.sh
./scripts/verify-enterprise-migration.sh
```

`oceanbase-upgrade.sh` 会：

1. 创建迁移历史、逐语句检查点和带 `pms-schema-upgrade` 名称的租约锁（默认 30 分钟），同一时间只允许一个升级进程；释放锁时校验 owner token，避免误清理其他进程的锁。
2. 读取 `flyway_schema_history`。旧 Flyway 记录没有本工具的 SHA-256 检查点时默认暂停，运维人员核对发布包后显式设置 `PMS_ACCEPT_FLYWAY_BASELINE=true` 才能记录，避免静默接受被替换的脚本。
3. 对每个脚本保存 SHA-256，并为每条已成功执行的语句保存检查点；脚本内容被修改或中途失败时立即失败，已完成语句不会重复执行。
4. 只执行缺失版本，不执行 `DROP DATABASE` 或 `TRUNCATE`。

同一命令连续执行两次是安全的；第二次应显示所有版本已校验且没有待执行版本。升级失败时保留日志，先恢复到新库并完成预检，再切换应用连接，禁止直接删除生产表。

## 生成备份

```bash
export OCEANBASE_USER=pms_migrator
export OCEANBASE_PASSWORD="$PMS_MIGRATOR_PASSWORD"
export PMS_BACKUP_DIR=/var/backups/pms
./scripts/backup-oceanbase.sh
```

每次备份生成三个文件：

- `brad_pms-<UTC时间>-v<schema版本>.sql.gz`（或本机有 `zstd` 时的 `.sql.zst`）
- 同名 `.sha256` 校验文件
- 同名 `.meta` 元数据（库名、脚本版本、生成时间和表级行数统计）

备份使用一致性事务快照、跳过表锁/删除表语句和十六进制二进制值，不记录密码；默认使用迁移账号读取结构并支持恢复所需的建表权限。备份目录权限应为 `700`，并按企业策略加密、异地复制和定期清理。生成后必须执行：

```bash
./scripts/verify-backup.sh /var/backups/pms/brad_pms-<UTC时间>-v5.sql.gz
```

项目图片不在数据库备份文件中。生产环境还必须对 Compose 的 `pms-uploads` 卷或
`PMS_UPLOAD_DIR` 对应目录做同一时间点的文件级/卷级快照，并记录快照 ID；数据库和图片卷必须成对恢复。
恢复演练时把卷挂载到临时环境，再用图片 URL 抽样确认文件可读。

## 恢复演练与回滚

恢复只接受显式的空目标库，避免覆盖现有业务数据。先由 DBA 创建临时库并授权 `pms_migrator`，再执行：

```bash
export OCEANBASE_DATABASE=brad_pms_restore_YYYYMMDD
export OCEANBASE_USER=pms_migrator
export OCEANBASE_PASSWORD="$PMS_MIGRATOR_PASSWORD"
./scripts/restore-oceanbase.sh --allow-empty-target \
  /var/backups/pms/brad_pms-<UTC时间>-v5.sql.gz
./scripts/verify-enterprise-migration.sh
```

恢复脚本会先验证压缩流和 SHA-256，再确认目标库没有表；目标名为 `brad_pms` 时必须额外显式设置 `PMS_ALLOW_PRODUCTION_RESTORE=true`，默认拒绝。恢复完成后必须通过企业表、索引、外键、根组织和项目归属校验，抽样比对用户、组织、项目、任务和审计日志数量后才能切换应用连接。

生产回滚路径是“备份 → 新库恢复 → 只读验收 → 切换连接”，不在原库执行破坏性回滚。恢复演练用的临时库确认完成后由 DBA 按库名清理，并保留校验日志。

## 发布验收记录

本地 OceanBase 4.3.5 演练已验证：

- 现有 `brad_pms` 的 V1–V6 被记录并可重复校验；第二次升级无待执行版本。
- 备份压缩流和 SHA-256 校验通过，文件名带 `v5` 版本标识。
- 备份恢复到隔离空库后，企业迁移校验通过；抽样数据（2 个项目、21 个用户、9 个组织、6 个任务）存在。

另外，在专用临时库 `brad_pms_upgrade_smoke_20260828` 中使用 `pms_migrator` 完成了从空库执行 V1–V5、再次执行升级（输出 `No pending migrations`）和清理临时库的回归；共生成 28 张表，未触碰现有 `brad_pms`。V6 在现有 `brad_pms` 上单独应用并完成了二次幂等校验。

以上是开发环境证据，生产发布仍需按目标环境保存备份位置、校验值、执行人和回滚联系人。
