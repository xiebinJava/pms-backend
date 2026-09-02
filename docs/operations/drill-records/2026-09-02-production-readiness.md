# 2026-09-02 第一阶段生产就绪演练记录

本记录只保存本机目标 OceanBase 实例和本地应用验收结果，不代表任意企业生产环境已经完成认证。密码、JWT、令牌和 SMTP 内容均通过运行时环境注入，未写入记录。

## 1. 配置门禁

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 合成生产配置 | 通过 | `bash scripts/validate-production-config.test.sh`；缺失生产模式、通配 CORS、HTTP 公网地址、不完整 SMTP 均拒绝，完整合成配置通过 |
| 脚本安全性 | 通过 | `bash -n scripts/validate-production-config.sh scripts/validate-production-config.test.sh`；诊断只输出变量名/规则，不输出值 |
| 本机开发配置误用生产校验 | 按预期拒绝 | `.env.oceanbase.local` 保持开发设置；未将本地开发值伪装成生产签字 |

生产模板见 [`.env.production.example`](../../../.env.production.example)，正式部署必须由企业密钥管理器注入，并在目标环境重新执行：

```bash
./scripts/validate-production-config.sh --require-smtp --require-object-storage
```

## 2. OceanBase 迁移、备份与恢复

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 数据库 | 通过 | OceanBase CE 4.3.5，MySQL 兼容模式，`brad_pms` |
| 幂等升级第 1/2 次 | 通过 | V1–V10 均 checksum 校验，均返回 `No pending migrations`；未删除表 |
| 企业迁移验收 | 通过 | 必需表、列、索引、外键、唯一根组织、7 个内置角色和项目归属检查通过 |
| 完整性预检 | 通过 | 邮箱/英文名唯一性、用户/角色/组织引用、组织路径和项目归属均无异常 |
| 逻辑备份 | 通过 | `brad_pms-20260902T064253Z-v10.sql.zst`，178685 bytes；元数据含 35 张表的精确行数 |
| 压缩流与 SHA-256 | 通过 | `verify-backup.sh` 通过；SHA-256 `6c06ac5b2f694588b0e6a1ca3de57fcb683dc0f4139a8ec3ee3b2bb5f3196197` |
| 隔离恢复 | 通过 | 恢复到临时空库 `brad_pms_phase1_restore_1788331373`，再次通过企业迁移验收 |
| 关键表比对 | 通过 | 源库/恢复库 `sys_user/sys_org_unit/project/project_task/sys_operation_log` 行数均为 `21/9/2/7/21` |
| 临时库清理 | 完成 | 演练结束清理钩子删除临时库；`brad_pms` 未被覆盖 |

## 3. 故障边界

| 场景 | 结果 |
| --- | --- |
| 篡改备份 `.sha256` 后校验 | 按预期拒绝，退出码 1 |
| 使用 `--allow-empty-target` 恢复到已有 `brad_pms` | 按预期拒绝，退出码 2；未执行写入 |

## 4. 应用验收状态

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 后端健康检查 | 通过 | `GET /api/health/live` 与 `GET /api/health/ready` 均返回 HTTP 200；readiness 显示 `database=UP`、`migration=10` |
| API 冒烟 | 通过 | `PMS_SMOKE_BASE_URL=http://127.0.0.1:8080/api bash scripts/smoke-test.sh` |
| 冒烟登录身份回归 | 通过 | `bash scripts/smoke-test.test.sh`；邮箱标识发送 `email`，旧英文账号仍发送 `username` |
| 后端回归 | 通过 | `mvn -q test` 当前报告汇总 190 tests，0 failures、0 errors、1 skipped |
| 前端回归 | 通过 | `pnpm test` 112 项通过；`pnpm typecheck`、`pnpm build` 通过 |
| Playwright 桌面流程 | 通过 | `auth-and-project.spec.ts` 登录、项目/组织/权限入口和注销通过 |
| Playwright 390px 窄屏 | 通过 | 登录页无页面级横向溢出 |
| 使用手册录制 | 通过 | `manual-record.spec.ts` 通过；视频仅保存在本地 `test-results`，未提交占位素材 |

以上验收使用本机开发演示账号；真实企业域名、HTTPS 证书、SMTP、对象存储、监控告警、RPO/RTO 和故障联系人仍由部署企业注入并复验。

## 5. 可复现命令

```bash
# 迁移两次、结构验收和只读预检
PMS_OCEANBASE_VERIFY=true ./scripts/verify-oceanbase.sh

# 备份与完整性校验
PMS_BACKUP_DIR=/secure/backup/path ./scripts/backup-oceanbase.sh
./scripts/verify-backup.sh /secure/backup/path/<backup-file>.sql.zst

# 仅恢复到明确新建的空库；禁止指向现有生产库
./scripts/restore-oceanbase.sh --allow-empty-target /secure/backup/path/<backup-file>.sql.zst
./scripts/verify-enterprise-migration.sh
```
