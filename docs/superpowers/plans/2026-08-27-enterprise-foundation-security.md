# 企业级基础能力一期实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** 为单企业 MySQL 部署补齐登录审计、认证安全、显式 CORS 和上线前数据库预检能力。

**Architecture:** 认证服务在同一事务中更新用户安全状态并写入登录日志，使用 `noRollbackFor` 确保认证失败也能留下安全记录。生产配置通过启动时校验拒绝弱 JWT 密钥，通过显式来源列表配置 CORS；MySQL 的 schema 和数据完整性由只读预检脚本在部署前验证。

**Tech Stack:** Spring Boot 2.7、Java 17、MyBatis-Plus、Flyway、MySQL 8、JUnit 5、Bash。

**Spec:** `docs/superpowers/specs/2026-08-27-enterprise-foundation-security-design.md`

## Global Constraints

- 生产数据库固定使用 MySQL 8；不把内存数据库配置用于生产运行。
- 单企业本地部署，不增加 `tenant_id`。
- 不记录密码、JWT、刷新令牌或密码重置令牌。
- 预检脚本只读，不执行 DROP、TRUNCATE、覆盖或自动回滚。
- 用户界面继续使用中文文案；英文名仅作为登录账号和辅助展示。

---

### Task 1: 登录审计和失败锁定持久化

**Files:**
- Create: `src/main/java/com/brad/pms/entity/LoginLogDO.java`
- Create: `src/main/java/com/brad/pms/mapper/LoginLogMapper.java`
- Modify: `src/main/java/com/brad/pms/service/AuthService.java`
- Modify: `src/test/java/com/brad/pms/service/AuthServiceTest.java`

**Interfaces:**
- `LoginLogMapper extends BaseMapper<LoginLogDO>` maps to existing `sys_login_log`.
- `AuthService.login(LoginRequest, String, String)` records `SUCCESS` or `FAILURE` with a safe reason and no credentials.

- [ ] **Step 1: Write failing tests**

Add tests that mock `LoginLogMapper` and assert an invalid password increments the user failure count and inserts a `FAILURE` log, a successful login resets the count and inserts `SUCCESS`, and an unknown username still inserts a log with a null user ID.

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `mvn -q -Dtest=AuthServiceTest test`

Expected: compilation/test failure because `LoginLogMapper` is not yet part of `AuthService`.

- [ ] **Step 3: Implement the minimal audit path**

Create the entity fields `id`, `userId`, `loginName`, `result`, `reason`, `ip`, `userAgent`, and `createdAt`. Inject the mapper into `AuthService`, annotate login with `@Transactional(noRollbackFor = BusinessException.class)`, and add a private `recordLoginAttempt` method that truncates `loginName` to 80 and `userAgent` to 500 before insert. Record all authentication exits without persisting password or token values.

- [ ] **Step 4: Run focused and full backend tests**

Run: `mvn -q -Dtest=AuthServiceTest test` and then `mvn -q test`.

Expected: all tests pass and failed login state/log rows remain persisted after the expected 401 exception.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/brad/pms/entity/LoginLogDO.java src/main/java/com/brad/pms/mapper/LoginLogMapper.java src/main/java/com/brad/pms/service/AuthService.java src/test/java/com/brad/pms/service/AuthServiceTest.java
git commit -m "feat: persist authentication audit logs"
```

### Task 2: 生产认证配置和 CORS 安全

**Files:**
- Modify: `src/main/java/com/brad/pms/security/JwtTokenProvider.java`
- Modify: `src/main/java/com/brad/pms/config/WebConfig.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/brad/pms/service/PasswordResetService.java`
- Create: `src/test/java/com/brad/pms/security/JwtTokenProviderTest.java`

**Interfaces:**
- `JwtTokenProvider` rejects blank or shorter-than-32-byte secrets with an actionable `IllegalStateException`.
- `WebConfig` reads `pms.security.cors.allowed-origins` as a comma-separated explicit list and never combines credentials with `*`.

- [ ] **Step 1: Write failing tests**

Add `JwtTokenProviderTest` cases for a 31-byte secret failing and a 32-byte secret creating/parsing a token. Add a source/config assertion that the production default password-reset exposure is `false` and the CORS property exists.

- [ ] **Step 2: Run tests and verify the security tests fail**

Run: `mvn -q -Dtest=JwtTokenProviderTest test`.

Expected: the short-secret test fails because the constructor currently accepts it.

- [ ] **Step 3: Implement secure defaults**

Validate the JWT secret before calling `Keys.hmacShaKeyFor`; change `application.yml` to require `${PMS_JWT_SECRET:}` and set `password-reset-expose-token` default to `false`; make `PasswordResetService` use the same false default. Add `pms.security.cors.allowed-origins` with local development origins and update `WebConfig` to split, trim, reject empty lists, and pass explicit origins to `allowedOriginPatterns`.

- [ ] **Step 4: Run tests and build**

Run: `mvn -q -Dtest=JwtTokenProviderTest test`, `mvn -q test`, and in the frontend repo `pnpm build`.

Expected: all backend tests and the frontend build pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/brad/pms/security/JwtTokenProvider.java src/main/java/com/brad/pms/config/WebConfig.java src/main/resources/application.yml src/main/java/com/brad/pms/service/PasswordResetService.java src/test/java/com/brad/pms/security/JwtTokenProviderTest.java
git commit -m "fix: harden production authentication defaults"
```

### Task 3: MySQL 生产预检和文档验收

**Files:**
- Modify: `scripts/enterprise-preflight.sh`
- Modify: `scripts/verify-enterprise-migration.sh`
- Create: `src/main/resources/db/migration/V4__authentication_audit_indexes.sql`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Create: `src/test/java/com/brad/pms/config/EnterprisePreflightScriptTest.java`

**Interfaces:**
- Scripts continue to read `PMS_DB_*` first, then `MYSQL_*`, and never echo the password.
- Preflight checks required enterprise tables/indexes, valid user login names, exactly one active root, and projects with organization assignment.

- [ ] **Step 1: Write failing script contract tests**

Add a test that reads both scripts and asserts they use `set -euo pipefail`, `MYSQL_PWD` for connection, no destructive SQL (`DROP DATABASE`, `TRUNCATE`), and include checks for `sys_login_log`, `sys_auth_session`, `uk_user_username_normalized`, and active root organization.

- [ ] **Step 2: Run the contract test and verify it fails**

Run: `mvn -q -Dtest=EnterprisePreflightScriptTest test`.

Expected: failure because the preflight script does not yet check every required security table/index.

- [ ] **Step 3: Add read-only production checks**

Extend `enterprise-preflight.sh` with explicit required table/index checks and security data checks; extend `verify-enterprise-migration.sh` with the same post-migration assertions. Document the exact commands and expected environment variables in the runbook, including the required JWT secret and explicit CORS origins.

- [ ] **Step 4: Run script contract, backend, and shell syntax checks**

Run: `mvn -q -Dtest=EnterprisePreflightScriptTest test`, `mvn -q test`, `bash -n scripts/enterprise-preflight.sh scripts/verify-enterprise-migration.sh`.

Expected: all tests pass and both scripts parse successfully without connecting to a database.

- [ ] **Step 5: Commit**

```bash
git add scripts/enterprise-preflight.sh scripts/verify-enterprise-migration.sh docs/operations/enterprise-upgrade-runbook.md src/test/java/com/brad/pms/config/EnterprisePreflightScriptTest.java
git commit -m "chore: strengthen MySQL deployment preflight"
```

### Task 4: Final review and delivery

**Files:**
- Modify only files from Tasks 1–3 if review finds a defect.

- [ ] **Step 1: Run full verification**

Run backend `mvn -q test`, frontend `pnpm typecheck`, frontend `pnpm build`, and `git diff --check` in both repositories.

- [ ] **Step 2: Review security-sensitive changes**

Confirm no password/token value appears in login logs, no wildcard CORS is combined with credentials, and no production default secret remains.

- [ ] **Step 3: Commit any review fix**

Use a focused commit message describing the defect and verification.
