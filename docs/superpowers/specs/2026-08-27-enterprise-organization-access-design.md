# 企业组织、权限与身份中心设计

## 1. 目标与边界

将当前 PMS 从 MVP 的“用户 + 管理员二值角色 + 项目成员”升级为适用于企业私有化部署的组织、身份与权限中心。

- **部署模型：** 开源、自托管、单企业。每个企业独立部署应用和数据库；本期不引入 `tenant_id` 或多租户隔离。
- **组织模型：** 一棵主组织树，组织类型可配置；员工一个主归属，可有多个兼职任职和多个项目成员关系。
- **授权模型：** 可配置 RBAC，角色控制菜单、按钮、API 与数据范围；项目内成员/负责人/节点负责人规则继续生效。
- **账号模型：** 邮箱是新账号的唯一核心身份，登录邮箱不区分大小写；中文姓名和英文名均为可选展示字段。展示优先为 `中文名（English.Name）`，缺少姓名时回退到邮箱；历史英文名仅作为兼容登录字段。
- **注册模型：** 无公开注册。首次部署初始化超级管理员；之后管理员邀请、创建、批量导入员工，员工使用邀请链接激活账号。
- **本期不做：** 多租户 SaaS、外部 SSO/LDAP 的具体连接器、工作流审批、工资/人事档案管理。认证接口需为未来的企业微信、飞书、LDAP/AD 预留扩展点。

## 2. 当前系统与改造原则

当前后端为 Spring Boot 2.7 + Java 17 + MyBatis-Plus，数据库兼容 H2、MySQL 8 和 OceanBase MySQL 模式；前端为 Vue 3 + TypeScript + Ant Design Vue。`sys_user` 当前只含 `username`、`nickname` 和 `system_role`，现有项目权限由 `ProjectPermissionPolicy` 和项目成员关系判定。

改造遵循以下原则：

1. 保留项目、任务、节点、成员等现有业务表和 ID；通过新增表与新增列完成升级，禁止清库或物理删除历史数据。
2. 后端为所有权限的最终裁决者；前端的路由、菜单和按钮隐藏只用于改善体验。
3. 组织树只表达主汇报关系，禁止一个节点或主归属出现两个父节点；矩阵协作以兼职任职和 `project_member` 表达。
4. 所有组织、人员、角色、授权、导入和安全关键操作必须写入审计日志。
5. 数据库结构使用版本化、幂等的迁移脚本，分别验证 H2、MySQL 和 OceanBase。

## 3. 领域模型与数据库设计

### 3.1 企业、组织与岗位

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `sys_company_profile` | `id`, `name`, `logo`, `timezone`, `security_policy_json` | 当前部署企业的配置，仅允许一条有效记录。 |
| `sys_org_unit_type` | `id`, `code`, `name`, `sort`, `enabled` | 组织类型字典，例如业务线、BG、中心、部门、团队、PDT。 |
| `sys_org_unit` | `id`, `parent_id`, `type_id`, `code`, `name`, `leader_user_id`, `sort`, `status`, `path` | 主组织树。`code` 唯一，`path` 为可查询的祖先路径，`status` 为启用/停用。 |
| `sys_position` | `id`, `code`, `name`, `enabled` | 岗位字典，例如研发工程师、部门负责人、项目经理。 |
| `sys_user_position` | `id`, `user_id`, `org_unit_id`, `position_id`, `manager_user_id`, `assignment_type`, `is_primary`, `start_date`, `end_date`, `status` | 员工任职记录；`assignment_type` 为 `PRIMARY` 或 `PART_TIME`；同一用户仅允许一条在职主归属。 |

组织新增、移动、停用、删除的规则：

- 根组织不可删除或停用。
- 有启用子组织、在职主归属、项目归属或待处理兼职记录的节点不可直接删除。
- 管理员先在迁移向导中选择目标组织，再迁移人员、下级组织和项目归属；完成后只将原节点标记为停用。
- 负责人不是唯一权限来源。组织负责人通过角色绑定或数据范围获得授权，避免“仅因负责人字段而越权”。

### 3.2 用户与认证

扩展 `sys_user`，保留现有主键和项目外键引用：

| 字段 | 规则 |
| --- | --- |
| `name_zh` | 可选中文姓名；由旧 `nickname` 回填，但不再强制要求。 |
| `username` | 可选英文名，例如 `Alex.Zhang`；历史客户端仍可将其作为登录标识。 |
| `username_normalized` | `username` 的 `Locale.ROOT` 小写值；兼容字段唯一索引，防止旧客户端冲突。 |
| `email` | 新账号必填的登录邮箱，保留用户输入的展示形式。 |
| `email_normalized` | `email` trim 后的 `Locale.ROOT` 小写值；新账号唯一索引和登录查询键。 |
| `password` | BCrypt 密码哈希；从不返回 DTO 或日志。 |
| `status` | `PENDING_ACTIVATION`、`ACTIVE`、`LOCKED`、`DISABLED`。 |
| `failed_login_count`、`locked_until`、`last_login_at`、`password_changed_at` | 登录安全状态。 |
| `system_role` | 迁移期兼容字段；迁移完成后只读，权限以 RBAC 角色为准。 |

新增认证表：

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `sys_invitation` | `id`, `user_id`, `token_hash`, `expires_at`, `status`, `created_by` | 邀请激活链接；只保存 token 哈希。 |
| `sys_auth_session` | `id`, `user_id`, `refresh_token_hash`, `expires_at`, `revoked_at`, `ip`, `user_agent` | 可撤销刷新会话。 |
| `sys_password_reset_token` | `id`, `user_id`, `token_hash`, `expires_at`, `used_at` | 密码重置令牌。 |
| `sys_login_log` | `id`, `user_id`, `login_name`, `result`, `reason`, `ip`, `created_at` | 成功、失败、锁定和退出的认证审计。 |

认证规则：

1. 登录请求优先以 `Locale.ROOT` 将邮箱 trim 并转为小写，再按 `email_normalized` 精确查询；未提供邮箱时，兼容查询 `username_normalized`；密码使用 BCrypt 校验。
2. 返回短时 Access Token，并以 HttpOnly、Secure、SameSite 的刷新会话完成续期；浏览器不得在 `localStorage` 持久化访问令牌。
3. 被停用、锁定、未激活的账号不得建立或刷新会话；禁用、重置密码、主动退出会撤销所有对应会话。
4. 第一次启动必须以运行时配置创建超级管理员；不再把 `admin / admin123` 作为生产默认账号。
5. `AuthProvider` 定义本地账号认证接口，未来的企业微信、飞书、LDAP/AD 实现该接口而不改写业务授权服务。

### 3.3 角色、权限与数据范围

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `sys_role` | `id`, `code`, `name`, `builtin`, `data_scope_type`, `enabled` | 角色定义；内置角色不可删除。 |
| `sys_permission` | `id`, `code`, `name`, `resource_type`, `parent_id`, `http_method`, `path_pattern`, `sort` | 菜单、按钮和 API 权限点。 |
| `sys_role_permission` | `role_id`, `permission_id` | 角色拥有的权限点。 |
| `sys_user_role` | `id`, `user_id`, `role_id`, `scope_org_unit_id`, `start_at`, `end_at`, `status` | 将角色授予人员，可限定组织范围和有效期。 |
| `sys_role_org_scope` | `role_id`, `org_unit_id` | `CUSTOM_ORGS` 数据范围的指定组织集合。 |
| `sys_operation_log` | `id`, `operator_id`, `action`, `resource_type`, `resource_id`, `before_json`, `after_json`, `request_id`, `created_at` | 不可变的管理操作审计。 |

`data_scope_type` 只允许：`SELF`、`SELF_AND_SUBORDINATES`、`ORG`、`ORG_AND_DESCENDANTS`、`CUSTOM_ORGS`、`ALL`。初始内置角色为超级管理员、组织管理员、业务线负责人、部门负责人、项目管理员、项目经理、普通员工。

权限判定顺序：

1. 校验 Access Token、账号状态和会话撤销状态。
2. 加载用户在当前时间有效的 `sys_user_role`，合并其权限点和数据范围。
3. 对 API/菜单/按钮检查目标权限编码；超级管理员拥有全部权限，但仍需账号有效。
4. 对人员、组织和项目查询构造组织范围过滤条件。
5. 对项目的写操作再执行既有项目成员、项目经理、节点负责人和任务负责人规则。组织角色不会绕过项目生命周期的只读限制。

### 3.4 项目与历史数据

- `project` 新增 `org_unit_id`，标识项目的主归属组织；跨组织协作继续使用 `project_member`。
- 项目列表默认只返回“用户拥有组织数据范围的项目”或“用户为项目成员的项目”的并集。
- 人员离职仅将 `sys_user.status` 设为 `DISABLED`，结束有效任职并撤销会话；不删除用户、项目成员、评论或日志。
- 转岗创建新的任职记录并结束旧记录；项目历史和审计继续引用原用户 ID。

## 4. 管理页面与交互

### 4.1 导航

沿用 PMS 的冷灰工作台设计系统。侧栏采用用户确认的模块菜单：

```text
工作台
项目管理
配置管理（可展开）
  人员与权限
  组织架构
  角色管理
  审计日志
```

`配置管理` 及子菜单使用 14px 导航字号；这是对现有 13px 正文令牌的有意扩展，新增 `--pms-font-size-nav: 14px` 并在设计规范中说明。页面继续使用白色顶栏、236px 白色侧栏、`--pms-bg` 内容背景、8px 面板圆角与 36px 控件高度。

### 4.2 人员与权限

- 左侧组织树筛选，右侧人员表格展示 `中文名（英文名）` 或邮箱、主归属、岗位、角色、账号状态。
- 支持按中文名、英文名、邮箱、岗位、组织和角色查询；管理抽屉集中编辑账号状态、主归属、兼职、岗位、角色和数据范围。
- “邀请员工”创建待激活账号；支持重发邀请、停用、重置密码、转岗和离职。
- 人员选择器、项目成员、任务负责人、评论与审计日志全部复用统一 `displayName`，禁止只展示 `nickname`。

### 4.3 组织架构

- 画布渲染主组织树，节点展示名称、组织类型、负责人和人数；右侧属性面板编辑选中节点。
- 拖拽前弹出确认并调用移动接口；后端阻止循环引用、跨根移动和无权限移动。
- 新增、停用和删除使用向导，不提供直接物理删除。
- PDT 可作为一种组织类型；跨 BG 的人员通过兼职和项目成员标记展示，不创建第二个主父节点。

### 4.4 角色、审计与导入

- 角色页按“权限树 + 数据范围 + 已授权人员”配置角色；超级管理员角色受保护。
- 审计页可按操作人、资源、动作、时间筛选，详情显示变更前后字段差异，不显示密码、令牌或秘密。
- 支持 Excel 与 CSV 模板下载、上传、预览、字段校验和结果下载。组织导入以组织编码和父组织编码建树；员工导入以邮箱和主组织编码为必填，中文名、英文名、岗位、角色可选，直属上级优先填写邮箱并兼容旧英文名。
- 导入在预览阶段发现任一结构或唯一性错误时不提交数据；提交阶段单事务写入，并记录每行结果和操作审计。

## 5. API 边界

| 前缀 | 能力 |
| --- | --- |
| `/auth` | 登录、刷新、退出、邀请激活、重置密码；首次管理员通过部署环境变量初始化。 |
| `/admin/users` | 人员分页、邀请、状态变更、任职、角色、导入。 |
| `/admin/org` | 组织树、新增、移动、停用；权限点为 `admin:org:*`。 |
| `/admin/roles` | 角色、权限点、数据范围配置；权限点作为角色编辑的一部分维护。 |
| `/admin/audit` | 审计日志查询（当前返回最近 200 条，支持动作筛选）。 |
| `/projects` | 保留现有资源路径，内部增加组织数据范围过滤与 `org_unit_id` 写入。 |

所有 `/admin/**` 接口均以权限编码保护；首次管理员不开放公开 setup 接口，而是在持久化 profile 首次启动时读取必填的 `PMS_BOOTSTRAP_ADMIN_EMAIL`、`PMS_BOOTSTRAP_ADMIN_PASSWORD`，以及可选的中文名/英文名。

## 6. 迁移、回滚与安全

1. 迁移前备份生产库并运行只读预检：检查邮箱归一化冲突、旧英文名归一化冲突、孤儿项目、缺失项目成员用户与组织编码冲突。
2. 使用版本化迁移创建新表、索引和 `project.org_unit_id`；V7 增加 `email_normalized` 并回填历史邮箱。
3. 旧 `nickname` 迁移到可选 `name_zh`；旧 `username` 保留为兼容展示/登录字段并生成归一化值。若邮箱或旧英文名归一化冲突，迁移停止并报告冲突账号，不自动改名。
4. 旧 `system_role=1` 映射到内置超级管理员角色；普通用户映射普通员工角色并创建主归属记录。
5. 现有项目归属公司总部，后续由组织管理员在页面迁移到实际业务线。
6. 成功前不删除旧字段或数据；回滚以应用版本回退和迁移前数据库备份恢复为准，禁止自动 `DROP`、`TRUNCATE` 或覆盖非空生产库。

## 7. 验收标准

- 以 `Alex@Example.com`、`alex@example.com`、` ALEX@EXAMPLE.COM ` 均可登录同一账号，且不能创建大小写不同的重复邮箱；旧英文名登录仍在兼容期内可用。
- 有姓名时人员展示为 `中文名（英文名）`，缺少任一姓名时按可用姓名展示，姓名均缺少时展示邮箱；项目成员、负责人、评论和审计日志不单独展示旧 `nickname`。
- 管理员可创建组织、设置负责人、移动组织、设置主归属/兼职与转岗；非法循环、无迁移删除和多主归属均被拒绝。
- 角色配置可控制菜单、按钮、API 与数据范围；前端隐藏不替代后端拒绝。
- 禁用员工立即失去访问权，历史项目与审计仍可查询；转岗不改变历史任职记录。
- Excel/CSV 导入能下载模板、给出行级错误、在全部预检通过后一次性写入并生成导入日志。
- 现有项目、节点、任务、里程碑、成员、评论和权限生命周期回归测试通过；H2、MySQL 和 OceanBase 均能完成迁移验证。
