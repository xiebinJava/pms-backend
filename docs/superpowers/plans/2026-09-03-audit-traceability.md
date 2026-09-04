# 审计日志与权限变更追踪实施计划

> 实施状态（2026-09-03）：Task 1–6、Task 7 的安全回归/API 验收、文档和完整门禁已完成；浏览器点击验收因本机 Sky Computer Use 服务启动失败暂未完成，详见 [Review](../reviews/2026-09-03-audit-traceability.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有操作日志、登录日志和请求追踪的基础上，建立可检索、可关联、可脱敏的项目及权限变更审计链路，让每次高风险变更都能回答“谁在什么时间、通过哪个请求、对哪个项目/资源做了什么改变”。

**Architecture:** 保留现有 `sys_operation_log` 作为跨模块操作审计主表，增加项目上下文、结果、原因和请求来源字段；用结构化 `AuditEvent` 统一写入，业务状态变更与成功审计在同一事务内提交。项目生命周期日志继续保留为领域历史，操作审计负责跨模块检索和权限变更追踪，二者不互相替代。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、OceanBase MySQL 兼容模式、Flyway 迁移、Vue 3、TypeScript、Node test、Playwright。

**Spec:** `docs/business-specification.md` §6、§11；`docs/product-specs/pms-project-collaboration-permission-lifecycle.md` §10；`docs/operations/release-checklist.md` 审计与可观测性条目。

## Global Constraints

- 审计写入只覆盖状态改变、权限改变和高风险管理操作；普通 GET 列表、详情、搜索和统计不写审计，避免审计表被读取流量淹没。
- 成功业务变更与成功审计必须在同一事务内提交；审计插入失败时回滚该次业务变更，不允许出现“操作成功但没有审计记录”。
- 审计摘要只保存白名单字段；禁止保存密码、JWT、刷新令牌、重置令牌、令牌哈希、数据库密码、评论正文、反馈正文、来源 URL、幂等键和二进制内容。
- 已有 `GET /admin/audit` 保持兼容，新增字段使用可选查询参数；审计接口继续只要求 `admin:audit:read`，不继承 `project:read`。
- 系统管理员保留全局绕过，但其成功变更仍必须记录操作人、请求 ID和审计事件。
- 所有数据库时间按 UTC 保存，前端按当前语言和时区展示；默认审计保留期继续使用现有 `PMS_AUDIT_RETENTION_DAYS=180`，本计划不擅自改变合规保留年限。
- 保留用户当前未提交改动；实现前先检查目标文件现有 diff，不使用 `reset`、`checkout` 或整文件覆盖。

---

## 设计基线

### 事件统一接口

新增 `AuditEvent`，服务层只提交业务摘要，操作人和请求环境由审计服务补齐：

```java
public record AuditEvent(
        String action,
        String resourceType,
        Long resourceId,
        Long projectId,
        String reason,
        Object before,
        Object after,
        String result
) {
    public static AuditEvent success(String action, String resourceType, Long resourceId,
                                     Long projectId, String reason, Object before, Object after) {
        return new AuditEvent(action, resourceType, resourceId, projectId, reason, before, after, "SUCCESS");
    }
}
```

`OperationLogService.record(AuditEvent event)` 是唯一新增写入口；现有五参数 `record(...)` 保留为兼容重载，并委托到 `AuditEvent.success(...)`。`result` 第一阶段固定支持 `SUCCESS` 和 `FAILURE`，拒绝写入另走安全事件路径，不把异常堆栈或请求正文写入审计。

### 事件目录

第一阶段固定以下动作编码，编码写入数据库，中文名称只在前端 locale 映射：

| 领域 | 动作 |
| --- | --- |
| 项目 | `PROJECT_CREATED`、`PROJECT_UPDATED`、`PROJECT_MANAGER_CHANGED`、`PROJECT_TERMINATED`、`PROJECT_RESTORED`、`PROJECT_DELETED` |
| 节点 | `NODE_OWNER_CHANGED`、`NODE_SCHEDULE_CHANGED`、`NODE_COMPLETED`、`NODE_ROLLED_BACK` |
| 任务 | `TASK_CREATED`、`TASK_UPDATED`、`TASK_MOVED`、`TASK_DELETED`、`TASK_ASSIGNEE_CHANGED`、`TASK_PRIORITY_CHANGED`、`TASK_STATUS_CHANGED`、`TASK_NODE_CHANGED`、`TASK_MILESTONE_CHANGED` |
| 协作资源 | `PROJECT_MEMBER_ADDED`、`PROJECT_MEMBER_REMOVED`、`PROJECT_MEMBER_ROLE_CHANGED`、`PROJECT_FOLLOWER_CHANGED`、`COMMENT_CREATED`、`COMMENT_DELETED`、`TASK_ATTACHMENT_UPLOADED`、`TASK_ATTACHMENT_DELETED`、`PROJECT_IMAGE_UPLOADED`、`PROJECT_IMAGE_DELETED` |
| 权限与组织 | `ROLE_CREATED`、`ROLE_UPDATED`、`ROLE_DELETED`、`ROLE_PERMISSION_CHANGED`、`ROLE_SCOPE_CHANGED`、`USER_ROLE_ASSIGNED`、`USER_ROLE_UNASSIGNED`、`USER_PRIMARY_POSITION_CHANGED`、`USER_PART_TIME_POSITION_ADDED`、`USER_PART_TIME_POSITION_REMOVED`、`USER_DISABLED`、`ORG_CREATED`、`ORG_UPDATED`、`ORG_MOVED`、`ORG_DEACTIVATED` |
| 反馈、认证与导入 | 沿用现有 `FEEDBACK_CREATED`、`FEEDBACK_UPDATED`、`FEEDBACK_REOPENED`、`PASSWORD_RESET`、`USER_INVITED`、`USER_ACTIVATED`、`IMPORT_COMMITTED`，统一补齐上下文和白名单摘要 |
| 系统运维 | `AUDIT_RETENTION_RUN`；只记录保留天数、dry-run、候选数和删除数，不记录业务正文 |

项目、节点、任务和附件事件必须携带 `projectId`；角色、人员、组织和认证事件 `projectId` 为空。评论只记录评论 ID、项目 ID、任务 ID、作者 ID和结果，不记录正文。

### 审计摘要规则

- `PROJECT_UPDATED` 只记录实际发生变化的字段：`name`、`priority`、`orgUnitId`、`startDate`、`endDate`；成员、关注人和项目经理变化使用独立动作。
- `TASK_UPDATED` 只记录 `title`、`descriptionChanged`、`deliverableChanged`、`status`、`dueDate`；负责人、优先级、节点和里程碑使用独立动作。正文只记录“是否变化”，不记录原文。
- 角色变化记录权限编码集合和数据范围编码，不记录表单原始 JSON；`ROLE_UPDATED` 的 before/after 必须能看出权限集合或范围是否变化。
- 附件只记录原始文件名、媒体类型、大小和附件 ID，不记录文件内容；项目图片只记录文件名和项目 ID。
- `reason` 只来自终止、恢复、回滚、停用等需要原因的业务字段，长度限制 500 字符并做控制字符清理。

## Task 1: 审计事件契约与 V14 数据迁移

**Files:**

- Create: `src/main/java/com/brad/pms/audit/AuditEvent.java`
- Create: `src/main/java/com/brad/pms/audit/AuditAction.java`
- Create: `src/main/java/com/brad/pms/audit/AuditResourceType.java`
- Modify: `src/main/java/com/brad/pms/entity/OperationLogDO.java`
- Modify: `src/main/java/com/brad/pms/mapper/OperationLogMapper.java`
- Create: `src/main/resources/db/migration/V14__audit_context_and_result.sql`
- Modify: `src/main/resources/schema.sql`
- Test: `src/test/java/com/brad/pms/config/EnterpriseSchemaMigrationTest.java`
- Test: `src/test/java/com/brad/pms/service/OperationLogServiceTest.java`

**Interfaces:**

- Consumes: existing `sys_operation_log` columns and `OperationLogService.record(...)` call sites.
- Produces: `AuditEvent`, stable action/resource codes, `OperationLogDO.projectId/reason/result/ip/userAgent` and V14 migration.

- [ ] **Step 1: 写迁移回归测试。** 断言 V14 后 `sys_operation_log` 包含 `project_id`、`reason`、`result`、`ip`、`user_agent`，且存在项目、操作人、动作和结果查询索引；断言旧日志读取仍然可用，新增列允许历史数据为空。
- [ ] **Step 2: 运行迁移测试确认失败。**

Run: `mvn -q -Dtest=EnterpriseSchemaMigrationTest test`

Expected: FAIL，缺少新增列或迁移版本。

- [ ] **Step 3: 实现 `AuditEvent`、动作/资源目录和 V14 迁移。** V14 使用可重复执行的项目迁移风格，新增 `result VARCHAR(16) NOT NULL DEFAULT 'SUCCESS'`，并为 `project_id, created_at`、`operator_id, created_at`、`action, created_at`、`result, created_at` 建索引；同步更新 `schema.sql`。
- [ ] **Step 4: 扩展实体和 Mapper。** 给 `OperationLogDO` 增加字段，保留现有 retention 查询；不新增审计更新、删除 API。
- [ ] **Step 5: 运行测试确认通过。**

Run: `mvn -q -Dtest=EnterpriseSchemaMigrationTest,OperationLogServiceTest test`

Expected: PASS。

## Task 2: 统一脱敏、请求上下文与审计写入

**Files:**

- Create: `src/main/java/com/brad/pms/audit/AuditSanitizer.java`
- Modify: `src/main/java/com/brad/pms/service/OperationLogService.java`
- Modify: `src/main/java/com/brad/pms/config/RequestTraceFilter.java`
- Modify: `src/main/java/com/brad/pms/job/OperationLogRetentionJob.java`
- Test: `src/test/java/com/brad/pms/service/OperationLogServiceTest.java`
- Test: `src/test/java/com/brad/pms/job/OperationLogRetentionJobTest.java`

**Interfaces:**

- Consumes: `AuditEvent`、`MDC.requestId`、当前 Servlet 请求和既有脱敏逻辑。
- Produces: `OperationLogService.record(AuditEvent)`；所有新旧调用都写入统一字段。

- [ ] **Step 1: 写脱敏和上下文测试。** 覆盖嵌套对象、数组、snake_case/camelCase 敏感字段、空值、不可序列化对象、过长摘要、无 HTTP 请求的定时任务；断言密码、token、secret、authorization、JWT 及其变体只出现 `[REDACTED]`。
- [ ] **Step 2: 运行测试确认失败。**

Run: `mvn -q -Dtest=OperationLogServiceTest test`

Expected: FAIL，新增事件字段尚未写入。

- [ ] **Step 3: 实现统一写入。** `OperationLogService.record(AuditEvent)` 补齐当前用户、请求 ID、远端 IP、User-Agent、项目 ID、原因和结果；无请求上下文时允许环境字段为空。脱敏器只接受白名单摘要并将最终 JSON 控制在现有列长度内，不能通过截断把敏感值重新暴露。
- [ ] **Step 4: 保持 retention 语义。** 保留现有 180 天配置和每批 500 条删除策略；审计清理完成后记录 `AUDIT_RETENTION_RUN`，并让 retention 测试确认 dry-run 不删除、正式运行按批次删除。
- [ ] **Step 5: 运行测试确认通过。**

Run: `mvn -q -Dtest=OperationLogServiceTest,OperationLogRetentionJobTest test`

Expected: PASS。

## Task 3: 接入项目、节点、任务和协作资源事件

**Files:**

- Modify: `src/main/java/com/brad/pms/service/ProjectService.java`
- Modify: `src/main/java/com/brad/pms/service/NodeService.java`
- Modify: `src/main/java/com/brad/pms/service/TaskService.java`
- Modify: `src/main/java/com/brad/pms/service/MemberService.java`
- Modify: `src/main/java/com/brad/pms/service/FollowerService.java`
- Modify: `src/main/java/com/brad/pms/service/CommentService.java`
- Modify: `src/main/java/com/brad/pms/service/TaskAttachmentService.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectImageService.java`
- Test: existing project/node/task/comment/image service tests plus new audit assertions in the same test classes

**Interfaces:**

- Consumes: Task 1 的动作目录和 Task 2 的 `OperationLogService.record(AuditEvent)`。
- Produces: 每个成功项目域变更都有准确的 `projectId/resourceId/action`，且与现有生命周期日志、通知和权限判断保持同一事务边界。

- [ ] **Step 1: 为项目变更补充失败测试。** 覆盖创建、项目资料更新、首节点确认项目经理、终止、恢复、逻辑删除；断言创建人/项目经理权限相同的场景只产生正确的成功事件，终止/恢复/回滚原因进入 `reason`。
- [ ] **Step 2: 为节点、任务和资源变更补充失败测试。** 覆盖节点负责人、排期、完成、回滚、任务创建/编辑/移动/删除、任务负责人变化、成员增删、关注人替换、评论增删、任务附件和项目图片上传/删除；断言失败的越权请求不产生成功审计。
- [ ] **Step 3: 实现项目服务事件。** 在业务状态更新完成、返回详情前写入结构化 before/after；项目经理发生变化时写 `PROJECT_MANAGER_CHANGED`，不要把成员/关注人/项目经理变化塞进 `PROJECT_UPDATED`。
- [ ] **Step 4: 实现节点、任务和协作资源事件。** 事件和业务写入使用现有 `@Transactional` 边界；摘要遵循设计基线，不记录评论或反馈正文，不记录附件二进制。
- [ ] **Step 5: 运行项目域测试。**

Run: `mvn -q -Dtest=ProjectServiceTest,NodeServiceTest,TaskServiceTest,CommentServiceTest,ProjectImageServiceTest test`

Expected: PASS，并能在测试 Mapper 中断言 `projectId`、动作和 before/after 摘要。

## Task 4: 接入角色、数据范围、人员、组织和导入变更

**Files:**

- Modify: `src/main/java/com/brad/pms/service/RoleService.java`
- Modify: `src/main/java/com/brad/pms/service/PersonnelService.java`
- Modify: `src/main/java/com/brad/pms/service/OrgUnitService.java`
- Modify: `src/main/java/com/brad/pms/service/EnterpriseImportService.java`
- Modify: `src/main/java/com/brad/pms/service/InvitationService.java`
- Modify: `src/main/java/com/brad/pms/service/PasswordResetService.java`
- Test: `src/test/java/com/brad/pms/service/RoleServiceTest.java`
- Test: existing personnel/org/import/auth service tests

**Interfaces:**

- Consumes: Task 2 的统一审计写入；现有角色绑定、组织历史和导入事务。
- Produces: 权限点、数据范围、角色分配、主归属和导入结果的 before/after 可追踪记录。

- [ ] **Step 1: 为角色权限变化写失败测试。** 更新自定义角色时，断言 `ROLE_UPDATED` 能区分权限集合变化、数据范围变化和名称变化；权限编码使用排序后的去重集合，不能记录密码或表单无关字段。
- [ ] **Step 2: 为人员、组织和认证变更写失败测试。** 断言角色分配/移除、主归属变更、停用、组织移动、导入提交和密码重置都有动作、操作者、请求 ID和白名单摘要；失败事务不产生成功事件。
- [ ] **Step 3: 实现服务接入。** 修正现有 `ROLE_UPDATED`、`USER_ROLE_ASSIGNED` 等调用，传入真实 before/after；组织领域历史继续记录完整结构，操作审计只记录组织 ID、编码、名称、父组织和状态等摘要。
- [ ] **Step 4: 运行权限与组织测试。**

Run: `mvn -q -Dtest=RoleServiceTest,PersonnelServiceTest,OrgUnitServiceTest,EnterpriseImportServiceTest test`

Expected: PASS。

## Task 5: 审计查询 API、详情 DTO 与 OpenAPI 合同

**Files:**

- Create: `src/main/java/com/brad/pms/dto/response/AuditLogDTO.java`
- Create: `src/main/java/com/brad/pms/service/AuditQueryService.java`
- Modify: `src/main/java/com/brad/pms/controller/AdminAuditController.java`
- Modify: `src/main/java/com/brad/pms/mapper/OperationLogMapper.java`
- Modify: `src/main/resources/openapi/pms-api.yaml`
- Test: `src/test/java/com/brad/pms/controller/AdminAuditControllerTest.java`
- Test: `src/test/java/com/brad/pms/service/AuditQueryServiceTest.java`

**Interfaces:**

- Consumes: `OperationLogDO`、用户/项目展示信息和 `admin:audit:read`。
- Produces: `GET /admin/audit` 的扩展查询参数，以及 `GET /admin/audit/{id}` 详情接口；响应不直接暴露数据库实体。

- [ ] **Step 1: 写控制器和查询服务失败测试。** 覆盖 `projectId`、`result`、`requestId`、action、resourceType、resourceId、operatorId、from/to、分页上限 100；无 `admin:audit:read` 返回 403，非法时间返回 422，跨项目查询不套用 `project:read`。
- [ ] **Step 2: 实现 `AuditLogDTO` 和查询服务。** DTO 包含 `operatorDisplayName`、`projectName`、`projectId`、动作/资源编码、结果、原因、before/after 摘要、requestId 和 UTC 创建时间；用户或项目已删除时保留 ID 并使用“已删除/未知”展示。
- [ ] **Step 3: 扩展 Mapper 查询。** 使用数据库分页和组合索引，不先加载全量日志 ID；`from/to` 为左闭右开或明确的 ISO-8601 约定，并在 OpenAPI 中固定。
- [ ] **Step 4: 实现详情接口并更新 OpenAPI。** 详情仍经过 `admin:audit:read`，只返回脱敏后的摘要；禁止通过接口修改或删除审计记录。
- [ ] **Step 5: 运行后端 API 合同测试。**

Run: `mvn -q -Dtest=AdminAuditControllerTest,AuditQueryServiceTest test && bash scripts/validate-openapi.sh`

Expected: PASS。

## Task 6: 前端审计工作台与权限变更检索

**Files:**

- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/admin-audit.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/audit/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/zh-CN.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/locales/en-US.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/styles/fs-insight.css`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/audit/audit.test.mjs`

**Interfaces:**

- Consumes: Task 5 的分页列表和详情接口、现有 `admin:audit:read` 路由守卫。
- Produces: 可按动作/资源/结果/项目/操作人/请求 ID/时间筛选的审计页，以及可读的 before/after 详情抽屉。

- [ ] **Step 1: 写前端失败测试。** 覆盖查询参数去空、分页重置、权限拒绝错误、空态、加载失败保留筛选条件、动作和资源英文编码显示为中英文 locale 标签；断言页面不展示原始评论/反馈正文。
- [ ] **Step 2: 扩展 API 类型。** `AuditLog` 增加 `projectId`、`reason`、`result`、展示名称和详情字段；新增 `getAudit(id)`，保留 `listAudit(params)` 的旧调用兼容。
- [ ] **Step 3: 重做审计表格。** 表格展示时间、结果、动作、资源、项目、操作人、request ID和原因；点击“查看详情”打开抽屉，使用字段级 before/after 对比，不直接渲染未处理的 HTML。
- [ ] **Step 4: 增加权限变更快捷筛选。** 以 `ROLE_`、`USER_ROLE_`、`USER_PRIMARY_POSITION_`、`ORG_` 动作作为前端筛选预设；不添加新的权限点，页面仍只由后端 `admin:audit:read` 决定是否可见。
- [ ] **Step 5: 完成响应式与可访问性。** 桌面端保持表格密度，窄屏将筛选项切成两列；为筛选组、详情抽屉、空态和错误重试提供稳定 aria 标签。
- [ ] **Step 6: 运行前端验证。**

Run: `npm test && npm run typecheck && npm run build`

Expected: PASS。

## Task 7: 端到端验收、运维文档与发布门禁

**Files:**

- Modify: `/Users/fs/Desktop/Project/pms-backend/docs/business-specification.md`
- Modify: `/Users/fs/Desktop/Project/pms-backend/docs/operations/release-checklist.md`
- Modify: `/Users/fs/Desktop/Project/pms-backend/docs/operations/infrastructure-status.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/docs/user-manual.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/docs/design-logic.md`
- Create: `/Users/fs/Desktop/Project/pms-backend/scripts/audit-acceptance.sh`
- Test: `/Users/fs/Desktop/Project/pms-backend/src/test/java/com/brad/pms/controller/AdminAuditPermissionAnnotationTest.java`

**Interfaces:**

- Consumes: Tasks 1–6 的数据库、API、页面和事件目录。
- Produces: 可重复执行的本地/测试环境验收脚本、权限与审计业务规则、发布清单证据。

- [ ] **Step 1: 写安全回归测试。** 覆盖无 `admin:audit:read` 的普通员工、只有 `project:read` 的员工、项目管理员和系统管理员；断言只有审计权限能查询，项目读取权限不自动继承审计读取。
- [x] **Step 2: 编写 API 验收脚本。** 使用环境变量注入的 QA 账号创建临时项目并执行匿名 401、普通成员 403、项目 ID/result/request ID 查询、详情查询和删除清理；脚本结束验证验收项目均为 `deleted=1`，不写入仓库凭据。
- [ ] **Step 3: 增加浏览器验收。** 用系统管理员验证审计菜单、筛选、分页、详情抽屉和权限变更快捷筛选；用普通成员验证菜单不可见且直接访问返回 403；用 390×844 验证筛选和详情不横向溢出。
- [x] **Step 4: 更新业务手册。** 明确哪些操作必留审计、哪些字段不留、谁能看审计、审计与项目读取/反馈权限的关系、保留期配置和 request ID 排查方法。
- [x] **Step 5: 执行完整门禁。** 后端 225 tests（0 失败、0 错误、1 跳过）、前端 139 tests、类型检查、构建、OpenAPI、V14 OceanBase 和真实 API 验收均通过。

Run:

```bash
mvn -q test
mvn -q -DskipTests package
bash scripts/validate-openapi.sh
bash -n scripts/*.sh
git diff --check
```

Run in frontend:

```bash
npm test
npm run typecheck
npm run build
git diff --check
```

Expected: 全部命令退出码为 0；OceanBase 隔离实例完成 V14 迁移并通过健康检查；API 与浏览器验收无失败；审计摘要不含敏感值，测试数据清理为 0。

## 分期边界

第一阶段只做“可追踪和可检索”的企业内审计，不引入以下范围：

- 不做完整事件溯源，不用审计表替代业务表或项目生命周期表。
- 不做外部 SIEM/WORM 存储、数字签名和跨系统消息投递；先保证本地数据库内的事务一致性和脱敏。
- 不做 CSV 导出、实时 WebSocket 推送和全量 GET 访问审计；这些需求需要独立容量与合规评审。
- 不改变人员、组织、审计接口的现有数据范围语义；本期只增强审计内容和查询字段。

## 完成定义

- 所有第一阶段动作目录均有成功变更审计，项目域事件带 `projectId`，权限/组织事件能看到 before/after 摘要。
- 角色权限、数据范围和用户角色变更可以按操作人、动作、时间和 request ID检索。
- 普通成员不能调用审计 API；`project:read` 不会隐式获得 `admin:audit:read`。
- 终止、恢复、回滚、停用等原因可追踪；已删除项目的审计仍可按项目 ID查看。
- 审计写入失败会阻止成功业务提交；敏感字段、正文和二进制不会进入审计记录。
- 后端、前端、OpenAPI、OceanBase V14 迁移、脚本和 API 验收全部通过，文档和权限矩阵同步更新；浏览器验收保留为环境恢复后的补验项。
