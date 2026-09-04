# 审计日志与权限变更追踪 Review

日期：2026-09-03

## 结论

核心实现通过代码 Review、后端/前端自动化门禁、真实本地 OceanBase 验证和 API 验收。审计链路已支持项目上下文、结果、原因、请求 ID、操作人、IP/User-Agent、脱敏摘要、项目/权限变更查询和详情查看；服务已用包含最新代码的构建重新启动。

## 实现范围

- 新增 `AuditEvent`、动作/资源目录和 `AuditSanitizer`，统一写入 `sys_operation_log`。
- V14 增加审计项目上下文、原因、结果、IP、User-Agent 字段及项目/操作人/动作/结果索引。
- 项目、节点、任务、成员、关注人、评论、附件、项目图片、角色权限和数据范围变更写入结构化成功事件。
- 保留 180 天审计保留策略，并记录 `AUDIT_RETENTION_RUN`；敏感字段、正文和二进制不进入摘要。
- 新增 `/admin/audit` 分页筛选和 `/admin/audit/{id}` 详情接口，接口只要求独立的 `admin:audit:read`。
- 前端审计工作台支持动作、资源、项目、结果、操作人、request ID、时间筛选和字段级 before/after 对比。
- 项目删除改为显式软删除 SQL，确保 `status=4` 与 `deleted=true` 同时落库；审计项目补查改为批量查询，避免列表 N+1。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 后端全量测试 | 通过：225 tests，0 failures，0 errors，1 skipped |
| 前端 Node 测试 | 通过：139 tests |
| 前端类型检查 | 通过：`npm run typecheck` |
| 前端生产构建 | 通过：`npm run build` |
| OpenAPI/发布一致性 | 通过：`validate-openapi.sh`、`release-consistency.test.sh` |
| 脚本与差异检查 | 通过：`bash -n`、两仓库 `git diff --check` |
| OceanBase V14 | 通过：迁移版本 14，企业表/列/索引/外键/角色/组织完整性校验通过 |
| 后端健康检查 | 通过：live/ready HTTP 200，`database=UP`、`migration=14` |
| 真实 API 验收 | 通过：匿名 401、普通成员 403、管理员项目筛选/详情/request ID 查询通过 |
| 验收数据清理 | 通过：`audit-acceptance-*` 项目共 5 条，5 条均为 `status=4, deleted=1` |

## Review 关注点

- 审计查询不套用 `project:read`，权限由 `admin:audit:read` 独立控制。
- 已删除项目不会出现在普通项目读取结果中，但历史审计仍能按项目 ID 查询并保留项目名称上下文。
- `from/to` 使用左闭右开区间；分页大小上限为 100；详情接口只读。
- `before/after` 仅保存业务白名单摘要；脱敏后再做长度控制，过长摘要不会通过截断泄露敏感值。
- 真实验收脚本不含凭据，账号和密码只从环境变量读取，位置为 [`scripts/audit-acceptance.sh`](../../scripts/audit-acceptance.sh)。

## 未完成的环境相关项

本次尝试浏览器层点击验收时，本机 Sky Computer Use 服务启动失败，因此未完成真实浏览器点击和 390×844 视口检查；前端 139 项自动化测试、类型检查和生产构建已通过。待 UI 自动化服务可用后，应补验管理员审计页筛选/分页/详情抽屉、普通成员 403 和窄屏布局。

本地前后端仍保持运行：前端 `http://127.0.0.1:5173`，后端 API `http://127.0.0.1:8080/api`。
