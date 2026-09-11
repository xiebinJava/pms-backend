# MySQL V1–V7 迁移幂等与结构校验记录（2026-08-31）

> 历史记录说明：本文记录 2026-08-31 当时完成的 V1–V7 验收。随后 V8–V10 已在本机 `pms` 完成并复核；当前发布基线以 V1–V10 和 [`infrastructure-status.md`](../infrastructure-status.md) 为准。

## 范围

针对本机 `pms` 执行版本化迁移复核，确认代码中的 V1–V7 脚本、数据库迁移历史和关键企业约束一致。不删除业务表，不覆盖业务数据。

## 执行环境

- 数据库：MySQL 8，数据库 `pms`
- 迁移账号：`pms_migrator`（仅从本地环境变量注入）
- SQL 客户端：运行中的 MySQL 容器内 `/usr/bin/mysql`
- 迁移脚本：`scripts/enterprise-preflight.sh`

## 验证结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 数据库迁移历史 | 通过 | `pms_schema_migration_history` 包含 V1、V2、V3、V4、V5、V6、V7；V7 为 `email_identity` |
| 升级第 1 次执行 | 通过 | V1–V7 均输出 `already applied (checksum verified)`，最终输出 `No pending migrations` |
| 升级第 2 次执行 | 通过 | 再次输出 V1–V7 checksum verified 和 `No pending migrations` |
| 企业预检 | 通过 | `scripts/enterprise-preflight.sh` 检查表、索引、外键、邮箱归一化唯一性、组织路径和项目归属全部通过 |
| 迁移后校验 | 通过 | `scripts/verify-enterprise-migration.sh` 检查企业表、字段、索引、外键、根组织、内置角色和项目归属全部通过 |
| 数据安全边界 | 通过 | 升级脚本输出 `No database or table was dropped`；本次没有执行删除、截断或恢复操作 |

## 复核结论

当前代码仓库的 V1–V7 迁移脚本与本机 `pms` 版本历史一致，V7 邮箱归一化唯一索引已落库。备份、空库恢复和故障演练已在同一发布日的独立记录中完成；生产环境仍需保存企业自己的备份介质、RPO/RTO 和联系人。
