# OceanBase 备份恢复演练记录（2026-08-31）

## 范围

本次仅针对本机已有 `brad_pms` 做只读连通性与迁移验收预检；不覆盖、不删除现有数据库，也不创建生产恢复目标库。

## 已执行

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 读取 `.env.oceanbase.local` | 通过 | 仅确认变量名存在，未输出任何密码或 JWT |
| 通过已有 OceanBase 容器执行 `SELECT 1` | 通过 | `pms-oceanbase` 内 `/usr/bin/obclient` 返回 `1` |
| `enterprise-preflight.sh` | 通过 | 使用 OceanBase 官方镜像内 `/usr/bin/obclient` 对 `brad_pms` 执行只读预检 |
| `verify-enterprise-migration.sh` | 通过 | 结构、索引、外键、邮箱唯一性、组织路径和项目归属全部通过 |
| `backup-oceanbase.sh` | 通过 | `/u01/obclient/bin/mysqldump` 生成 `brad_pms-20260831T094114Z-v7.sql.gz`；SHA-256=`ff7439275e34939ac78775511faf1d36dbbb8ff2e10f0fe3ffb27220f740f5af`，元数据使用精确行数 |
| 空库恢复与数据比对 | 通过 | 恢复到隔离库 `brad_pms_restore_20260831b` 后结构校验通过；源库/恢复库用户、组织、项目、任务、审计行数均为 21/9/2/6/18；验收后已删除本次演练创建的临时恢复库 |
| 故障/就绪演练 | 通过 | 短暂停止并恢复 `pms-oceanbase`；期间 liveness 0.13 秒返回 200，readiness 3.02 秒返回 503；恢复后 readiness 返回 200 且 migration=7 |

## 阻塞与后续动作

1. 在具备 Docker Hub 镜像缓存或已安装 MySQL/OceanBase 客户端的部署机执行：
   `enterprise-preflight.sh`、`verify-enterprise-migration.sh`。
2. 设置权限为 `700` 的备份目录执行 `backup-oceanbase.sh`，保存 `.sha256` 与 `.meta`。
3. 仅创建明确的空库（例如 `brad_pms_restore`）后执行 `restore-oceanbase.sh --allow-empty-target`，再重复迁移验收。
4. 记录备份文件名、SHA-256、schema 版本、恢复耗时和就绪探针响应；禁止在记录中写入密码、JWT 或 Token。

本次演练完成了备份、校验、隔离空库恢复、数据比对和容器恢复。OceanBase profile 已将连接超时/校验超时默认收敛为 3 秒/1 秒，故障期间 readiness 在 3.02 秒内返回 503，避免监控探针长时间阻塞。生产发布仍需保存实际备份位置、RPO/RTO、对象存储卷快照和故障联系人。
