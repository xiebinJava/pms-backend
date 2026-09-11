# 通知中心与任务临期/逾期提醒 Review

日期：2026-09-04

## Review 范围

本次 Review 覆盖通知中心分页、顶部铃铛入口、任务临期/逾期扫描、V15 去重结构、项目生命周期过滤、权限门禁、前端文案和业务规则/使用手册同步。

## 已核对的实际规则

- 通知中心和通知相关接口要求 `project:read`；在当前项目读取模型中，持有该权限的标准账号可读取全公司未删除项目的项目内容，但这不等于拥有项目写权限。
- 临期状态窗口是 `[today, today + N]`，默认 `N=7`；只有 `dueDate = today + N` 的当地 09:00 扫描写入临期通知。
- 逾期只在 `dueDate = today - 1` 的当地 09:00 扫描写入一次；到期当天不发临期，历史逾期、窗口内部新建/改期/重开和漏跑均不补发。
- 只通知当前任务负责人；换负责人不撤回旧通知、不修改旧通知已读状态，后续扫描只面向新负责人。
- 第一阶段提醒只写入 `user_notification`，不调用出站 Webhook，也不发送邮件、飞书或企业微信。
- V15 使用可空 `dedupe_key` 和 `(user_id, type, dedupe_key)` 唯一索引；旧通知保持 `NULL`，提醒只标记已读、不软删除。
- 列表、分页、顶部预览和未读数在 SQL 中先排除删除项目，再分页或计数；终止项目历史通知仍可读，删除项目按不存在处理。
- 提醒作业默认关闭，提前天数限制为 1–30，日期使用 `Asia/Shanghai` 等配置时区；工作台时区差异按已知边界保留。

## 实现核对

- `TaskReminderService` 只查询未完成、当前负责人有效（未停用且未逻辑删除）、任务/项目未删除且项目可运行的边界日期任务。
- `TaskReminderJob` 使用 `@ConditionalOnProperty`，默认不会注册调度作业；使用配置时区计算扫描日期。
- `NotificationService.emitInApp` 独立写入站内表并捕获重复键，不经过会发布 Webhook 的 `emit()` 事件路径。
- `UserNotificationMapper` 的列表、分页和未读数均包含项目删除/生命周期 SQL 条件；没有保留“先取 80 条、再由 Java 丢弃”的路径。
- `GET /notifications/page` 受 `project:read` 保护，非法 `type` 返回 400；前端筛选互斥、切换回到第 1 页、翻页保留筛选，并使用文本插值渲染。
- `start-local-mysql.sh` 已补充传递本地开发环境、通知启动检查和提醒配置，避免使用应用的生产默认值启动本地服务。

## 验证证据

| 检查 | 结果 |
| --- | --- |
| `mvn -q test` | 235 tests，0 failures，0 errors，1 skipped |
| `pnpm test` | 143 tests，0 failures |
| `pnpm typecheck` | 通过 |
| `pnpm build` | 通过，Vite 生产构建完成 |
| `./scripts/validate-openapi.sh` | 通过 |
| `bash scripts/release-consistency.test.sh` | 通过，当前迁移基线 V1–V15 |
| `bash -n scripts/*.sh docker/*.sh` | 通过 |
| `git diff --check`（前后端） | 通过 |
| MySQL 幂等升级 | V1–V15 checksum 全部 verified；第二次执行无待迁移版本，未删除业务表 |
| 企业迁移校验/完整性预检 | 通过；V15 去重索引存在；无数据变更 |
| 本机 API | `/api/health/ready` 返回 200，`status=UP`、`database=UP`、`migration=15`；未登录访问 `/api/notifications/page` 返回 401 |
| 前端路由冒烟 | `5173/notifications` 可加载；无登录会话时按预期跳转登录页。CUA 服务不可用，使用 Playwright 完成该路由的实际页面冒烟；未伪造登录态宣称已完成认证页面验收 |

## 结论与未覆盖边界

本次代码和文档规则已对齐，当前本机开发环境可以继续联调。提醒作业仍保持默认关闭，因此本轮没有在共享开发库主动制造临期/逾期通知；真正打开作业前仍需完成项目权限收口、SQL 可见性验收，并在受控测试数据中验证两个边界日期、换负责人和无 Webhook 结果。生产目标企业还需要使用真实账号、配置和自己的浏览器验收流程复验，不能把本机结果当作生产签字。

由于当前工具环境没有可调度的独立 reviewer 子代理，本记录采用本地结构化 Review，并以测试、迁移校验、脚本校验和运行时探针作为证据。
