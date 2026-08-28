# 企业级能力收口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有单企业 PMS 基础上补齐可重复部署、安全运营、组织权限、批量导入、项目一致性、可观测性与发布验收，使项目具备企业内网长期运行和开源交付能力。

**Architecture:** 保持单企业私有化部署和 OceanBase MySQL 兼容模式，后端继续以认证、权限点、组织数据范围和业务规则分层裁决访问；前端沿用现有 PMS 设计系统，仅补齐状态、交互和错误反馈。所有跨模块写操作通过事务服务完成，并记录可检索的操作审计。

**Tech Stack:** Java 17、Spring Boot 2.7、MyBatis-Plus、OceanBase、JJWT、BCrypt、Vue 3、TypeScript、Ant Design Vue、pnpm、JUnit 5、Node test runner。

**Spec:** `docs/superpowers/specs/2026-08-27-enterprise-organization-access-design.md`、`docs/superpowers/specs/2026-08-27-enterprise-foundation-security-design.md`

## Global Constraints

- 单企业私有化部署；运行时数据库只允许 OceanBase，H2 仅用于测试。
- 英文登录名使用 `Locale.ROOT` 小写归一化；用户展示固定为 `中文名（English.Name）`。
- 每名在职员工只能有一条主归属；组织负责人和员工归属是两套独立关系。
- 组织和人员只做停用/离职，不做物理删除；项目与审计历史必须可追溯。
- 后端授权是唯一安全边界；前端隐藏菜单和按钮不能替代后端权限校验。
- 数据迁移禁止 `DROP`、`TRUNCATE` 和覆盖非空业务数据。
- 管理页面遵循现有 PMS 规范：中文主文案、英文代码用小字辅助展示、36px 控件、8px 圆角、蓝色主操作色。
- 每一期采用测试先行、完成后运行自我 review，并形成独立提交。

---

### Task 1: 可重复的 OceanBase 启动与生产配置

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-oceanbase.yml`
- Create: `scripts/start-local-oceanbase.sh`
- Create: `scripts/stop-local-oceanbase.sh`
- Create: `.env.oceanbase.example`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Test: `src/test/java/com/brad/pms/config/OceanbaseConfigurationTest.java`

**Deliverable:** 启动脚本从环境变量读取 OceanBase 连接和固定 `PMS_JWT_SECRET`，不再每次重启随机生成密钥；脚本能检查 2881、数据库连接、JWT 长度和 8080 占用，并输出可操作的失败原因。停止脚本只终止由脚本记录的后端 PID。

- [ ] **Step 1:** 为缺少 JWT、JWT 少于 32 字节、缺少 OceanBase 账号和端口不可达分别补充配置测试。
- [ ] **Step 2:** 实现 `start-local-oceanbase.sh`、`stop-local-oceanbase.sh`，密码和 JWT 只通过进程环境传递，不写日志。
- [ ] **Step 3:** 运行 `mvn -q -Dtest=OceanbaseConfigurationTest test` 和两个脚本的 shellcheck/冒烟检查。
- [ ] **Step 4:** review 启动失败、重复启动、旧 PID 不存在和 8080 被占用四种路径后提交 `chore: make OceanBase startup repeatable`。

**验收：** 重启后旧 JWT 仍可验证；后端日志明确显示 `oceanbase` profile；H2 不会被加载。

### Task 2: 完整的账号、会话和安全运营闭环

**Files:**
- Modify: `src/main/java/com/brad/pms/service/AuthService.java`
- Modify: `src/main/java/com/brad/pms/controller/AuthController.java`
- Modify: `src/main/java/com/brad/pms/security/AuthInterceptor.java`
- Modify: `src/main/java/com/brad/pms/service/PasswordResetService.java`
- Modify: `src/main/java/com/brad/pms/service/InvitationService.java`
- Modify: `src/main/java/com/brad/pms/controller/AdminUserController.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/store/user.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/login/index.vue`
- Test: `src/test/java/com/brad/pms/service/AuthServiceTest.java`
- Test: `src/test/java/com/brad/pms/security/AuthInterceptorTest.java`

**Deliverable:** 登录、刷新、退出、改密、邀请激活、重置密码、锁定、禁用和撤销会话形成闭环；刷新令牌轮换且只保存哈希，失败登录和管理员操作可追溯。

- [ ] **Step 1:** 先补充大小写不敏感登录、刷新令牌轮换、禁用即时失效、连续失败锁定和改密撤销旧会话的失败测试。
- [ ] **Step 2:** 实现 refresh token rotation、当前会话注销、管理员撤销用户全部会话和密码策略校验。
- [ ] **Step 3:** 登录页处理 401/锁定/待激活/禁用提示；请求拦截器只在刷新失败后跳转登录页并保留原 redirect。
- [ ] **Step 4:** 运行后端认证测试、前端 store 测试和 `pnpm typecheck`，review 不得把密码、令牌或哈希写入日志。
- [ ] **Step 5:** 提交 `feat: complete enterprise account lifecycle`。

**验收：** 输入 `brad.xie`、`Brad.Xie`、`BRAD.XIE` 均能登录；禁用或改密后旧会话立即失效；所有展示为 `中文名（English.Name）`。

### Task 3: RBAC、数据范围与管理页面权限闭环

**Files:**
- Modify: `src/main/java/com/brad/pms/security/AuthorizationService.java`
- Modify: `src/main/java/com/brad/pms/security/DataScopeResolver.java`
- Modify: `src/main/java/com/brad/pms/service/RoleService.java`
- Modify: `src/main/java/com/brad/pms/controller/AdminRoleController.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/router/admin-guard.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/roles/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/users/index.vue`
- Test: `src/test/java/com/brad/pms/security/DataScopeResolverTest.java`
- Test: `src/test/java/com/brad/pms/service/RoleServiceTest.java`
- Test: `/Users/fs/Desktop/Project/pms-front/src/router/admin-guard.test.mjs`

**Deliverable:** 权限点和六类数据范围使用中文主文案、英文小字说明；内置角色受保护，自定义角色必须绑定有效权限和组织；列表、详情、接口三层结果一致。

- [ ] **Step 1:** 为每种数据范围编写允许/拒绝矩阵测试，覆盖主归属、兼职归属、组织负责人和项目权限的独立关系。
- [ ] **Step 2:** 实现后端统一授权入口和组织范围 SQL 条件，禁止在查询中拼接用户输入。
- [ ] **Step 3:** 完善角色编辑弹窗的中文标签、英文小字、保存校验和权限不足提示。
- [ ] **Step 4:** 运行后端授权测试、前端路由测试，review 所有管理接口是否缺少 `@RequirePermission`。
- [ ] **Step 5:** 提交 `feat: close RBAC and data-scope enforcement`。

**验收：** 无权限用户直接调用接口返回 403；页面小屏时菜单可折叠但权限不丢失；英文代码统一小写展示。

### Task 4: 组织架构画布与人员归属一致性

**Files:**
- Modify: `src/main/java/com/brad/pms/service/OrgUnitService.java`
- Modify: `src/main/java/com/brad/pms/service/PersonnelService.java`
- Modify: `src/main/java/com/brad/pms/controller/AdminOrgUnitController.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/org/OrgCanvas.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/org/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/users/index.vue`
- Test: `src/test/java/com/brad/pms/service/OrgUnitServiceTest.java`
- Test: `src/test/java/com/brad/pms/service/PersonnelServiceTest.java`
- Test: `/Users/fs/Desktop/Project/pms-front/src/views/admin/org/org-canvas.test.mjs`

**Deliverable:** 组织画布全屏可拖拽、缩放和居中，所有父子节点连线正确；节点展示组织类型和负责人；新增、移动、停用组织和调整员工主归属后，人员页即时显示最新主归属。

- [ ] **Step 1:** 为树结构连线、孤儿节点、循环父级、负责人独立于员工归属和停用组织迁移补充测试。
- [ ] **Step 2:** 后端增加树校验、批量移动和停用前置校验，保留负责人历史和员工任职历史。
- [ ] **Step 3:** 前端画布改为固定可视区域 + 无限舞台，使用 pointer 拖拽和缩放，不因窗口变小挤压节点；连线按节点实际坐标绘制。
- [ ] **Step 4:** 人员列表按最新有效主归属查询，显示兼职/项目归属，不把组织负责人关系混入主归属字段。
- [ ] **Step 5:** 运行组织和人员测试并进行浏览器视觉 review，提交 `feat: stabilize organization canvas and affiliations`。

**验收：** 画布充满内容区且可横纵拖拽；每个 BG、部门、团队均可看到负责人；组织页与人员页主归属一致。

### Task 5: Excel/CSV 批量导入的可审计事务流程

**Files:**
- Modify: `src/main/java/com/brad/pms/service/EnterpriseImportService.java`
- Modify: `src/main/java/com/brad/pms/service/ImportTemplateService.java`
- Modify: `src/main/java/com/brad/pms/controller/AdminImportController.java`
- Modify: `src/main/java/com/brad/pms/entity/ImportBatchDO.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/import/index.vue`
- Test: `src/test/java/com/brad/pms/service/EnterpriseImportServiceTest.java`
- Test: `/Users/fs/Desktop/Project/pms-front/src/views/admin/import/import.test.mjs`

**Deliverable:** 提供组织模板和员工模板，支持 CSV/XLSX 上传、预览、错误行下载、英文名解析、负责人解析、主归属/兼职归属校验和全有全无提交。

- [ ] **Step 1:** 为重复英文名、缺失父组织、循环组织、负责人不存在、主归属为空和重复导入编写失败测试。
- [ ] **Step 2:** 实现模板下载、大小/行数限制、编码识别、字段级错误和预览摘要；预览不写业务表。
- [ ] **Step 3:** 提交时使用单事务和幂等批次号，失败自动回滚，保留导入批次及错误审计。
- [ ] **Step 4:** 前端增加“上传 → 预览 → 确认 → 结果”四步状态，错误行支持下载，成功后刷新组织与人员列表。
- [ ] **Step 5:** 运行导入测试和 `pnpm typecheck`，review 大文件、重复提交和部分失败路径，提交 `feat: add auditable organization import`。

**验收：** 20 人样例可一次导入；任何一行失败都不会写入半批数据；再次提交同一批次不会产生重复人员或组织。

### Task 6: 项目列表、详情与节点协作一致性

**Files:**
- Modify: `src/main/java/com/brad/pms/service/ProjectService.java`
- Modify: `src/main/java/com/brad/pms/service/NodeService.java`
- Modify: `src/main/java/com/brad/pms/dto/response/ProjectDTO.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/list/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/components/TaskKanban.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.ts`
- Test: `src/test/java/com/brad/pms/service/ProjectServiceScopeTest.java`
- Test: `src/test/java/com/brad/pms/service/NodeServiceScheduleTest.java`
- Test: `/Users/fs/Desktop/Project/pms-front/src/views/project/detail/workflow.test.mjs`

**Deliverable:** 项目列表和详情共用同一字段来源；项目经理为空显示“待分配”；业务线支持选择到最低层级并逐级展示；选择业务线后节点负责人自动带出负责人；节点排期中文化并与负责人同排；任务卡片支持 hover 删除、确认和阴影视觉反馈。

- [ ] **Step 1:** 为列表/详情字段映射、项目经理回退、业务线层级、节点负责人自动带出和终止项目只读写测试。
- [ ] **Step 2:** 后端统一项目摘要 DTO，业务线和负责人返回完整组织路径及负责人展示名。
- [ ] **Step 3:** 前端删除重复本地映射，复用同一 `displayName`、状态、业务线路径和成员统计字段。
- [ ] **Step 4:** 增加任务卡片删除确认、节点排期中文日期选择器和只读项目保护。
- [ ] **Step 5:** 运行后端测试、前端单测、`pnpm typecheck`、`pnpm build`，浏览器逐项 review 后提交 `fix: align project collaboration data`。

**验收：** 项目列表点击进入详情后名称、创建人、经理、成员、周期、状态和进度完全一致；项目终止后所有写操作被拒绝。

### Task 7: 审计、健康检查和可观测性

**Files:**
- Modify: `src/main/java/com/brad/pms/service/OperationLogService.java`
- Modify: `src/main/java/com/brad/pms/controller/AdminAuditController.java`
- Create: `src/main/java/com/brad/pms/config/RequestTraceFilter.java`
- Create: `src/main/java/com/brad/pms/controller/HealthController.java`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/admin/audit/index.vue`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Test: `src/test/java/com/brad/pms/controller/AdminAuditControllerTest.java`
- Test: `src/test/java/com/brad/pms/security/EnterpriseRegressionTest.java`

**Deliverable:** 登录审计和操作审计可分页、按人员/资源/结果/时间检索；每个请求带 trace id；健康检查区分应用、数据库和迁移状态；错误响应统一且不泄露 SQL、密码或令牌。

- [ ] **Step 1:** 为未登录失败、禁用账号、权限拒绝、导入回滚和组织变更补充审计断言。
- [ ] **Step 2:** 实现 trace id 过滤器、只读健康接口和审计筛选分页，敏感字段统一脱敏。
- [ ] **Step 3:** 前端审计页增加筛选、详情抽屉和空状态，沿用现有中文/英文小字规范。
- [ ] **Step 4:** 运行回归测试并 review 生产日志样例，提交 `feat: add enterprise audit observability`。

**验收：** 用 trace id 能串起一次请求；数据库不可用时健康检查明确失败；审计记录不包含密码、令牌及其哈希。

### Task 8: 开源交付、CI 和最终验收

**Files:**
- Create: `Dockerfile`
- Create: `docker-compose.example.yml`
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Create: `docs/operations/release-checklist.md`
- Test: `src/test/java/com/brad/pms/config/EnterpriseSchemaMigrationTest.java`
- Test: `/Users/fs/Desktop/Project/pms-front/src/router/admin-guard.test.mjs`

**Deliverable:** 新用户可以按 README 在本地启动 OceanBase、后端和前端；CI 自动运行后端测试、前端类型检查和构建；发布清单覆盖迁移、回滚、备份、默认账号、JWT、CORS 和浏览器冒烟。

- [ ] **Step 1:** 编写不含真实密码的 Compose 示例和环境变量说明，默认不自动导入演示数据。
- [ ] **Step 2:** 添加 CI：`mvn -q test`、`pnpm typecheck`、`pnpm build`，并检查仓库不存在密钥、H2 运行配置和大文件。
- [ ] **Step 3:** 执行 `scripts/enterprise-preflight.sh`、`scripts/verify-enterprise-migration.sh` 和 OceanBase 冒烟登录。
- [ ] **Step 4:** 使用浏览器验证登录、人员、组织、角色、导入、项目列表、项目详情和注销流程；记录每个失败项并修复后重跑。
- [ ] **Step 5:** 最终 review 数据库、权限、日志、文案和响应式布局，提交 `chore: prepare enterprise open-source release`。

**验收：** 全部测试通过，部署文档可从零执行，且没有未说明的默认密码、随机生产密钥或 H2 运行入口。

## 执行顺序

按 Task 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 顺序执行。每个 Task 完成后先运行其验收命令，再做一次代码和浏览器 review，确认通过后才进入下一期；任何一期失败都停在当前期修复，不跨期堆积问题。

## 阶段完成与 Review 记录（2026-08-28）

本轮按“测试先行 → 实现 → 回归 → 文档沉淀”的顺序收口。历史提交已经完成 Task 1–8 的业务实现，本轮补齐此前缺失的数据库完整性契约，并对各阶段做了复核：

| 阶段 | 状态 | Review 证据 |
| --- | --- | --- |
| 第 0 期：当前页面修复 | 已完成 | 前端搜索框、状态下拉框、查询按钮统一 36px/6px；桌面与 390px 窄屏无页面横向溢出；Node 单测 77/77、`pnpm typecheck`、`pnpm build` 通过。 |
| 第 1 期：企业级基础加固 | 已完成 | 认证、RBAC、审计、健康检查和 OceanBase 配置由既有提交覆盖；新增 V5 迁移、MyBatis-Plus 逻辑删除/乐观锁、唯一索引、外键及 SQL 过滤；后端全量 71 项测试通过。 |
| 第 2 期：组织与导入可靠性 | 已完成 | 主归属与组织负责人保持独立；导入预览、校验、幂等、事务回滚、错误审计和组织变更历史由既有服务测试覆盖；组织/人员相关测试通过。 |
| 第 3 期：研发管理扩展 | 已完成 | 项目、节点排期、任务、里程碑、业务线和工作台页面已实现；列表/详情字段一致性与节点服务测试通过。 |
| 第 4 期：开源交付 | 已完成 | README、Compose、初始化/升级脚本、CI、健康检查和发布清单已提交；迁移脚本在 H2 测试数据库完整执行，OceanBase 实例上线前仍需按本手册执行一次真实环境预检。 |

### 本轮完整性变更

- `V5__integrity_soft_delete_optimistic_lock.sql` 为可升级数据库增加 `deleted`、`version`、关键唯一索引和跨表外键。
- 可变实体统一使用 MyBatis-Plus `@TableLogic` 与 `@Version`；全局拦截器启用乐观锁。
- 原生 Mapper 查询补充逻辑删除过滤，避免绕过实体级软删除规则。
- 新增 `EnterpriseIntegrityMigrationTest`，验证列、索引、外键和实体注解；先确认 RED，再以全量测试 GREEN。

### 发布前仍需执行

自动化测试使用 H2 仅验证迁移语法和业务回归，生产运行时仍只允许 OceanBase。发布前必须在目标 OceanBase 租户执行 `enterprise-preflight.sh`、V1–V6 迁移、`verify-enterprise-migration.sh` 和登录/健康检查冒烟，并记录结果到发布清单。

### 基础设施后续计划（2026-08-28）

上表的“阶段完成”表示此前业务能力和基础开源骨架已完成，不表示生产基础设施已经全部收口。以下事项转入独立计划继续执行：

- `pms_app` / `pms_migrator` 数据库账号隔离和应用配置切换；
- OceanBase 备份、恢复、迁移锁、版本校验和可重复升级；
- 统一 HTTP 错误状态、限流、通知服务和上传持久化；
- 容器加固、结构化日志、指标、审计保留、OpenAPI、集成 CI、E2E、镜像扫描和 SBOM。

状态和验收证据统一记录在 [`docs/operations/infrastructure-status.md`](../operations/infrastructure-status.md)，执行计划见 [`2026-08-28-enterprise-infrastructure-hardening.md`](2026-08-28-enterprise-infrastructure-hardening.md)。
