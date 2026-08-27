# 企业组织、权限与身份中心 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为自托管单企业 PMS 实现组织架构、岗位任职、可配置 RBAC、账号邀请登录、批量导入和审计，并将其接入现有项目权限。

**Architecture:** 保留 `sys_user` 和现有项目表的主键及引用关系，通过 Flyway 版本化迁移扩展身份字段并添加组织、授权、会话、导入和审计表。后端以“认证 → 权限点 → 组织数据范围 → 现有项目规则”的顺序决定访问权；Vue 前端以“配置管理”可展开菜单提供人员、组织、角色和审计页面。

**Tech Stack:** Java 17、Spring Boot 2.7、MyBatis-Plus、Flyway、BCrypt、JJWT、Apache POI、Apache Commons CSV、JUnit 5、Vue 3、TypeScript、Pinia、Vue Router、Ant Design Vue、pnpm。

**Spec:** `docs/superpowers/specs/2026-08-27-enterprise-organization-access-design.md`

## Global Constraints

- 单企业私有化部署；任何核心表不得新增 `tenant_id`。
- 英文登录名在应用层使用 `Locale.ROOT` 小写归一化，并由 `sys_user.username_normalized` 唯一索引保证大小写无关唯一性。
- 用户展示固定为 `name_zh（username）`；不得继续把 `nickname` 作为唯一展示名称。
- 一个在职用户只能有一条 `PRIMARY` 任职记录；兼职用 `PART_TIME` 任职记录表示。
- 组织和人员不做物理删除；离职禁用账号并结束任职，转岗保留旧任职历史。
- 权限校验只能在后端授权；前端菜单与按钮隐藏不是安全边界。
- 所有组织、人员、角色、授权、导入和会话撤销必须写入 `sys_operation_log`，且日志不得含密码、令牌或其哈希。
- 迁移不得执行 `DROP`、`TRUNCATE` 或覆盖非空业务库；H2、MySQL 8、OceanBase MySQL 模式均须通过验证。
- 管理页面沿用 PMS 设计系统；新增 `--pms-font-size-nav: 14px`，控件高度 36px，面板圆角 8px。

---

### Task 1: Establish migration infrastructure and enterprise schema

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/db/migration/V1__baseline_project_schema.sql`
- Create: `src/main/resources/db/migration/V2__enterprise_identity_org_rbac.sql`
- Create: `src/test/java/com/brad/pms/config/EnterpriseSchemaMigrationTest.java`
- Modify: `src/main/resources/schema.sql`

**Interfaces:**
- Flyway owns schema upgrades using `flyway_schema_history`; the legacy project schema is version `1`, and enterprise identity/organization/RBAC tables are version `2`.
- Existing non-empty databases are baselined at version `1` before `V2` runs; fresh databases apply `V1` and then `V2` without deleting existing data.
- SQL initialization is disabled for persistent profiles so `schema.sql` is never replayed over an existing database.

- [ ] **Step 1: Add a failing schema-contract test**

```java
@Test
void enterpriseSchemaContainsIdentityOrganizationAndRbacTables() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
        assertThat(tableExists(connection, "sys_org_unit")).isTrue();
        assertThat(tableExists(connection, "sys_user_position")).isTrue();
        assertThat(tableExists(connection, "sys_role")).isTrue();
        assertThat(tableExists(connection, "sys_auth_session")).isTrue();
        assertThat(columnExists(connection, "sys_user", "username_normalized")).isTrue();
        assertThat(columnExists(connection, "project", "org_unit_id")).isTrue();
    }
}
```

- [ ] **Step 2: Run the focused test to verify the contract is absent**

Run: `mvn -q -Dtest=EnterpriseSchemaMigrationTest test`

Expected: FAIL because the enterprise tables and columns do not exist.

- [ ] **Step 3: Add Flyway and migration configuration**

Add `org.flywaydb:flyway-core`, `org.apache.poi:poi-ooxml`, and `org.apache.commons:commons-csv` to `pom.xml`. Configure Flyway to baseline existing schemas at `1` and run `classpath:db/migration`; keep the legacy `schema.sql` content copied into `V1__baseline_project_schema.sql` for fresh databases.

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1
    locations: classpath:db/migration
```

- [ ] **Step 4: Write the V2 enterprise migration with safe indexes and status checks**

Create every table named in the specification. Use `VARCHAR` status values for portability, `TIMESTAMP` audit fields, `UNIQUE (username_normalized)`, `UNIQUE (code)` on organization and position dictionaries, and indexes such as:

```sql
CREATE INDEX idx_org_parent_status ON sys_org_unit (parent_id, status, sort);
CREATE INDEX idx_user_position_user_status ON sys_user_position (user_id, status, is_primary);
CREATE INDEX idx_user_role_user_status ON sys_user_role (user_id, status, start_at, end_at);
CREATE INDEX idx_operation_log_resource ON sys_operation_log (resource_type, resource_id, created_at);
CREATE INDEX idx_project_org_unit ON project (org_unit_id, status);
```

Use `ALTER TABLE ... ADD COLUMN` only through an idempotent Java compatibility guard for old databases when dialect syntax differs; do not use destructive DDL.

- [ ] **Step 5: Make fresh and existing schema initialization deterministic**

Keep `schema.sql` as a legacy reference only; Flyway owns initialization in all profiles. Ensure the migration test starts H2 with Flyway enabled and verifies a second application context start does not duplicate indexes or seed rows.

- [ ] **Step 6: Run migration verification**

Run: `mvn -q -Dtest=EnterpriseSchemaMigrationTest test`

Expected: PASS with all enterprise tables, `sys_user.username_normalized`, `sys_user.name_zh`, and `project.org_unit_id` present.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/resources/application-mysql.yml src/main/resources/application-oceanbase.yml src/main/resources/db/migration/V1__baseline_project_schema.sql src/main/resources/db/migration/V2__enterprise_identity_org_rbac.sql src/test/java/com/brad/pms/config/EnterpriseSchemaMigrationTest.java
git commit -m "feat: add enterprise organization schema"
```

### Task 2: Migrate legacy users, seed protected roles, and define enterprise entities

**Files:**
- Modify: `src/main/java/com/brad/pms/entity/UserDO.java`
- Create: `src/main/java/com/brad/pms/entity/OrgUnitDO.java`
- Create: `src/main/java/com/brad/pms/entity/OrgUnitTypeDO.java`
- Create: `src/main/java/com/brad/pms/entity/PositionDO.java`
- Create: `src/main/java/com/brad/pms/entity/UserPositionDO.java`
- Create: `src/main/java/com/brad/pms/entity/RoleDO.java`
- Create: `src/main/java/com/brad/pms/entity/PermissionDO.java`
- Create: `src/main/java/com/brad/pms/entity/UserRoleDO.java`
- Create: `src/main/java/com/brad/pms/common/enums/UserStatus.java`
- Create: `src/main/java/com/brad/pms/common/enums/AssignmentType.java`
- Create: `src/main/java/com/brad/pms/common/enums/DataScopeType.java`
- Create: `src/main/java/com/brad/pms/config/EnterpriseDataMigration.java`
- Create: `src/test/java/com/brad/pms/config/EnterpriseDataMigrationTest.java`

**Interfaces:**
- `UserDO` exposes `nameZh`, `usernameNormalized`, `status`, `failedLoginCount`, `lockedUntil`, `lastLoginAt`, and `passwordChangedAt`.
- `EnterpriseDataMigration` is idempotent and creates `公司总部`, `待分配人员`, protected built-in roles, one primary position per legacy user, and a root organization value for legacy projects.

- [ ] **Step 1: Write migration behavior tests**

```java
@Test
void migrationNormalizesLegacyNamesAndMapsAdministratorToProtectedRole() {
    runMigration();
    UserDO admin = userMapper.findByUsernameNormalized("admin");
    assertThat(admin.getNameZh()).isEqualTo("管理员");
    assertThat(admin.getUsernameNormalized()).isEqualTo("admin");
    assertThat(userRoleMapper.countActiveRole(admin.getId(), "SUPER_ADMIN")).isEqualTo(1);
    assertThat(userPositionMapper.countActivePrimary(admin.getId())).isEqualTo(1);
}
```

- [ ] **Step 2: Run the test to verify legacy data is not yet migrated**

Run: `mvn -q -Dtest=EnterpriseDataMigrationTest test`

Expected: FAIL because normalized names, roles, and primary positions are absent.

- [ ] **Step 3: Add entities and enum contracts**

Define `UserStatus` as `PENDING_ACTIVATION`, `ACTIVE`, `LOCKED`, `DISABLED`; `AssignmentType` as `PRIMARY`, `PART_TIME`; and `DataScopeType` as the six values in the spec. Annotate each entity with the matching `@TableName`; use `LocalDate` for employment dates and `LocalDateTime` for audit/security timestamps.

- [ ] **Step 4: Implement deterministic data backfill**

In one `@Transactional` `CommandLineRunner`, first stop with a clear error if two existing users lower-case to the same `username_normalized`. Then create the root/holding organization, built-in roles, default permissions, user roles and primary positions. Update each old project whose `org_unit_id` is null to the root organization. Never overwrite a non-null normalized name, existing role binding, or active position.

- [ ] **Step 5: Replace insecure demo initialization behavior**

Change `DataInitializer` so local sample users explicitly receive `nameZh`, canonical English `username`, normalized username, and active status. In production profiles, initialize the first administrator only from `PMS_BOOTSTRAP_ADMIN_USERNAME`, `PMS_BOOTSTRAP_ADMIN_NAME_ZH`, and `PMS_BOOTSTRAP_ADMIN_PASSWORD`; reject absent bootstrap credentials when no user exists.

- [ ] **Step 6: Run data migration tests**

Run: `mvn -q -Dtest=EnterpriseDataMigrationTest test`

Expected: PASS, including a second run with no duplicate root organization, roles, role bindings, or primary positions.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/brad/pms/entity src/main/java/com/brad/pms/common/enums src/main/java/com/brad/pms/config/EnterpriseDataMigration.java src/main/java/com/brad/pms/config/DataInitializer.java src/test/java/com/brad/pms/config/EnterpriseDataMigrationTest.java
git commit -m "feat: migrate legacy users into enterprise identity model"
```

### Task 3: Implement account identity, case-insensitive login, and revocable sessions

**Files:**
- Modify: `src/main/java/com/brad/pms/mapper/UserMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/AuthSessionMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/InvitationMapper.java`
- Modify: `src/main/java/com/brad/pms/service/AuthService.java`
- Modify: `src/main/java/com/brad/pms/security/JwtTokenProvider.java`
- Modify: `src/main/java/com/brad/pms/security/AuthInterceptor.java`
- Modify: `src/main/java/com/brad/pms/security/LoginUser.java`
- Modify: `src/main/java/com/brad/pms/dto/request/LoginRequest.java`
- Create: `src/main/java/com/brad/pms/dto/request/RefreshTokenRequest.java`
- Create: `src/main/java/com/brad/pms/dto/response/SessionResponse.java`
- Modify: `src/main/java/com/brad/pms/controller/AuthController.java`
- Create: `src/test/java/com/brad/pms/service/AuthServiceTest.java`
- Create: `src/test/java/com/brad/pms/security/AuthInterceptorTest.java`

**Interfaces:**
- `UserMapper.findByUsernameNormalized(String normalized)` is the only username lookup used for login.
- `AuthService.login(LoginRequest)` returns `SessionResponse { accessToken, refreshToken, user }`; the refresh token is persisted only as a SHA-256 hash.
- `AuthService.revokeAllSessions(Long userId, String reason)` is invoked by password reset and account disable flows.

- [ ] **Step 1: Write failing case-insensitive authentication tests**

```java
@ParameterizedTest
@ValueSource(strings = {"brad.xie", "Brad.Xie", "BRAD.XIE"})
void loginUsesNormalizedEnglishName(String loginName) {
    LoginResponse response = authService.login(new LoginRequest(loginName, "CorrectPassword1!"));
    assertThat(response.getUser().getDisplayName()).isEqualTo("谢斌（Brad.Xie）");
}

@Test
void disabledUserCannotRefreshSession() {
    disable(userId);
    assertThatThrownBy(() -> authService.refresh(refreshToken)).isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 2: Run the authentication tests to verify they fail**

Run: `mvn -q -Dtest=AuthServiceTest,AuthInterceptorTest test`

Expected: FAIL because login still queries the case-sensitive legacy username and sessions cannot be revoked.

- [ ] **Step 3: Implement normalized lookup and display-name conversion**

```java
public static String normalizeUsername(String username) {
    return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
}

public static String displayName(UserDO user) {
    return user.getNameZh() + "（" + user.getUsername() + "）";
}
```

Validate English names against `^[A-Za-z][A-Za-z0-9._-]{1,49}$`, generate `usernameNormalized` on every create/update/import path, and return `displayName` from `UserDTO`.

- [ ] **Step 4: Add session issuance, refresh, and revocation**

Use `SecureRandom` to create opaque refresh tokens, persist only a SHA-256 hash with expiry and revocation fields, and issue an access JWT containing only user ID and a session ID. The interceptor must load the current user and session status from the database, reject disabled/locked users, and clear `UserContext` after completion.

- [ ] **Step 5: Add login lockout and audit recording**

Increment failed counters on bad password, set `locked_until` after the configured threshold, reset the counter on successful login, and insert `sys_login_log` records without password data. Make lock threshold and access/refresh expiry values configurable in `application.yml`.

- [ ] **Step 6: Run focused authentication tests**

Run: `mvn -q -Dtest=AuthServiceTest,AuthInterceptorTest test`

Expected: PASS for normalized login, disabled users, expired/revoked sessions, lockout, and password secrecy.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/brad/pms/mapper src/main/java/com/brad/pms/service/AuthService.java src/main/java/com/brad/pms/security src/main/java/com/brad/pms/controller/AuthController.java src/main/java/com/brad/pms/dto src/test/java/com/brad/pms/service/AuthServiceTest.java src/test/java/com/brad/pms/security/AuthInterceptorTest.java src/main/resources/application.yml
git commit -m "feat: add normalized login and revocable sessions"
```

### Task 4: Build the RBAC permission and organization-scope policy

**Files:**
- Create: `src/main/java/com/brad/pms/security/PermissionCode.java`
- Create: `src/main/java/com/brad/pms/security/AuthorizationService.java`
- Create: `src/main/java/com/brad/pms/security/DataScopeResolver.java`
- Create: `src/main/java/com/brad/pms/security/RequirePermission.java`
- Modify: `src/main/java/com/brad/pms/security/AuthInterceptor.java`
- Create: `src/main/java/com/brad/pms/mapper/RoleMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/PermissionMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/UserRoleMapper.java`
- Create: `src/main/java/com/brad/pms/mapper/OrgUnitMapper.java`
- Modify: `src/main/java/com/brad/pms/common/exception/GlobalExceptionHandler.java`
- Create: `src/test/java/com/brad/pms/security/AuthorizationServiceTest.java`
- Create: `src/test/java/com/brad/pms/security/DataScopeResolverTest.java`

**Interfaces:**
- `AuthorizationService.require(String permissionCode)` throws `BusinessException.forbidden` when the current user lacks a live role grant.
- `DataScopeResolver.resolveOrgUnitIds(LoginUser user, String permissionCode)` returns the allowed organization IDs for `SELF`, subordinate, organization, descendant, custom, and all-company scopes.
- Controller methods use `@RequirePermission("admin:user:read")`; the interceptor enforces it before service execution.

- [ ] **Step 1: Specify denied-path tests before policy code**

```java
@Test
void departmentManagerCannotReadSiblingDepartmentUsers() {
    LoginUser manager = loginAs("sales.manager");
    assertThat(scopeResolver.resolveOrgUnitIds(manager, "admin:user:read"))
        .containsExactlyInAnyOrder(salesId, salesTeamAId, salesTeamBId)
        .doesNotContain(productId);
}

@Test
void missingApiPermissionReturnsForbidden() {
    assertThatThrownBy(() -> authorizationService.require("admin:role:update"))
        .isInstanceOf(BusinessException.class)
        .hasMessage("无权执行此操作");
}
```

- [ ] **Step 2: Run policy tests to verify they fail**

Run: `mvn -q -Dtest=AuthorizationServiceTest,DataScopeResolverTest test`

Expected: FAIL because current code only knows `system_role`.

- [ ] **Step 3: Implement permission loading and protected system role rules**

Seed permission codes for every admin API and menu. `SUPER_ADMIN` is `builtin=true`; reject deletion, disabling, or removal of the last active super administrator. Aggregate valid time-bounded user-role grants and choose the union of their permissions/data scopes.

- [ ] **Step 4: Implement organization scope expansion without string interpolation**

Resolve descendants by a parameterized mapper query against `sys_org_unit.path` or recursive parent traversal in Java; never concatenate user input into SQL. For `SELF_AND_SUBORDINATES`, resolve the current user’s direct/indirect report chain from active `sys_user_position.manager_user_id`.

- [ ] **Step 5: Apply permission annotation enforcement**

Make `AuthInterceptor` inspect `@RequirePermission` on the handler method/type after authentication. Preserve `@IgnoreAuth`; return code `403` via `GlobalExceptionHandler` for authorization failures.

- [ ] **Step 6: Run RBAC policy tests**

Run: `mvn -q -Dtest=AuthorizationServiceTest,DataScopeResolverTest test`

Expected: PASS for all six data scopes, expired grants, protected super admin, and denied APIs.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/brad/pms/security src/main/java/com/brad/pms/mapper src/main/java/com/brad/pms/common/exception/GlobalExceptionHandler.java src/test/java/com/brad/pms/security/AuthorizationServiceTest.java src/test/java/com/brad/pms/security/DataScopeResolverTest.java
git commit -m "feat: add rbac permission and data scope policy"
```

### Task 5: Implement organization, position, and personnel lifecycle services

**Files:**
- Create: `src/main/java/com/brad/pms/service/OrgUnitService.java`
- Create: `src/main/java/com/brad/pms/service/PersonnelService.java`
- Create: `src/main/java/com/brad/pms/service/OperationLogService.java`
- Create: `src/main/java/com/brad/pms/dto/request/OrgUnitCreateCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/OrgUnitMoveCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/UserPositionCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/UserDisableCmd.java`
- Create: `src/main/java/com/brad/pms/dto/response/OrgUnitTreeDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/PersonnelDTO.java`
- Create: `src/main/java/com/brad/pms/controller/AdminOrgUnitController.java`
- Create: `src/main/java/com/brad/pms/controller/AdminUserController.java`
- Create: `src/test/java/com/brad/pms/service/OrgUnitServiceTest.java`
- Create: `src/test/java/com/brad/pms/service/PersonnelServiceTest.java`

**Interfaces:**
- `OrgUnitService.move(Long id, OrgUnitMoveCmd cmd)` rejects moves that introduce an ancestor cycle.
- `PersonnelService.changePrimaryPosition(Long userId, UserPositionCmd cmd)` ends the current primary record and creates exactly one new active primary record atomically.
- `PersonnelService.disable(Long userId, UserDisableCmd cmd)` disables the account, ends active positions, revokes sessions, and writes an operation log.

- [ ] **Step 1: Write failing organization and lifecycle tests**

```java
@Test
void movingAnOrganizationBelowItsDescendantIsRejected() {
    assertThatThrownBy(() -> orgUnitService.move(salesId, new OrgUnitMoveCmd(teamAId)))
        .isInstanceOf(BusinessException.class)
        .hasMessage("不能移动到自身或下级组织");
}

@Test
void transferRetainsHistoryAndLeavesOnePrimaryPosition() {
    personnelService.changePrimaryPosition(userId, positionIn(productId));
    assertThat(userPositionMapper.countActivePrimary(userId)).isEqualTo(1);
    assertThat(userPositionMapper.listByUser(userId)).hasSize(2);
}
```

- [ ] **Step 2: Run lifecycle tests to verify they fail**

Run: `mvn -q -Dtest=OrgUnitServiceTest,PersonnelServiceTest test`

Expected: FAIL because organization services and historical position transitions do not exist.

- [ ] **Step 3: Implement organization tree operations**

Generate a stable organization `path` from parent path and ID after insert. Require unique `code`, enforce active parent and type, and validate moving nodes before writing any data. Implement `precheckDeactivate` to return blocking children, primary positions, active part-time positions, and project counts.

- [ ] **Step 4: Implement personnel creation, transfer, part-time work, and departure**

Validate a primary appointment has an active organization and manager from the same organization ancestry. End previous primary records with the day before the new `startDate`; allow multiple `PART_TIME` records; on disable, set user status to `DISABLED`, end all active positions, revoke sessions, and never delete project records.

- [ ] **Step 5: Record auditable before/after changes**

`OperationLogService.record` must receive sanitized maps that exclude `password`, `refreshToken`, `tokenHash`, and `authorization`. Call it for organization create/update/move/deactivate and personnel create/transfer/disable actions.

- [ ] **Step 6: Expose protected admin endpoints**

Use `@RequirePermission` with `admin:org:read/write/move/deactivate` and `admin:user:read/write/disable`. Return DTOs containing `displayName`, never `password` or raw session IDs.

- [ ] **Step 7: Run organization and lifecycle tests**

Run: `mvn -q -Dtest=OrgUnitServiceTest,PersonnelServiceTest test`

Expected: PASS for cycles, organization deletion prechecks, primary uniqueness, transfer history, part-time appointments, and disabling users.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/brad/pms/service src/main/java/com/brad/pms/controller src/main/java/com/brad/pms/dto src/test/java/com/brad/pms/service/OrgUnitServiceTest.java src/test/java/com/brad/pms/service/PersonnelServiceTest.java
git commit -m "feat: manage organization and personnel lifecycle"
```

### Task 6: Add roles, permissions, invitations, and password-reset administration

**Files:**
- Create: `src/main/java/com/brad/pms/service/RoleService.java`
- Create: `src/main/java/com/brad/pms/service/InvitationService.java`
- Create: `src/main/java/com/brad/pms/controller/AdminRoleController.java`
- Modify: `src/main/java/com/brad/pms/controller/AuthController.java`
- Create: `src/main/java/com/brad/pms/dto/request/RoleSaveCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/InviteUserCmd.java`
- Create: `src/main/java/com/brad/pms/dto/request/InviteActivateCmd.java`
- Create: `src/main/java/com/brad/pms/dto/response/RoleDTO.java`
- Create: `src/test/java/com/brad/pms/service/RoleServiceTest.java`
- Create: `src/test/java/com/brad/pms/service/InvitationServiceTest.java`

**Interfaces:**
- `RoleService.save(RoleSaveCmd)` validates every requested permission and custom organization scope before storing a role.
- `InvitationService.invite(InviteUserCmd)` creates a `PENDING_ACTIVATION` account and returns a one-time raw token only to the authorized caller.
- `InvitationService.activate(InviteActivateCmd)` consumes the token, sets BCrypt password, activates account, and invalidates prior invitation tokens.

- [ ] **Step 1: Write authorization administration tests**

```java
@Test
void lastSuperAdministratorCannotBeRemoved() {
    assertThatThrownBy(() -> roleService.removeUserRole(onlySuperAdminId, superAdminRoleId))
        .isInstanceOf(BusinessException.class);
}

@Test
void activationTokenCanOnlyBeUsedOnce() {
    invitationService.activate(new InviteActivateCmd(rawToken, "NewPassword1!"));
    assertThatThrownBy(() -> invitationService.activate(new InviteActivateCmd(rawToken, "OtherPassword1!")))
        .isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 2: Run the focused tests to verify they fail**

Run: `mvn -q -Dtest=RoleServiceTest,InvitationServiceTest test`

Expected: FAIL because role editing and invitation activation do not exist.

- [ ] **Step 3: Implement role CRUD with complete permission validation**

Reject edits to role code and `builtin` state for protected roles. Allow custom roles to select permission IDs, one data-scope type, and only existing active organization IDs for custom scope. Write role-permission and custom-scope changes in one transaction and log a sanitized before/after diff.

- [ ] **Step 4: Implement invitation and reset flows**

Generate a 32-byte random invitation/reset token, send it through the configured mail sender when SMTP exists, and return a copyable activation URL otherwise. Store only the SHA-256 token hash. Activation must validate expiry/status, set password, mark the user active, create an audit log, and revoke all prior sessions.

- [ ] **Step 5: Run role and invitation tests**

Run: `mvn -q -Dtest=RoleServiceTest,InvitationServiceTest test`

Expected: PASS for protected roles, custom organization scopes, invitation expiry, one-time activation, and session revocation.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/brad/pms/service/RoleService.java src/main/java/com/brad/pms/service/InvitationService.java src/main/java/com/brad/pms/controller/AdminRoleController.java src/main/java/com/brad/pms/controller/AuthController.java src/main/java/com/brad/pms/dto src/test/java/com/brad/pms/service/RoleServiceTest.java src/test/java/com/brad/pms/service/InvitationServiceTest.java
git commit -m "feat: manage roles and invited accounts"
```

### Task 7: Implement Excel/CSV organization and personnel import

**Files:**
- Create: `src/main/java/com/brad/pms/service/EnterpriseImportService.java`
- Create: `src/main/java/com/brad/pms/service/ImportTemplateService.java`
- Create: `src/main/java/com/brad/pms/entity/ImportJobDO.java`
- Create: `src/main/java/com/brad/pms/mapper/ImportJobMapper.java`
- Create: `src/main/java/com/brad/pms/controller/AdminImportController.java`
- Create: `src/main/java/com/brad/pms/dto/response/ImportPreviewDTO.java`
- Create: `src/main/java/com/brad/pms/dto/response/ImportRowErrorDTO.java`
- Create: `src/test/java/com/brad/pms/service/EnterpriseImportServiceTest.java`

**Interfaces:**
- `previewOrganizations(MultipartFile file)` and `previewUsers(MultipartFile file)` parse `.xlsx` and `.csv` and return rows plus validation errors without writing business data.
- `commit(UUID importJobId)` writes all rows in a single transaction only when preview has zero errors.
- Organization rows use `组织编码,组织名称,组织类型编码,父组织编码,负责人英文名,排序`; user rows use `中文名,英文名,邮箱,手机号,主组织编码,岗位编码,角色编码,直属上级英文名`.

- [ ] **Step 1: Write import validation tests**

```java
@Test
void previewRejectsCaseInsensitiveDuplicateEnglishNamesWithoutWrites() {
    ImportPreviewDTO preview = importService.previewUsers(csv("谢斌,Brad.Xie,...\n李强,brad.xie,..."));
    assertThat(preview.getErrors()).extracting(ImportRowErrorDTO::getField).contains("英文名");
    assertThat(userMapper.selectCount(null)).isEqualTo(existingUserCount);
}

@Test
void commitWritesAllRowsOnlyAfterCleanPreview() {
    UUID jobId = importService.previewOrganizations(validCsv()).getJobId();
    importService.commit(jobId);
    assertThat(orgUnitMapper.findByCode("SALES")).isNotNull();
}
```

- [ ] **Step 2: Run import tests to verify they fail**

Run: `mvn -q -Dtest=EnterpriseImportServiceTest test`

Expected: FAIL because no parsing, preview, or import job support exists.

- [ ] **Step 3: Implement template generation and parsers**

Use Apache POI for `.xlsx` and Commons CSV for `.csv`; generate empty templates with exact Chinese headers and one non-sensitive example row. Reject formulas, files larger than the existing 5MB limit, unsupported extensions, duplicate header names, and empty required cells.

- [ ] **Step 4: Implement all-or-nothing preview and commit**

Validate organization parent codes within the batch and against existing nodes, detect parent cycles, normalize English names, validate role/position references, and store serialized preview/error information in `sys_import_job`. `commit` must re-run critical uniqueness checks inside one `@Transactional` method, reject a stale/expired/failed job, write an operation log, and set job status `SUCCESS` only after all rows persist.

- [ ] **Step 5: Run import tests**

Run: `mvn -q -Dtest=EnterpriseImportServiceTest test`

Expected: PASS for CSV and XLSX, field errors, no partial writes, duplicate English names, parent cycles, and successful job audit records.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/brad/pms/service/EnterpriseImportService.java src/main/java/com/brad/pms/service/ImportTemplateService.java src/main/java/com/brad/pms/entity/ImportJobDO.java src/main/java/com/brad/pms/mapper/ImportJobMapper.java src/main/java/com/brad/pms/controller/AdminImportController.java src/main/java/com/brad/pms/dto src/test/java/com/brad/pms/service/EnterpriseImportServiceTest.java
git commit -m "feat: import enterprise organizations and users"
```

### Task 8: Apply organization data scope to projects and all user displays

**Files:**
- Modify: `src/main/java/com/brad/pms/entity/ProjectDO.java`
- Modify: `src/main/java/com/brad/pms/dto/request/ProjectCreateCmd.java`
- Modify: `src/main/java/com/brad/pms/dto/request/ProjectUpdateCmd.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectService.java`
- Modify: `src/main/java/com/brad/pms/service/ProjectPermissionService.java`
- Modify: `src/main/java/com/brad/pms/convertor/Convertors.java`
- Modify: `src/main/java/com/brad/pms/dto/response/UserDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/ProjectDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/ProjectMemberDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/ProjectTaskDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/ProjectCommentDTO.java`
- Create: `src/test/java/com/brad/pms/service/ProjectOrganizationScopeTest.java`

**Interfaces:**
- `ProjectDO.orgUnitId` is mandatory for new projects and defaults to the creator’s primary organization when omitted by an authorized creator.
- `ProjectService.page` returns the union of organization-scoped projects and projects in which the user is a member.
- All response DTOs expose `userDisplayName` or use the existing field name with a value in `中文名（英文名）` format.

- [ ] **Step 1: Write scope and display regression tests**

```java
@Test
void projectMemberCanReadCrossOrganizationProjectButCannotBrowseUnrelatedProjects() {
    assertThat(projectService.page(queryFor(memberUser))).extracting(ProjectDTO::getId)
        .contains(crossOrgMemberProjectId)
        .doesNotContain(unrelatedProjectId);
}

@Test
void projectMemberDisplayNameUsesChineseAndEnglishName() {
    assertThat(projectService.members(projectId).get(0).getDisplayName()).isEqualTo("谢斌（Brad.Xie）");
}
```

- [ ] **Step 2: Run scope tests to verify they fail**

Run: `mvn -q -Dtest=ProjectOrganizationScopeTest test`

Expected: FAIL because project queries ignore `org_unit_id` and DTOs use `nickname` alone.

- [ ] **Step 3: Add scope-aware project query composition**

Intersect organization-based project reads with `DataScopeResolver`, then union member project IDs without exposing duplicate rows. Keep current `ProjectPermissionPolicy` checks for write operations and lifecycle status; do not promote organization managers to project editors unless an explicit project role allows it.

- [ ] **Step 4: Replace nickname-only conversion paths**

Add one `Convertors.userDisplayName(UserDO)` helper and use it for project owner, creator, manager, members, task assignees, and comment authors. Keep the old `nickname` JSON field only during a documented compatibility release, with the same full display value.

- [ ] **Step 5: Run project scope tests and existing authorization tests**

Run: `mvn -q -Dtest=ProjectOrganizationScopeTest,ProjectPermissionPolicyTest test`

Expected: PASS for scoped browsing, cross-organization membership, project writes, and all personnel display values.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/brad/pms/entity/ProjectDO.java src/main/java/com/brad/pms/dto src/main/java/com/brad/pms/service/ProjectService.java src/main/java/com/brad/pms/service/ProjectPermissionService.java src/main/java/com/brad/pms/convertor/Convertors.java src/test/java/com/brad/pms/service/ProjectOrganizationScopeTest.java
git commit -m "feat: scope projects by organization and membership"
```

### Task 9: Upgrade frontend authentication and shared enterprise types

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/domain.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/types/api.d.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/api/auth.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/api/admin-user.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/api/admin-org.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/api/admin-role.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/plugins/http/index.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/store/user.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/views/login/index.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/auth/activate.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/auth/reset-password.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/store/user.test.mjs`

**Interfaces:**
- `User` includes `nameZh`, `username`, `displayName`, `status`, and effective permission codes.
- Access tokens live only in Pinia memory; refresh is cookie-backed and `http` retries exactly once after `POST /auth/refresh`.
- Login label and placeholder explicitly say “英文名”，but the request value remains unmodified because the backend normalizes it.

- [ ] **Step 1: Write failing store and request tests**

```javascript
test('user display name is used after login', async () => {
  await store.login('Brad.Xie', 'CorrectPassword1!')
  assert.equal(store.user.displayName, '谢斌（Brad.Xie）')
  assert.equal(localStorage.getItem('pms_token'), null)
})
```

- [ ] **Step 2: Run the frontend test to verify it fails**

Run: `node --test src/store/user.test.mjs`

Expected: FAIL because the store persists `pms_token` in local storage and lacks display-name fields.

- [ ] **Step 3: Implement session-aware HTTP handling**

Remove `TOKEN_KEY` persistence. Store access token in Pinia memory, use `withCredentials: true`, queue concurrent 401 responses behind one refresh request, retry once, then call `logout()` and route to `/login` on refresh failure. Do not retry login, activation, password reset, or upload requests automatically.

- [ ] **Step 4: Implement login, activation, and reset screens**

Change the login form to “英文名” and “密码”, remove the default `admin/admin123` values, add a link for invitation activation and password reset, and show backend validation errors. Activation requires a token, Chinese display name preview, password/confirmation, then routes to login.

- [ ] **Step 5: Run frontend type and store tests**

Run: `node --test src/store/user.test.mjs && pnpm typecheck`

Expected: PASS with no local-storage access token and correct bilingual display name.

- [ ] **Step 6: Commit**

```bash
git -C /Users/fs/Desktop/Project/pms-front add src/types src/api src/plugins/http/index.ts src/store/user.ts src/store/user.test.mjs src/views/login/index.vue src/views/auth
git -C /Users/fs/Desktop/Project/pms-front commit -m "feat: add enterprise authentication client"
```

### Task 10: Build configuration navigation and administration pages

**Files:**
- Modify: `/Users/fs/Desktop/Project/pms-front/src/styles/index.css`
- Modify: `/Users/fs/Desktop/Project/pms-front/docs/frontend-design-system.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/layout/Index.vue`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/router/index.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/users/index.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/users/UserDrawer.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/org/index.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/org/OrgCanvas.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/roles/index.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/audit/index.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/views/admin/import/index.vue`
- Create: `/Users/fs/Desktop/Project/pms-front/src/router/admin-guard.ts`
- Create: `/Users/fs/Desktop/Project/pms-front/src/router/admin-guard.test.mjs`
- Modify: `/Users/fs/Desktop/Project/pms-front/design-qa.md`

**Interfaces:**
- `/admin/users`, `/admin/org`, `/admin/roles`, `/admin/audit`, and `/admin/import` require matching frontend permission codes and backend API authorization.
- `canAccessAdminRoute(user, permission)` returns a boolean for route/menu experience only; API denial remains authoritative.
- The sidebar contains `工作台`, `项目管理`, and expandable `配置管理`, with 14px navigation text and Ant Design Vue icons.

- [ ] **Step 1: Write frontend route permission tests**

```javascript
test('organization manager can enter org page but not role page', () => {
  const user = { permissionCodes: ['admin:org:read'] }
  assert.equal(canAccessAdminRoute(user, 'admin:org:read'), true)
  assert.equal(canAccessAdminRoute(user, 'admin:role:read'), false)
})
```

- [ ] **Step 2: Run the guard test to verify it fails**

Run: `node --test src/router/admin-guard.test.mjs`

Expected: FAIL because admin routes and permission-code checks do not exist.

- [ ] **Step 3: Add configuration navigation using existing PMS tokens**

Add `--pms-font-size-nav: 14px` to `styles/index.css` and document it in `docs/frontend-design-system.md`. Extend `Index.vue` using `a-sub-menu` and icons from `@ant-design/icons-vue`; do not add custom SVG or a second visual theme.

```vue
<a-sub-menu v-if="canAccessConfig" key="configuration">
  <template #title><SettingOutlined /><span>配置管理</span></template>
  <a-menu-item v-if="can('admin:user:read')" key="admin-users">人员与权限</a-menu-item>
  <a-menu-item v-if="can('admin:org:read')" key="admin-org">组织架构</a-menu-item>
</a-sub-menu>
```

- [ ] **Step 4: Implement the personnel and role pages**

Build the left organization filter plus paginated personnel table. The drawer edits account status, primary/part-time positions, role grants, and data scope. Build role permission trees from backend metadata and disable protected role deletion in the UI while still handling backend `403` errors.

- [ ] **Step 5: Implement organization canvas, import, and audit pages**

Render organization nodes from the backend tree, use a selected-node right property panel, and require a modal confirmation before move/deactivate. The import page exposes template download, upload, preview error table, and explicit commit. The audit page filters operation logs and shows sanitized before/after differences.

- [ ] **Step 6: Run route tests, typecheck, and production build**

Run: `node --test src/router/admin-guard.test.mjs && pnpm typecheck && pnpm build`

Expected: PASS; users without a permission cannot reach the corresponding route, and all new pages compile.

- [ ] **Step 7: Perform visual regression against the approved prototype**

Compare desktop and 360px screens against the approved mockup: white top bar, white sidebar, 14px configuration navigation, soft table headers, PMS primary color, and center-aligned organization connectors. Record intentional differences and verification date in `design-qa.md`.

- [ ] **Step 8: Commit**

```bash
git -C /Users/fs/Desktop/Project/pms-front add src/styles/index.css docs/frontend-design-system.md src/layout/Index.vue src/router src/views/admin design-qa.md
git -C /Users/fs/Desktop/Project/pms-front commit -m "feat: add enterprise configuration management pages"
```

### Task 11: End-to-end migration verification, documentation, and release checks

**Files:**
- Modify: `README.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/README.md`
- Create: `docs/operations/enterprise-upgrade-runbook.md`
- Create: `scripts/enterprise-preflight.sh`
- Create: `scripts/verify-enterprise-migration.sh`
- Modify: `src/test/java/com/brad/pms/config/OceanbaseConfigurationTest.java`
- Create: `src/test/java/com/brad/pms/security/EnterpriseRegressionTest.java`

**Interfaces:**
- `scripts/enterprise-preflight.sh` is read-only and exits non-zero for username normalization conflicts, missing user references, or invalid organization paths.
- `scripts/verify-enterprise-migration.sh` verifies required tables, indexes, roots, protected roles, and project organization assignments without writing or deleting data.

- [ ] **Step 1: Write the cross-feature regression test**

```java
@Test
void disabledProjectMemberCannotUseExistingSessionButHistoryRemainsVisibleToAdministrator() {
    String accessToken = login("Terry.Li", "CorrectPassword1!");
    personnelService.disable(terryId, new UserDisableCmd("离职"));
    assertThat(requestWith(accessToken, "/projects/" + projectId)).hasStatus(401);
    assertThat(operationLogService.page(adminQuery()).getList())
        .extracting(OperationLogDTO::getAction).contains("USER_DISABLED");
}
```

- [ ] **Step 2: Run the test to verify the integrated behavior before release hardening**

Run: `mvn -q -Dtest=EnterpriseRegressionTest test`

Expected: PASS only after Tasks 1–10 are complete; otherwise use the failure to finish the missing contract before proceeding.

- [ ] **Step 3: Write safe operational scripts and runbook**

Document exact backup, preflight, migration, post-check, application startup, and rollback steps. Scripts must use `SELECT`/metadata checks only, never run destructive SQL, and must not print database credentials or token values.

- [ ] **Step 4: Verify all supported database profiles**

Run: `mvn -q test`

Run: `mvn -q -Dspring.profiles.active=mysql -Dtest=EnterpriseSchemaMigrationTest,EnterpriseRegressionTest test`

Run: `mvn -q -Dspring.profiles.active=oceanbase -Dtest=OceanbaseConfigurationTest,EnterpriseSchemaMigrationTest test`

Expected: PASS with database credentials supplied only through the documented runtime environment.

- [ ] **Step 5: Run frontend release checks**

Run: `pnpm --dir /Users/fs/Desktop/Project/pms-front typecheck && pnpm --dir /Users/fs/Desktop/Project/pms-front build`

Expected: PASS with no TypeScript error and a production bundle.

- [ ] **Step 6: Update deployment documentation**

Document bootstrap administrator environment variables, SMTP optional behavior, import templates, session security, database migration commands, account recovery, and the rule that English login names are case-insensitive.

- [ ] **Step 7: Commit**

```bash
git add README.md docs/operations/enterprise-upgrade-runbook.md scripts/enterprise-preflight.sh scripts/verify-enterprise-migration.sh src/test/java/com/brad/pms/config/OceanbaseConfigurationTest.java src/test/java/com/brad/pms/security/EnterpriseRegressionTest.java
git commit -m "docs: add enterprise upgrade runbook"
git -C /Users/fs/Desktop/Project/pms-front add README.md design-qa.md
git -C /Users/fs/Desktop/Project/pms-front commit -m "docs: document enterprise configuration release"
```

## Plan Self-Review

- Spec coverage: Tasks 1–2 implement schema and legacy migration; Tasks 3 and 6 implement identity, invitation, reset, sessions, and login security; Tasks 4–5 implement RBAC, scope, organizations, positions, lifecycle, and audit; Task 7 implements Excel/CSV import; Task 8 protects existing project behavior; Tasks 9–10 implement the frontend and approved navigation; Task 11 provides migration, documentation, and release verification.
- No placeholders: every task names concrete files, contracts, commands, and expected behavior; no task depends on an unnamed later component.
- Type consistency: `usernameNormalized`, `nameZh`, `displayName`, `OrgUnitService`, `PersonnelService`, `AuthorizationService`, `DataScopeResolver`, and `EnterpriseImportService` use the same names across dependent tasks.
