# 2026-09-02 组织与导入可靠性演练记录

## 范围

本记录覆盖第二阶段：组织负责人和员工主归属独立、组织变更历史、导入幂等、失败回滚和错误报告下载。环境为本机 MySQL 8.4，数据库 `pms`；凭据只从未入库的本地环境变量注入。

## 执行结果

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 后端全量回归 | 通过 | `mvn -q test`：194 tests，0 failures，0 errors，1 skipped |
| 前端回归 | 通过 | `pnpm test`：112 passed |
| 前端类型/构建 | 通过 | `pnpm typecheck`、`pnpm build` |
| OpenAPI/脚本 | 通过 | `./scripts/validate-openapi.sh`、`bash -n scripts/*.sh docker/*.sh` |
| V1–V11 升级第 1 次 | 通过 | `pms_migrator` 校验 V1–V10 checksum，执行 V11 |
| V1–V11 升级第 2 次 | 通过 | 所有版本 checksum 校验通过，输出 `No pending migrations` |
| 企业迁移校验 | 通过 | `scripts/verify-enterprise-migration.sh`：表、列、索引、外键、单根组织和项目归属均通过 |
| 数据完整性预检 | 通过 | `scripts/enterprise-preflight.sh`：邮箱唯一性、用户归属、角色权限、组织路径和项目关联均无孤儿数据 |

## 变更摘要

- V11 新增 `sys_org_unit_history`，以追加方式记录组织创建、更新、移动和停用的前后快照、操作人、请求 ID 和时间。
- `sys_import_job` 新增 `failure_reason` 与 `failed_at`；导入提交按 job ID 幂等，失败批次不保留部分业务写入。
- 新增 `GET /admin/org/{id}/history` 和 `GET /admin/import/{jobId}/errors.csv`，前端组织页和导入页均已接入。
- 组织负责人仍由 `sys_org_unit.leader_user_id` 独立维护，员工主归属仍由 `sys_user_position.is_primary` 维护，二者不会互相覆盖。

## Review 结论

全量测试最初发现健康检查测试仍断言 V10，已同步为 V11 后通过。失败导入状态写入曾与行锁产生嵌套事务竞争，已改为提交事务结束后使用独立事务标记失败，避免锁超时。第一次运行因本机缺少 `mysql:8.4` 工具镜像而无法拉取，随后复用已存在的 MySQL 镜像内 `mysql` 完成相同校验。

本记录只证明本机开发环境演练通过；目标企业仍需使用自己的账号、备份介质和运维联系人复验。
