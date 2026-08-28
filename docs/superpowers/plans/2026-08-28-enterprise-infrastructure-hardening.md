# 企业级基础设施生产化收口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在单企业私有化部署模式下，补齐 OceanBase 权限隔离、可恢复升级、安全运营、容器运行、可观测性和开源交付能力，使 PMS 可以长期稳定运行。

**Architecture:** 应用运行时只使用 `pms_app`，数据库初始化和版本升级只使用短时的 `pms_migrator`，DBA 保留 `root@sys` 作为人工维护账号。OceanBase 是唯一运行时数据库，H2 仅保留在测试配置；所有升级先预检、备份、加锁，再按版本执行并进行只读校验。前端继续通过 Nginx 访问后端，不改动现有业务逻辑和页面信息架构。

**Tech Stack:** Java 17、Spring Boot 2.7、MyBatis-Plus、OceanBase MySQL 模式、MySQL/obclient、Vue 3、TypeScript、pnpm、Nginx、Docker Compose、JUnit 5、Node test runner、Playwright、Trivy、Syft。

**Spec:** `docs/superpowers/specs/2026-08-27-enterprise-foundation-security-design.md`、`docs/superpowers/specs/2026-08-26-oceanbase-migration-design.md`、`docs/business-specification.md`

## Global Constraints

- 单企业私有化部署，不引入多租户字段、租户切换或跨企业数据逻辑。
- OceanBase 是生产和本地部署运行时数据库；H2 只允许出现在测试资源和测试 profile。
- `pms_app` 不得拥有建表、改表结构和删除表权限；`pms_migrator` 只在升级任务中使用；应用不得使用 `root@sys`。
- 数据库升级禁止 `DROP DATABASE`、`TRUNCATE` 和覆盖非空业务数据；备份、校验和失败现场必须保留。
- 密码、JWT、数据库连接串和通知服务密钥只能通过环境变量或密钥管理系统提供，不进入 Git、镜像层和普通日志。
- 后端授权是唯一安全边界；所有敏感操作必须有操作审计和 `X-Request-Id`。
- 现有业务逻辑、组织负责人与员工主归属两套独立关系保持不变。
- 每个任务先写失败测试或验收脚本，再实现，再运行回归和自我 Review，并单独提交。

---

### Task 0: 基线盘点与文档状态对齐

**Files:**
- Create: `docs/operations/infrastructure-status.md`
- Modify: `docs/superpowers/plans/2026-08-27-enterprise-completion-roadmap.md`
- Modify: `README.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/README.md`
- Test: `git diff --check`、现有后端和前端验证命令

**Interfaces:**
- Consumes: 当前 OceanBase `brad_pms` 实例、现有 V1–V5 脚本、Compose 和 CI 配置。
- Produces: 一份标记“已完成/待完成/风险”的基础设施清单，后续任务以此为唯一状态来源。

- [x] **Step 1: 记录当前可重复的基线**

  在 `infrastructure-status.md` 固定记录以下结果：OceanBase V5 已执行、后端 71 项测试通过、前端 77 项测试通过、`pnpm typecheck` 和 `pnpm build` 通过、两个仓库工作区干净；同时列出当前缺口：应用仍默认使用 root、无备份恢复自动化、无 OpenAPI、无漏洞扫描、上传目录无持久卷、错误 HTTP 状态不统一。

- [x] **Step 2: 对齐路线图勾选状态**

  将已完成的历史任务与本计划的待执行基础设施任务分开，保留历史提交说明，避免把“业务功能已完成”误认为“生产基础设施已完成”。

- [x] **Step 3: 运行基线验证**

  ```bash
  cd /Users/fs/Desktop/Project/pms-backend
  mvn -q test
  bash -n scripts/*.sh docker/*.sh
  git diff --check
  cd /Users/fs/Desktop/Project/pms-front
  pnpm typecheck
  pnpm build
  git diff --check
  ```

  Expected: 后端测试、前端类型检查和构建全部成功，两个仓库无格式错误。

- [x] **Step 4: Commit**

  ```bash
  git add docs/operations/infrastructure-status.md docs/superpowers/plans/2026-08-27-enterprise-completion-roadmap.md README.md
  git commit -m "docs: establish infrastructure readiness baseline"
  ```

**Review gate:** 清单中的每个“已完成”都必须有命令、测试或运行记录作为证据；未验证的项目不得标记完成。

---

### Task 1: 创建数据库运行账号并切换应用到最小权限

**Files:**
- Create: `docker/oceanbase-accounts-init.sh`
- Create: `scripts/check-oceanbase-privileges.sh`
- Create: `docs/operations/database-accounts.md`
- Modify: `docker-compose.example.yml`
- Modify: `docker/oceanbase-init.sh`
- Modify: `src/main/resources/application-oceanbase.yml`
- Modify: `.env.oceanbase.example`
- Modify: `src/test/java/com/brad/pms/config/OceanbaseConfigurationTest.java`
- Test: `src/test/java/com/brad/pms/config/OceanbaseConfigurationTest.java`

**Interfaces:**
- Consumes: `OCEANBASE_ROOT_PASSWORD`、`PMS_APP_PASSWORD`、`PMS_MIGRATOR_PASSWORD`，以及已有 `brad_pms` 数据库。
- Produces: `pms_app`（运行时 DML 权限）和 `pms_migrator`（迁移 DDL 权限）；后端配置强制要求 `OCEANBASE_USER` 和 `OCEANBASE_PASSWORD`，不再默认回退到 root。

- [x] **Step 1: 写配置失败测试**

  为以下场景补充测试：OceanBase profile 缺少 `OCEANBASE_USER` 失败；缺少 `OCEANBASE_PASSWORD` 失败；配置 `root@sys` 作为应用账号时启动校验失败；配置 `pms_app` 时通过。

- [x] **Step 2: 编写账号初始化脚本**

  `oceanbase-accounts-init.sh` 只允许用 `root@sys` 首次执行，创建或更新两个账号：`pms_app` 对 `brad_pms.*` 授予 `SELECT, INSERT, UPDATE, DELETE`；`pms_migrator` 授予迁移需要的 DML 和 `CREATE, ALTER, INDEX, REFERENCES, CREATE VIEW, TRIGGER` 权限，不授予 `DROP`。脚本使用 `MYSQL_PWD` 传递密码，执行前拒绝空的生产密码，并且打印账号名和权限摘要但不打印密码。

- [x] **Step 3: 拆分 Compose 初始化职责**

  增加一次性 `accounts-init` 服务，用 root 创建数据库和两个账号；`schema-init` 改用 `pms_migrator` 执行 schema 与 V1–V5；`backend` 改用 `pms_app`。现有数据非空时禁止初始化脚本覆盖，账号初始化必须幂等。

- [x] **Step 4: 编写权限冒烟脚本**

  `check-oceanbase-privileges.sh` 使用应用账号验证 `SELECT/INSERT/UPDATE/DELETE` 可用、`CREATE TABLE` 被拒绝；使用迁移账号验证 `CREATE TABLE`、`ALTER TABLE` 可用。由于当前 OceanBase 不支持 MySQL 临时表，脚本使用带时间戳的探针表，并用 root 账号在退出时清理，不授予 `pms_migrator` 删除表权限。

- [x] **Step 5: 运行并 Review**

  ```bash
  OCEANBASE_PASSWORD="$OCEANBASE_ROOT_PASSWORD" \
    docker compose -f docker-compose.example.yml config --quiet
  bash scripts/check-oceanbase-privileges.sh
  mvn -q -Dtest=OceanbaseConfigurationTest test
  ```

  Review 应确认后端容器环境中不存在 root 凭据，账号初始化重复执行不会修改业务数据，失败时不会输出密码。

- [x] **Step 6: Commit**

  ```bash
  git add docker docker-compose.example.yml src/main/resources/application-oceanbase.yml .env.oceanbase.example docs/operations/database-accounts.md scripts/check-oceanbase-privileges.sh src/test/java/com/brad/pms/config/OceanbaseConfigurationTest.java
  git commit -m "feat: isolate OceanBase runtime and migration accounts"
  ```

**验收：** 应用只能使用 `pms_app`；迁移任务只能使用 `pms_migrator`；root 仅出现在账号初始化或 DBA 文档中。

---

### Task 2: OceanBase 版本化升级、备份恢复与迁移锁

**Files:**
- Create: `scripts/oceanbase-upgrade.sh`
- Create: `scripts/backup-oceanbase.sh`
- Create: `scripts/restore-oceanbase.sh`
- Create: `scripts/verify-backup.sh`
- Create: `docs/operations/oceanbase-backup-restore.md`
- Modify: `scripts/enterprise-preflight.sh`
- Modify: `scripts/verify-enterprise-migration.sh`
- Modify: `docker/oceanbase-init.sh`
- Create: `src/test/java/com/brad/pms/migration/OceanbaseUpgradeScriptTest.java`
- Test: `src/test/java/com/brad/pms/migration/OceanbaseSqlImporterTest.java`

**Interfaces:**
- Consumes: 迁移目录 `src/main/resources/db/migration/V*.sql`、`pms_migrator` 凭据、备份目录和 OceanBase MySQL/obclient 客户端。
- Produces: 可重复执行的升级命令、带校验和的备份文件、迁移锁和清晰的版本状态。

- [x] **Step 1: 为升级状态写测试**

  覆盖目标库不存在、目标库为空、目标库已有 V1–V5、重复执行同一版本、两个升级进程并发、脚本中途失败以及 checksum 不匹配六种场景；测试不得执行 `DROP DATABASE` 或 `TRUNCATE`。

- [x] **Step 2: 统一客户端发现和参数传递**

  升级、预检、备份和恢复脚本按顺序使用宿主机 `obclient`/`mysql`，找不到时使用已固定版本的 `mysql:8.4` 工具容器；密码通过 `MYSQL_PWD` 传入，命令输出统一隐藏连接参数中的密码。

- [x] **Step 3: 实现备份与恢复**

  备份脚本输出 `brad_pms-<UTC时间>-<schema版本>.sql.zst`、SHA-256 文件和元数据（数据库版本、表行数、脚本版本）；恢复脚本默认只允许恢复到显式指定的空库或临时库，恢复前执行确认参数 `--allow-empty-target`，恢复后运行 `verify-backup.sh`。

- [x] **Step 4: 实现升级锁和版本校验**

  升级脚本获取数据库命名锁 `pms-schema-upgrade`，读取 `flyway_schema_history` 或兼容的版本表，校验每个脚本的 SHA-256；仅执行缺失版本，已成功版本不得重复执行，失败时保留日志并输出下一步恢复命令。

- [x] **Step 5: 实现真实 OceanBase 冒烟**

  在 `docker compose` 中以 `pms_migrator` 执行 V1–V5，随后运行 preflight、行数、外键、唯一索引、软删除列和根组织检查；同一升级命令连续运行两次，第二次必须报告“无待执行版本”。

- [x] **Step 6: Commit**

  ```bash
  mvn -q -Dtest=OceanbaseSqlImporterTest,OceanbaseUpgradeScriptTest test
  bash -n scripts/*.sh docker/*.sh
  git add scripts docs/operations/oceanbase-backup-restore.md docker/oceanbase-init.sh src/test/java/com/brad/pms/migration
  git commit -m "feat: add repeatable OceanBase backup and upgrade workflow"
  ```

**验收：** 能从备份恢复到临时库；升级失败有可操作的恢复路径；升级脚本重复执行不会重复建表、重复索引或产生重复数据。

---

### Task 3: 统一安全边界、错误响应和请求链路

**Files:**
- Modify: `src/main/java/com/brad/pms/common/exception/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/brad/pms/common/response/ResponseResult.java`
- Modify: `src/main/java/com/brad/pms/security/AuthInterceptor.java`
- Modify: `src/main/java/com/brad/pms/config/WebConfig.java`
- Create: `src/main/java/com/brad/pms/config/ForwardedHeaderConfig.java`
- Create: `src/main/java/com/brad/pms/security/RequestRateLimitInterceptor.java`
- Modify: `src/main/resources/application.yml`
- Modify: `/Users/fs/Desktop/Project/pms-front/src/plugins/http/index.ts`
- Test: `src/test/java/com/brad/pms/common/exception/GlobalExceptionHandlerTest.java`
- Test: `src/test/java/com/brad/pms/security/AuthInterceptorTest.java`
- Test: `/Users/fs/Desktop/Project/pms-front/src/plugins/http/http.test.mjs`

**Interfaces:**
- Consumes: `BusinessException`、认证拦截器、`X-Request-Id`、前端 Axios 响应拦截器。
- Produces: 统一错误合同：HTTP 401/403/400/409/422/500 与响应体 `code/msg/data/requestId` 一致；登录、刷新、重置、邀请和上传接口具备限流；TLS 终止代理后的刷新 Cookie 正确设置 `Secure`。

- [x] **Step 1: 写错误合同测试**

  断言认证失败返回 HTTP 401、权限不足返回 403、参数校验返回 400、冲突返回 409、业务校验失败返回 422、未知异常返回 500；每个响应都包含 `requestId`，且不包含 SQL、密码、JWT 或令牌哈希。

- [x] **Step 2: 修复后端异常处理**

  在 `GlobalExceptionHandler` 使用 `ResponseEntity<ResponseResult<?>>` 设置真实 HTTP 状态；保留现有业务码兼容前端，同时把状态映射集中到一个方法中，避免控制器自行重复实现。

- [x] **Step 3: 修复前端错误处理**

  Axios 只在 HTTP 401 且刷新失败时跳转登录页；业务错误直接展示后端 `msg`，不再因为 `error.response` 为空而被覆盖成“网络异常”；保留 redirect 参数并防止同一错误重复弹窗。

- [x] **Step 4: 加入代理和限流配置**

  启用 Spring forwarded headers 支持，让 TLS 终止代理后的 Cookie 正确带 `Secure`；对登录、refresh、密码重置、邀请和上传按 IP + 账号设置令牌桶限流，超过阈值返回 429，并把限流结果写入安全日志。

- [x] **Step 5: 运行 Review**

  ```bash
  mvn -q -Dtest=GlobalExceptionHandlerTest,AuthInterceptorTest test
  cd /Users/fs/Desktop/Project/pms-front
  node --test src/*.test.mjs src/**/*.test.mjs
  pnpm typecheck
  ```

  Review 401/403、刷新 Cookie、连续登录失败和代理转发四条链路，确保前端不再重复提示或错误跳转。

- [x] **Step 6: Commit**

  ```bash
  git add src/main/java src/main/resources/application.yml src/test/java /Users/fs/Desktop/Project/pms-front/src/plugins/http /Users/fs/Desktop/Project/pms-front/src/plugins/http/http.test.mjs
  git commit -m "feat: standardize security errors and request controls"
  ```

**验收：** 浏览器、脚本调用和后端集成测试看到相同的 HTTP 状态和错误字段；限流不能绕过账号锁定，也不能泄露账号是否存在。

---

### Task 4: 通知服务和上传文件生产化

**Files:**
- Create: `src/main/java/com/brad/pms/notification/MailPasswordResetNotifier.java`
- Create: `src/main/java/com/brad/pms/notification/MailInvitationNotifier.java`
- Create: `src/main/java/com/brad/pms/storage/FileStorageService.java`
- Create: `src/main/java/com/brad/pms/storage/LocalFileStorageService.java`
- Modify: `src/main/java/com/brad/pms/controller/ProjectImageController.java`
- Modify: `src/main/resources/application.yml`
- Modify: `docker-compose.example.yml`
- Create: `docs/operations/notification-and-upload.md`
- Test: `src/test/java/com/brad/pms/storage/LocalFileStorageServiceTest.java`
- Test: `src/test/java/com/brad/pms/controller/ProjectImageControllerTest.java`

**Interfaces:**
- Consumes: `PMS_UPLOAD_DIR`、通知 SMTP/飞书/企业微信配置、项目图片上传接口。
- Produces: 可替换的持久化存储接口、文件魔数校验、文件名随机化、配额和通知器启动检查。

- [x] **Step 1: 写上传安全测试**

  覆盖伪造 MIME、双扩展名、路径穿越、超 5MB、超过用户配额、空文件和正常 PNG/JPEG；断言原始文件名不会成为存储路径，响应不返回服务器真实路径。

- [x] **Step 2: 抽象并实现文件存储**

  使用随机 UUID 文件名和白名单扩展名，读取文件魔数确认类型；本地模式写入 `${PMS_UPLOAD_DIR}`，禁止写入应用工作目录；为 Compose 增加独立 `pms-uploads` 持久卷，并提供后续对象存储实现接口。

- [x] **Step 3: 接入通知器**

  提供 SMTP 实现并保留飞书/企业微信适配接口；生产配置开启邀请或找回密码时，如果没有通知器 bean，应用启动检查必须明确失败；开发环境只有显式 `PMS_PASSWORD_RESET_EXPOSE_TOKEN=true`（邀请流程对应 `PMS_INVITATION_EXPOSE_TOKEN=true`）才会返回令牌。

- [x] **Step 4: 运行测试和安全 Review**

  ```bash
  mvn -q -Dtest=LocalFileStorageServiceTest,ProjectImageControllerTest,PasswordResetServiceTest,InvitationServiceTest test
  ```

  Review 文件权限、容器重建后文件可见性、邮件正文脱敏和日志中无令牌。

- [x] **Step 5: Commit**

  ```bash
  git add src/main/java src/main/resources/application.yml docker-compose.example.yml docs/operations/notification-and-upload.md src/test/java
  git commit -m "feat: harden notifications and persistent uploads"
  ```

**验收：** 容器重建不会丢失已上传文件；未配置生产通知器时不会静默生成无法送达的邀请或重置令牌。

---

### Task 5: 容器与 Compose 运行时加固

**Files:**
- Modify: `Dockerfile`
- Modify: `/Users/fs/Desktop/Project/pms-front/Dockerfile`
- Modify: `docker-compose.example.yml`
- Modify: `/Users/fs/Desktop/Project/pms-front/nginx.conf`
- Create: `docker/healthcheck-backend.sh`
- Create: `docker/healthcheck-frontend.sh`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Test: `docker compose -f docker-compose.example.yml config --quiet`

**Interfaces:**
- Consumes: 后端 `/api/health`、前端 Nginx、OceanBase 健康检查和现有 Compose 服务。
- Produces: 可观察、可自动恢复、默认最小权限的服务栈。

- [ ] **Step 1: 固定镜像和构建上下文**

  为 OceanBase、MySQL 工具、Java runtime、Node build 和 Nginx 记录可更新的版本清单，并在发布配置中使用 digest；构建阶段不得复制 `.env`、`.git`、`target`、`dist` 和上传文件。

- [ ] **Step 2: 增加健康检查和恢复策略**

  为 backend、frontend、schema-init 增加 healthcheck 或成功条件；backend/frontend 设置合理的 `restart: unless-stopped`，schema-init 保持 `restart: "no"`；backend 只有在 schema-init 成功并且 `/api/health` 为 UP 后才对外服务。

- [ ] **Step 3: 加固容器权限和资源**

  前端 Nginx 使用非 root 用户和可写临时目录；Compose 设置 `cap_drop: [ALL]`、`no-new-privileges`、CPU/内存上限和只读根文件系统（上传卷和 Nginx 临时目录显式可写）。

- [ ] **Step 4: 增加边缘安全头和代理限制**

  Nginx 增加 `X-Content-Type-Options`、`Referrer-Policy`、`Content-Security-Policy`、`Strict-Transport-Security`（仅 HTTPS 环境）、请求体上限、连接/读取超时和静态资源缓存策略；生产 CORS 只允许显式来源。

- [ ] **Step 5: 验证容器**

  ```bash
  OCEANBASE_PASSWORD="$OCEANBASE_ROOT_PASSWORD" \
  PMS_JWT_SECRET="$(openssl rand -hex 32)" \
  docker compose -f docker-compose.example.yml config --quiet
  docker build -t pms-backend:local .
  docker build -t pms-front:local /Users/fs/Desktop/Project/pms-front
  ```

  Review 镜像中无密码、JWT、Git 历史和上传文件，容器以非 root 运行，服务重启后数据和上传文件仍存在。

- [ ] **Step 6: Commit**

  ```bash
  git add Dockerfile docker-compose.example.yml docker docs/operations/enterprise-upgrade-runbook.md
  git commit -m "chore: harden container runtime defaults"
  ```

**验收：** 任一业务容器异常退出后可自动恢复；健康检查能区分“容器存活”和“应用可用”；镜像和 Compose 不包含生产秘密。

---

### Task 6: 可观测性、审计保留和 API 合同

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/brad/pms/config/ObservabilityConfig.java`
- Modify: `src/main/java/com/brad/pms/controller/HealthController.java`
- Modify: `src/main/java/com/brad/pms/service/OperationLogService.java`
- Create: `src/main/java/com/brad/pms/job/OperationLogRetentionJob.java`
- Create: `src/main/resources/logback-spring.xml`
- Create: `src/main/resources/openapi/pms-api.yaml`
- Modify: `README.md`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Test: `src/test/java/com/brad/pms/controller/HealthControllerTest.java`
- Test: `src/test/java/com/brad/pms/service/OperationLogServiceTest.java`

**Interfaces:**
- Consumes: `X-Request-Id`、登录日志、操作日志和数据库迁移版本。
- Produces: readiness/liveness 分离、结构化日志、指标端点、审计保留策略和可导入的 OpenAPI 文档。

- [ ] **Step 1: 写健康与脱敏测试**

  断言数据库不可用时 readiness 失败但 liveness 可返回；迁移版本缺失时 readiness 明确报告；日志事件不包含密码、JWT、refresh token 或 token hash。

- [ ] **Step 2: 增加指标和结构化日志**

  引入 Spring Boot Actuator，仅开放健康和指标所需端点；日志输出 JSON 字段 `timestamp, level, requestId, userId, action, resource, durationMs`，异常堆栈只写服务端日志，不返回前端。

- [ ] **Step 3: 实现审计保留策略**

  增加按天批处理的审计清理任务，默认保留 180 天，可通过环境变量调整；清理任务本身写入审计事件并支持 dry-run；不得删除登录失败和权限拒绝记录到保留期之前的数据。

- [ ] **Step 4: 固化 OpenAPI 合同**

  用 `openapi/pms-api.yaml` 描述认证、项目、组织、导入、审计和错误响应；README 增加生成/校验命令，前端和后端 CI 对合同做格式检查。

- [ ] **Step 5: 运行 Review 并提交**

  ```bash
  mvn -q test
  git diff --check
  git add pom.xml src/main docs README.md src/test/java
  git commit -m "feat: add production observability and API contract"
  ```

**验收：** 运维人员可以通过健康、指标和结构化日志定位一次请求；审计页面和 OpenAPI 文档不泄露敏感数据。

---

### Task 7: 集成 CI/CD、安全扫描与浏览器冒烟

**Files:**
- Modify: `.github/workflows/ci.yml`
- Modify: `/Users/fs/Desktop/Project/pms-front/.github/workflows/ci.yml`
- Create: `.github/workflows/integration.yml`
- Create: `.github/dependabot.yml`
- Create: `scripts/smoke-test.sh`
- Create: `/Users/fs/Desktop/Project/pms-front/tests/e2e/auth-and-project.spec.ts`
- Modify: `/Users/fs/Desktop/Project/pms-front/package.json`
- Modify: `docs/operations/release-checklist.md`

**Interfaces:**
- Consumes: Maven/pnpm 构建、Docker Compose、OceanBase、OpenAPI 合同和现有浏览器路由。
- Produces: 每次提交的单元测试、真实 OceanBase 集成测试、前端 E2E、镜像漏洞扫描、SBOM 和依赖更新。

- [ ] **Step 1: 写 CI 阻断条件**

  在 CI 中固定以下失败条件：后端测试失败、前端 typecheck/build 失败、OpenAPI 校验失败、仓库出现 H2 运行配置或私钥、Docker 镜像高危漏洞、SBOM 生成失败。

- [ ] **Step 2: 增加 OceanBase 集成作业**

  启动固定版本 OceanBase，执行 accounts-init、schema-init、preflight、upgrade 两次和后端健康检查；使用临时随机数据库密码，不把密码写入日志或 artifact。

- [ ] **Step 3: 增加前端 E2E**

  使用 Playwright 验证登录、刷新、项目列表、项目详情、人员、组织、角色、导入和退出；至少运行 1280px 桌面和 390px 窄屏两个 viewport。

- [ ] **Step 4: 增加镜像与依赖扫描**

  使用 Trivy 扫描 backend/frontend 镜像，使用 Syft 生成 SPDX SBOM，启用 Dependabot（Maven、pnpm、Docker、GitHub Actions）并将高危漏洞设为合并阻断。

- [ ] **Step 5: 运行本地等价 CI**

  ```bash
  mvn -q test
  cd /Users/fs/Desktop/Project/pms-front
  pnpm install --frozen-lockfile
  pnpm test
  pnpm typecheck
  pnpm build
  pnpm exec playwright test
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add .github scripts/smoke-test.sh docs/operations/release-checklist.md
  git add /Users/fs/Desktop/Project/pms-front/.github /Users/fs/Desktop/Project/pms-front/tests /Users/fs/Desktop/Project/pms-front/package.json
  git commit -m "ci: add integration, e2e, security scan and sbom gates"
  ```

**验收：** 任意提交都能得到可追踪的测试、镜像和 SBOM 结果；浏览器主流程在桌面和窄屏均通过。

---

### Task 8: 生产演练、最终 Review 与开源交付

**Files:**
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Modify: `docs/operations/release-checklist.md`
- Modify: `docs/operations/infrastructure-status.md`
- Modify: `README.md`
- Modify: `/Users/fs/Desktop/Project/pms-front/README.md`
- Create: `docs/operations/drill-records/2026-08-28-production-readiness.md`

**Interfaces:**
- Consumes: Task 0–7 的脚本、镜像、CI 报告、备份文件和测试环境。
- Produces: 一份可复核的生产准备记录和开源部署入口。

- [ ] **Step 1: 执行从零部署演练**

  在干净目录使用 `.env.oceanbase.example` 的实际副本启动 OceanBase、账号初始化、schema-init、backend 和 frontend；登录并验证项目、组织、人员、角色、导入和审计页面。

- [ ] **Step 2: 执行备份恢复演练**

  备份当前 `brad_pms`，恢复到临时库，运行行数、外键、索引、组织根节点和权限校验；记录耗时、备份大小、恢复结果和 RPO/RTO。

- [ ] **Step 3: 执行故障演练**

  分别模拟数据库不可用、backend 重启、frontend 重启、迁移失败和通知服务不可用，记录健康状态、告警、恢复命令和数据是否保持完整。

- [ ] **Step 4: 完成安全与文档 Review**

  检查默认密码、JWT、CORS、Cookie、限流、上传、审计、root 使用、镜像 digest、依赖漏洞和英文/中文文案；将所有结论写入 `drill-records/2026-08-28-production-readiness.md`。

- [ ] **Step 5: 发布前最终验证**

  ```bash
  git diff --check
  git status --short
  mvn -q test
  cd /Users/fs/Desktop/Project/pms-front
  pnpm typecheck
  pnpm build
  ```

  Expected: 两个仓库无未提交意外修改，所有自动化检查成功；任何未完成项必须在发布清单中明确标为阻塞项。

- [ ] **Step 6: Commit**

  ```bash
  git add docs README.md /Users/fs/Desktop/Project/pms-front/README.md
  git commit -m "docs: record enterprise production readiness review"
  ```

**验收：** 新企业只依赖 README、Compose、环境变量说明和升级手册即可部署；备份、恢复、升级、回滚、审计和故障处理均有实际演练记录。

## 执行顺序与停靠点

按 `Task 0 → Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7 → Task 8` 顺序执行。Task 1 和 Task 2 未完成前，不进入生产部署；Task 3 和 Task 4 未完成前，不开放外网或大规模导入；Task 7 未完成前，不发布开源版本。每个任务完成后必须执行该任务的 Review gate，并更新 `docs/operations/infrastructure-status.md`。

## 计划自我 Review

- 已覆盖账号隔离、迁移、备份、恢复、错误响应、Cookie、限流、通知、上传、容器、健康检查、日志、审计、OpenAPI、CI、E2E、漏洞扫描和开源交付。
- 所有任务均列出文件边界、输入输出接口、测试命令和提交点。
- 未改变单企业模型、组织负责人/员工主归属关系或现有项目业务逻辑。
- 现有本地 OceanBase 使用空密码只能作为迁移后的本地特例，计划明确要求生产账号使用非空强密码。
- `pms_app` 与 `pms_migrator` 的凭据不写入迁移 SQL、镜像或 Git 历史；实际执行时只从运行环境读取。
