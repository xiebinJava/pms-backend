# PMS 业务规范与后端设计逻辑

## 1. 文档定位

本文档是 PMS 后端的业务规则和数据边界说明，面向产品、前端、后端、测试以及企业部署维护人员。代码、数据库迁移脚本和本文档发生不一致时，应先修复实现或补充迁移，再更新本文档；不要只通过修改文档掩盖行为差异。

当前产品形态为**单企业、本地部署**：每个企业独立部署一套前后端和一个 `brad_pms` 数据库，不在业务表中引入 `tenant_id`。未来如果演进为云端多租户，需要另行设计租户隔离、计费和跨租户运维，不能直接把本方案当作多租户实现。

相关资料：

- 数据库迁移：[OceanBase 迁移设计](superpowers/specs/2026-08-26-oceanbase-migration-design.md)
- 企业安全：[企业基础安全设计](superpowers/specs/2026-08-27-enterprise-foundation-security-design.md)
- 组织与权限：[企业组织与访问设计](superpowers/specs/2026-08-27-enterprise-organization-access-design.md)
- 上线执行：[企业升级运行手册](operations/enterprise-upgrade-runbook.md)

## 2. 领域术语

| 术语 | 约定 |
| --- | --- |
| 企业 | 当前部署实例对应的一家公司，根组织为公司总部。 |
| 组织单元 | 组织树中的公司、BG、中心、部门、团队等节点，类型由 `sys_org_unit_type` 管理。 |
| 业务线 | 可承接项目的组织单元，通常是 BG 或其下级组织；选择时使用完整组织路径。 |
| 主归属 | 人员当前唯一的主要组织关系，来自 `sys_user_position.is_primary = true` 的有效记录。 |
| 兼职/项目归属 | 人员除主归属外的一个或多个有效组织关系。 |
| 组织负责人 | `sys_org_unit.leader_user_id` 指向的负责人，与员工主归属是两套独立关系。 |
| 项目经理 | 项目级负责人，保存在 `project.project_manager_id`；未设置时前端显示“待分配”。 |
| 项目成员 | 项目协作范围内的人员，角色包括负责人、管理员、成员。 |
| 数据范围 | 角色在组织和项目数据上的可见范围，不等同于功能权限。 |

用户展示遵循“中文名（英文名）”优先、邮箱兜底的规则，例如“张伟（Alex.Zhang）”。邮箱是新账号的唯一核心身份，登录时对邮箱执行 trim + 小写规范化并按 `email_normalized` 查询；中文名和英文名均可选。历史 `username` 字段保留为兼容字段，旧英文名登录在兼容期内仍可用，并继续以 `username_normalized` 保证唯一。

## 3. 核心业务不变量

以下规则必须由后端保证，前端只负责提供良好的编辑和错误提示体验：

1. 一个用户最多只能有一条**有效主归属**；设置新主归属时，旧主归属必须先失效或降为非主归属。
2. 用户可以拥有多条有效兼职/项目归属；归属关系有起止日期和状态，历史记录不可被前端静默覆盖。
3. 组织负责人独立于员工主归属。调整员工主归属不得自动改变组织负责人；调整组织负责人也不得改写员工主归属。
4. 组织树只能形成一棵无环父子树。停用组织前，必须处理有效子组织、成员和项目引用，不能产生孤儿数据。
5. 组织编码、岗位编码、角色编码、权限编码在各自命名空间内唯一；组织节点移动后必须同步维护 `path` 和下级节点可见范围。
6. `email_normalized` 使用 `Locale.ROOT` 小写规范化，并建立唯一索引；`Alex@Example.com`、`alex@example.com` 不能创建为两个账号。历史 `username_normalized` 同样保持唯一，避免旧客户端产生冲突。
7. 生产运行只使用 OceanBase；H2 仅用于自动化测试或一次性历史迁移快照，不能作为生产回退数据库。
8. 所有会改变人员、组织、角色、项目生命周期、导入结果或认证状态的写操作都要留下可追踪审计记录。
9. 已完成、已终止或已删除的项目/节点按只读处理；恢复项目必须通过明确的恢复操作和原因，不得靠普通编辑绕过生命周期。
10. 项目列表和项目详情必须读取同一套项目聚合字段：项目经理、业务线完整路径、负责人、状态、优先级、周期和进度不能出现两套解释。

## 4. 数据模型与关系

### 4.1 身份、组织和权限

```text
sys_user
  ├─< sys_user_position >─ sys_org_unit ─< sys_org_unit (parent_id/path)
  ├─< sys_user_role >─ sys_role ─< sys_role_permission >─ sys_permission
  │                         └─< sys_role_org_scope >─ sys_org_unit
  ├─< sys_auth_session
  ├─< sys_login_log
  └─< sys_operation_log (operator_id)
```

主要表职责：

| 表 | 职责 | 关键字段/约束 |
| --- | --- | --- |
| `sys_user` | 账号邮箱、可选中文名/英文名、认证状态 | `email_normalized` 唯一；`username_normalized` 为兼容字段且唯一；`ACTIVE/LOCKED/DISABLED/PENDING_ACTIVATION` |
| `sys_user_position` | 用户与组织的主归属、兼职归属、直属上级 | `user_id/org_unit_id/is_primary/assignment_type/status` |
| `sys_org_unit` | 可视化组织树和业务线 | `parent_id/path/type_id/leader_user_id/status` |
| `sys_position` | 岗位字典 | 编码唯一，可停用 |
| `sys_role` | 角色和数据范围类型 | 内置角色不可随意删除 |
| `sys_permission` | 细粒度功能权限点 | 以 `admin:*`、`project:*` 编码 |
| `sys_user_role` | 用户当前/历史角色授权 | 支持有效期和状态 |
| `sys_role_org_scope` | `CUSTOM_ORGS` 角色的组织范围 | 角色与组织多对多 |
| `sys_company_profile` | 企业名称、时区和安全策略 | 单实例一条企业配置 |

### 4.2 项目协作

```text
project
  ├─< project_node
  │     └─< project_task
  ├─< project_member >─ sys_user
  ├─< project_follower >─ sys_user
  ├─< project_milestone
  ├─< project_comment >─ sys_user
  └─< project_lifecycle_log >─ sys_user (operator_id)
```

`project.org_unit_id` 是项目主业务线/主组织；`project_node.owner_id` 是节点负责人；`project_task.assignee_id` 是任务执行人。这三者不可混用。

## 5. 状态与生命周期

### 5.1 用户状态

| 状态 | 说明 | 允许的关键动作 |
| --- | --- | --- |
| `PENDING_ACTIVATION` | 已邀请、尚未设置密码 | 仅能通过激活流程完成首次登录 |
| `ACTIVE` | 可正常登录和访问授权数据 | 可改密码、刷新会话、参与项目 |
| `LOCKED` | 登录失败次数达到策略阈值 | 等待解锁或走管理员/重置密码流程 |
| `DISABLED` | 管理员停用 | 拒绝登录并撤销有效会话 |

### 5.2 项目、节点、任务和里程碑

| 对象 | 状态值 | 业务含义 |
| --- | --- | --- |
| 项目 | `ACTIVE` 进行中、`COMPLETED` 已完成、`TERMINATED` 已终止、`DELETED` 已删除 | 终止和恢复必须写入生命周期日志 |
| 节点 | `NOT_STARTED` 未开始、`IN_PROGRESS` 进行中、`COMPLETED` 已完成、`TERMINATED` 已终止 | 完成/回退/排期/负责人均走节点接口 |
| 任务 | `TODO` 待办、`DOING` 进行中、`DONE` 已完成 | 拖拽看板等同于状态变更，必须做项目权限校验 |
| 里程碑 | `PENDING` 未开始、`ACTIVE` 进行中、`COMPLETED` 已完成 | 里程碑与任务通过 `milestone_id` 关联 |

前端显示使用中文标签；接口和数据库使用稳定的英文枚举/整数编码。未知编码不得被静默映射成“已完成”，应保留原值并返回可诊断错误。

## 6. 权限与数据范围

### 6.1 功能权限

控制器通过 `@RequirePermission` 声明接口级权限：

| 权限编码 | 用途 |
| --- | --- |
| `project:read` / `project:write` | 项目及项目协作数据读写 |
| `admin:user:read` / `admin:user:write` | 人员、归属、角色分配和停用 |
| `admin:org:read` / `admin:org:write` | 组织树查看和编排 |
| `admin:role:read` / `admin:role:write` | 角色、权限点和数据范围 |
| `admin:import:write` | 组织与员工导入 |
| `admin:audit:read` | 审计日志查看 |

系统管理员（`system_role = 1`）拥有管理端绕过权限，但仍然必须经过认证、记录审计并遵守不可破坏的数据约束。普通管理员的能力来自实时角色授权，而不是前端隐藏菜单。

### 6.2 数据范围

角色数据范围支持：

- `ALL`：全公司范围。
- `SELF`：仅本人负责/创建/被分配的数据。
- `SELF_AND_SUBORDINATES`：本人及直属下属。
- `ORG`：本人主归属组织。
- `ORG_AND_DESCENDANTS`：本人组织及所有下级组织。
- `CUSTOM_ORGS`：角色绑定的指定组织集合。

数据范围由 `DataScopeResolver` 在服务端解析。范围解析失败、未知范围或组织已停用时，应采取最小权限原则；不能因为前端传入了组织 ID 就扩大访问范围。列表查询、详情查询、写入、删除和导出都必须使用同一套范围策略。

## 7. 认证与安全规则

1. 登录接口以邮箱为核心，服务端先执行 trim + 小写规范化再查询；旧英文名仅作为兼容字段；错误提示不能泄露“账号不存在”还是“密码错误”的可枚举信息。
2. 成功登录签发访问令牌和刷新令牌；刷新令牌只存哈希，退出登录或停用账号时撤销会话。
3. 连续失败登录会累加 `failed_login_count`，达到策略后进入 `LOCKED`；成功登录会重置失败计数并记录 `last_login_at`。
4. 密码重置令牌只存哈希并设置有效期，默认不在生产响应中暴露明文令牌。
5. JWT 密钥必须通过 `PMS_JWT_SECRET` 注入，生产随机强度至少 32 字节；账号密码、令牌和数据库密码不能写入日志或提交到仓库。
6. CORS 只允许显式配置的前端来源；健康检查 `/api/health`、`/api/healthz` 可匿名访问，其余接口默认需要 `Authorization: Bearer <token>`。
7. 认证失败返回 401，权限不足返回 403，参数错误返回 400，业务异常返回可读的错误信息和 `requestId`；前端不得把 500 当作“无数据”。

## 8. API 约定

### 8.1 返回与分页

接口统一使用 `ResponseResult` 包装；分页使用 `PageResult`，请求参数沿用 `currPage` 和 `pageSize`。页码最小为 1，人员分页大小限制为 1–100，其他分页接口也应设置合理上限，避免无界查询。

### 8.2 接口分组

| 领域 | 主要接口 |
| --- | --- |
| 认证 | `POST /auth/login`、`/auth/refresh`、`/auth/logout`、密码修改/激活/重置、`GET /auth/me` |
| 项目 | `POST /projects/page`、项目 CRUD、`/terminate`、`/restore`、统计 |
| 节点与任务 | `/projects/{projectId}/nodes`、`/projects/{projectId}/tasks`、`/tasks/{id}/move` |
| 协作 | 成员、关注人、里程碑、评论接口 |
| 组织 | `GET /org/tree`（项目选择器）、`/admin/org/tree` 及组织 CRUD/移动 |
| 人员 | `/admin/users` 分页、邀请、主归属、兼职归属、角色、停用 |
| 角色 | `/admin/roles` CRUD 和权限/数据范围 |
| 导入 | 组织/用户预览、提交、CSV 模板 |
| 审计 | `GET /admin/audit`，按动作、资源、人员、时间和分页筛选 |

写接口应做到幂等或明确返回冲突；删除、停用、终止等危险动作必须在服务端再次校验当前状态和权限。

## 9. Excel/CSV 导入规范

导入是“预览 → 校验 → 提交”的两阶段流程：

1. 管理员上传 Excel/CSV，服务端限制文件类型、大小（当前实现上限 5 MB）和行数（当前实现上限 5000 行）。
2. 预览阶段只解析和校验，不写业务表；返回逐行错误、规范化结果和汇总数量。
3. 组织导入先处理父子关系和编码冲突；员工导入要求邮箱和主组织编码，中文名/英文名可选，直属上级优先填写邮箱（兼容旧英文名），同时校验角色和日期。
4. 提交阶段使用导入任务 ID，在事务内写入组织、用户、归属和角色，并写入 `sys_import_job` 与操作日志；失败应整体回滚，不产生半批数据。
5. 同一规范化邮箱、组织编码或角色编码重复时必须明确报错；英文名如填写也必须唯一；已存在记录只能按产品定义的更新策略处理，不能静默覆盖。
6. 导入完成后可在审计日志中通过 `IMPORT_COMMITTED` 和导入任务 ID 追踪；预览结果过期后不可再次提交。

## 10. OceanBase 与部署

- 默认 profile 为 `oceanbase`，使用 MySQL 兼容模式和 2881 端口，数据库名为 `brad_pms`。
- 生产连接参数通过 `OCEANBASE_HOST`、`OCEANBASE_PORT`、`OCEANBASE_DATABASE`、`OCEANBASE_USER`、`OCEANBASE_PASSWORD` 注入。
- 表结构按 V1–V10 版本化迁移顺序执行：V1–V7 完成基础企业模型与邮箱身份，V8 增加任务附件，V9 增加站内通知，V10 增加通知节点标识。
- V5 为现有业务表补充逻辑删除标记、乐观锁版本号、关键唯一索引和跨表外键；应用查询必须遵守逻辑删除条件，关键更新必须携带版本号。
- OceanBase profile 运行时不依赖 Flyway 自动执行；升级前先执行预检、备份策略和迁移脚本，再启动应用。
- `application-h2.yml` 和 H2 快照只服务自动化测试/一次性迁移工具；文档、示例和上线脚本不得引导用户用 H2 运行生产。
- 启动后通过 `/api/health` 或 `/api/healthz` 验证应用与数据库连通性；连接失败时优先检查网络、租户、账号权限和字符集，不要切换到 H2 掩盖故障。

## 11. 审计、可观测性与测试

所有关键变更至少记录：操作人、动作、资源类型、资源 ID、变更前后摘要、请求 ID和时间。登录日志额外记录登录名、结果、失败原因、IP 和 User-Agent；敏感值只能保存哈希或脱敏摘要。

每个请求可通过 `X-Request-Id` 关联前后端日志。发布前至少执行：

- `mvn test`：认证、权限、数据范围、组织、导入、控制器和配置测试。
- 迁移脚本在目标 OceanBase 实例上执行并验证表、索引、种子角色和健康检查。
- 对匿名访问、401/403、主归属唯一性、组织负责人独立关系、项目列表/详情一致性进行验收。
- 检查日志不包含密码、JWT、刷新令牌、重置令牌和数据库连接密码。

## 12. 变更规则

新增业务字段先补充领域模型和迁移脚本，再实现服务和接口；删除或重命名字段必须提供兼容期或数据迁移。新增权限点必须同时更新角色种子、后端 `PermissionCode`、前端路由守卫和本文件的权限表。任何影响主归属、组织负责人、数据范围、项目经理或导入行为的修改，都必须补充对应测试和审计说明。
