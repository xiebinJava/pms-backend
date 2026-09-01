# 2026-09-01 发布收口演练记录

本记录只保存本机目标 OceanBase 实例和本地应用验收结果，不代表任意企业生产环境已经完成认证。所有密码、JWT 和令牌均通过运行时环境注入，未写入记录。

## 环境

| 项目 | 结果 |
| --- | --- |
| 数据库 | OceanBase CE 4.3.5，`brad_pms`，MySQL 兼容模式 |
| 迁移账号 | `pms_migrator`（仅用于升级/备份/恢复） |
| 应用账号 | `pms_app`（只读完整性预检和本地应用运行） |
| 后端 | Java 17，`127.0.0.1:8080`，OceanBase profile |
| 前端 | Vite，`127.0.0.1:57979` |

## OceanBase 迁移与结构校验

| 操作 | 结果 | 证据 |
| --- | --- | --- |
| 连通性 | 通过 | `SELECT 1` 返回 1 |
| 幂等升级第 1 次 | 通过 | V1–V10 checksum 全部校验，`No pending migrations` |
| 幂等升级第 2 次 | 通过 | V1–V10 再次校验，`No pending migrations` |
| 企业迁移校验 | 通过 | 必需表、列、索引、外键、根组织、7 个内置角色和项目归属均通过 |
| 完整性预检 | 通过 | 唯一邮箱、用户/角色/组织引用、组织路径和项目归属均无异常 |

执行入口：

```bash
PMS_OCEANBASE_VERIFY=true bash scripts/verify-oceanbase.sh
```

该命令未删除数据库或表。

## 备份、恢复与数据比对

| 操作 | 结果 | 证据 |
| --- | --- | --- |
| 逻辑备份 | 通过 | `brad_pms-20260901T101226Z-v10.sql.zst` |
| 压缩流与 SHA-256 | 通过 | `verify-backup.sh` 通过；SHA-256 `adc0278be6cd9a0d3d46202d1e60f3caf96c90f554cec4d2cd90e82f902157ab` |
| 精确行数元数据 | 通过 | 元数据 36 行，包含全部 31 张表；关键表：`sys_user=21`、`sys_org_unit=9`、`project=2`、`project_task=7`、`sys_operation_log=20` |
| 隔离恢复 | 通过 | 恢复到 `brad_pms_restore_20260901b` 空库后通过企业迁移校验 |
| 源库/恢复库比对 | 通过 | 上述 5 张关键表行数逐表一致（21/9/2/7/20） |
| 临时库清理 | 完成 | 已删除 `brad_pms_restore_20260901b`；`brad_pms` 仍存在且未被覆盖 |

备份脚本修复了两个一致性问题：迁移版本按数字排序，避免 V10 被标记成 V9；表级 `COUNT(*)` 查询不再消费元数据输入流，确保所有表都写入 `.meta`。

## 应用与浏览器验收

| 操作 | 结果 | 证据 |
| --- | --- | --- |
| 后端 liveness | 通过 | `GET /api/health/live` 返回 HTTP 200，`status=UP` |
| 后端 readiness | 通过 | `GET /api/health/ready` 返回 HTTP 200，`database=UP`、`migration=10` |
| 邮箱登录 | 通过 | 本地非生产演示账号登录接口返回 HTTP 200；令牌未写入记录 |
| Playwright 桌面主流程 | 通过 | `tests/e2e/auth-and-project.spec.ts`，2 项通过（含登录、项目/组织/权限入口、注销和 390px 窄屏） |
| Playwright 使用手册录制 | 通过 | `tests/e2e/manual-record.spec.ts`，1 项通过；视频只写入本地 `test-results`，未作为占位素材提交 |

为避免浏览器默认英文 locale 导致中文断言失配，E2E 验收固定 `locale: zh-CN`；这不改变产品的中英文切换能力。

## 当前阻塞与后续动作

1. 跨仓库 CI：后端工作流已固定前端提交 `73714231bb6794126b96c3b0c14fe0b5715455f7`；当前已恢复 GitHub 认证，但后端仓库仍需添加仅 `pms-front` Contents: Read 的 `PMS_FRONT_REPO_READ_TOKEN` Secret 后重跑 `integration-and-e2e`。
2. 生产配置：仍需目标企业注入强 JWT、OceanBase 应用/迁移密码、SMTP、HTTPS、受限 CORS、对象存储和监控告警，并保存 RPO/RTO、备份介质和故障联系人。
3. 本记录是本机演练证据；生产环境必须按同一命令和清单重新执行，不能直接把本机通过视为生产通过。

## 发布审阅遗留项

- `sys_user.email` 的历史 V1 列定义仍小于业务规范允许的 320 字符上限。正式扩大列长度需要单独的后续迁移（并同步健康检查、升级脚本和回滚说明），本次不隐式修改 V1–V10 基线。
- `enterprise-preflight.sh` 当前依赖 V7 之后的归一化字段；从旧库升级时，必须先执行只读的 V7 前置检查或调整 runbook 顺序，不能在 V6 库上直接把现有 preflight 当作全量前置条件。
- 本次已补充导入预览的批次内英文名（不区分大小写）重复校验，并由后端全量测试回归覆盖。
