# 反馈中心实现 Review（2026-09-02）

## 结论

V1 反馈中心已按产品规格完成前后端闭环，保留“项目评论”和“反馈工单”两套独立关系。后端负责权限、数据范围、上下文一致性、状态机、幂等、乐观锁和审计；前端提供统一的反馈入口、筛选、提交、详情时间线及管理员处理控件。

## 对照检查

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 反馈类型和优先级 | 通过 | `FeedbackType`、`FeedbackPriority` 与前端中英文标签覆盖 QUESTION/BUG/FEATURE/UX/DATA/PERMISSION/OTHER 及四档优先级 |
| 状态机 | 通过 | `FeedbackStatus.canTransition` 覆盖主流程、异常结论和重开；非法流转由服务端拒绝 |
| 角色与权限 | 通过 | `feedback:read`/`feedback:write`/`feedback:manage` 已加入权限种子、控制器注解和前端路由；服务层再次校验本人/管理员可见范围 |
| 上下文一致性 | 通过 | 项目、任务、节点按项目层级校验；跨项目任务或节点会返回业务错误 |
| 幂等 | 通过 | `reporter_id + client_request_id` 唯一索引；重复提交返回已有工单详情，不重复写入 |
| 并发更新 | 通过 | `version` 乐观锁 + 行锁读取；更新影响行数为 0 时返回 409 |
| 历史和审计 | 通过 | 创建、更新、重开均追加 `feedback_history` 并写 `sys_operation_log`；历史为追加式 |
| 数据库 | 通过 | `V12__feedback_center.sql` 创建两张表、外键、索引、软删除和版本字段；Testcontainers MySQL 迁移测试通过，运行时以 MySQL 为准 |
| 前端体验 | 通过 | `/feedback` 页面包含筛选表格、提交弹窗、详情抽屉、历史时间线、管理员处理控件和窄屏样式；使用现有 PMS 视觉 token |
| 文档 | 通过 | 业务规范、运行/发布文档及前端使用手册已补充反馈中心和 V1–V12 基线 |

## 验证命令

```bash
mvn -q -Dtest=FeedbackServiceTest,FeedbackStatusTest,FeedbackPermissionAnnotationTest,EnterpriseSchemaMigrationTest,EnterpriseDataMigrationTest,HealthControllerTest test
mvn -q test
cd ../pms-front
pnpm test
pnpm typecheck
pnpm build
git diff --check
```

以上聚焦后端、后端全量、前端测试、类型检查、生产构建和差异空白检查均通过。前端测试包含新增 `feedback.test.mjs`，后端新增服务层幂等/状态/版本测试。

## V1 有意延后

附件、反馈评论线程、重复反馈关联、反馈转任务、通知编排和 SLA 统计不属于本期核心闭环，已在产品规格中明确列为后续增量；不以占位媒体或未实现接口冒充已完成能力。

## 外部环境注意事项

本地自动化使用 Testcontainers MySQL 验证迁移脚本，生产运行仍必须使用 MySQL。目标企业上线前仍需在真实 MySQL、正式 HTTPS/CORS/SMTP、企业测试账号和浏览器环境中重复执行发布验收；本次未提交任何真实凭据。

## 2026-09-03 跟进审查

针对首次 review 发现的边界问题已完成修复：

- 反馈管理员现在沿用 `feedback:manage` 的组织/项目数据范围；只有全公司范围可查看全部工单，提交人始终可读取自己的工单。
- 反馈权限在模块内按 `manage → write → read` 继承，其他权限族保持精确匹配；前端路由、提交和重开按钮使用同一套有效权限判断。
- 负责人候选人改为反馈专用、按范围过滤的接口，不再隐式要求 `admin:user:read`；前端不再提供“清空即取消分派”的歧义操作。
- 任务与节点同时关联时强制校验二者关系；重复保存关闭工单保留原关闭时间，重开未填写说明会清空旧处理说明。
- 操作审计改为白名单快照，排除标题、正文、来源 URL、幂等键和处理说明原文；反馈提交人查询新增 `(reporter_id, created_at, id)` 索引。
- 提交表单按项目加载节点和任务，并在选择任务后自动同步节点，减少跨上下文提交错误。

本次跟进验证：

```bash
mvn -q -Dtest=FeedbackServiceTest,AuthorizationServiceTest,FeedbackPermissionAnnotationTest test
cd ../pms-front
pnpm test
pnpm typecheck
pnpm build
cd .
mvn -q test
git diff --check
cd ../pms-front
git diff --check
```

以上聚焦后端回归、后端全量测试、前端测试、类型检查、生产构建和两仓库差异空白检查均通过；真实 MySQL/浏览器验收仍属于发布前环境验证，本地 review 不代替目标企业环境验收。
