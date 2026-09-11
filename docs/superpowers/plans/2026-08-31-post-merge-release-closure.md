# 合并后企业级发布收口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在前后端代码已进入远程 `main` 后，完成 MySQL 私有化部署的发布验证、生产配置、备份恢复演练和开源交付收口。

**Architecture:** 保持单企业本地部署，不引入多租户迁移。运行时数据库只使用 MySQL `pms`；后端以 Java 17/Spring Boot 3.5 提供 `/api`，前端以 Vue 3/Vite 提供静态应用，所有发布验证通过 CI 和可重复脚本完成。

**Tech Stack:** Java 17、Spring Boot 3.5、Jakarta Validation/Servlet、MyBatis-Plus、MySQL 8、Vue 3、TypeScript、Vite、pnpm、Docker Compose、GitHub Actions、Playwright。

**Spec:** `docs/operations/infrastructure-status.md`、`docs/operations/release-checklist.md`、`docs/operations/enterprise-upgrade-runbook.md`、`../pms-front/docs/frontend-design-system.md`

## 当前执行状态（2026-09-01）

- Task 1：本地收口完成。不可变 `v1.0.0` 标签仍为后端 `7cc2820`、前端 `1519e88736d2d9fe19c1e436b8a96cd66b1457bf`；当前本地 `main` 已追加代码、文档与集成工作流提交，待远程凭据可用后推送。
- Task 2：后端/前端本地门禁与后端 CI 已完成；当前后端 187 个用例（0 失败、0 错误、1 跳过），前端 111 项通过，类型检查和构建通过；Playwright 桌面、390px 窄屏和手册录制均通过。跨仓库集成仍等待 `PMS_FRONT_REPO_READ_TOKEN`。
- Task 3：配置、Compose、密钥材料和安全启动校验已完成自动化核查；生产密钥注入必须在目标企业环境执行。
- Task 4：2026-09-01 已在本机 MySQL 完成 V1–V10 幂等升级、逻辑备份、SHA-256、全表精确行数元数据、隔离空库恢复和关键数据比对；备份脚本版本排序/输入流问题已修复并通过回归测试。
- Task 5：2026-09-01 已在本机真实前后端完成邮箱登录、项目入口、桌面/390px Playwright 冒烟和使用手册录制；生产目标环境仍需用企业账号复验。
- Task 6：发布文档与扩展能力评估已更新；跨仓库 Secret 和生产密钥仍是外部配置动作。

## Global Constraints

- 远程主分支为 `main`，不存在 `master`；后端和前端均以远程 `main` 作为发布基线。
- 单企业私有化部署；MySQL 是唯一运行时数据库，测试使用 Testcontainers MySQL。
- 不在仓库、日志或镜像中保存密码、JWT、SMTP 凭据、邀请 Token 或重置 Token。
- 生产环境必须启用通知启动校验，关闭重置/邀请 Token 回显，并限制 CORS 和 Actuator 访问来源。
- 每个任务先执行验证，再修复问题；完成后更新 `infrastructure-status.md` 或 `release-checklist.md` 并进行 review。
- 备份恢复只允许写入明确的空目标库，不对现有 `pms` 执行覆盖式恢复。

---

### Task 1: 固定发布基线并同步本地主分支

**Files:**
- Modify: `docs/operations/infrastructure-status.md`
- Modify: `docs/operations/release-checklist.md`
- Verify: `../pms-front/.github/workflows/ci.yml`
- Verify: `.github/workflows/ci.yml`
- Verify: `.github/workflows/integration.yml`

**Deliverable:** 两个仓库的本地 `main` 与远程 `origin/main` 同步，发布提交、前端集成测试引用和文档基线明确记录。

- [ ] **Step 1: 刷新两个仓库的远程引用**

```bash
cd .
git fetch --prune origin
git switch main
git pull --ff-only origin main

cd ../pms-front
git fetch --prune origin
git switch main
git pull --ff-only origin main
```

- [ ] **Step 2: 验证工作区和发布提交**

```bash
git -C . status --short --branch
git -C ../pms-front status --short --branch
git -C . log -1 --oneline origin/main
git -C ../pms-front log -1 --oneline origin/main
```

Expected: 两个工作区 clean，当前分支为 `main`，且本地 `main` 不落后于远程 `origin/main`。

- [ ] **Step 3: 固定集成工作流使用的前端发布提交**

将后端 `.github/workflows/integration.yml` 中前端 checkout 的 `ref` 固定为本次发布的远程 `main` 提交 `1519e88`，并保留注释说明该 SHA 与发布基线对应。

- [ ] **Step 4: 更新状态文档并 review**

在 `infrastructure-status.md` 记录后端与前端远程 `main` 的最终提交，注明本任务的命令与结果；检查没有把 `master` 写入新的部署说明。

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/integration.yml docs/operations/infrastructure-status.md docs/operations/release-checklist.md
git commit -m "chore: pin post-merge release baseline"
```

---

### Task 2: 取得 CI、构建和浏览器门禁报告

**Files:**
- Verify: `.github/workflows/ci.yml`
- Verify: `.github/workflows/integration.yml`
- Verify: `../pms-front/.github/workflows/ci.yml`
- Test: `../pms-front/tests/e2e/auth-and-project.spec.ts`
- Modify: `docs/operations/infrastructure-status.md`

**Deliverable:** 发布基线在 GitHub Actions 和本地门禁中获得可保存的测试结果；失败时只修复失败任务，不绕过检查。

- [ ] **Step 1: 运行后端本地门禁**

```bash
cd .
mvn -q test
./scripts/validate-openapi.sh
bash -n scripts/*.sh docker/*.sh
```

Expected: 测试、OpenAPI 校验和脚本语法全部退出码为 0。

- [ ] **Step 2: 运行前端本地门禁**

```bash
cd ../pms-front
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build
```

Expected: 测试、类型检查和生产构建全部通过。

- [ ] **Step 3: 触发并保存 GitHub Actions 结果**

确认后端 CI、前端 CI 和后端集成工作流均在同一发布提交上运行，并检查 Docker 镜像 Trivy 高危扫描、SPDX SBOM、MySQL 迁移幂等性、API smoke 和 Playwright 桌面/窄屏测试均为通过。

- [ ] **Step 4: review 测试缺口**

检查失败日志中是否有网络超时、凭据缺失、浏览器依赖缺失或真实应用缺陷；网络/基础设施阻塞必须记录证据，不能改成静默跳过业务测试。

- [ ] **Step 5: 更新文档**

在 `infrastructure-status.md` 的 Task 7 和 Task 8 中记录 workflow URL、提交 SHA、运行时间和通过/阻塞原因；在 `release-checklist.md` 勾选已获得报告的项目。

- [ ] **Step 6: Commit**

```bash
git add docs/operations/infrastructure-status.md docs/operations/release-checklist.md
git commit -m "docs: record release gate results"
```

---

### Task 3: 收口生产配置和密钥策略

**Files:**
- Verify/Modify: `.env.mysql.example`
- Verify/Modify: `docker-compose.example.yml`
- Verify/Modify: `src/main/resources/application-mysql.yml`
- Verify/Modify: `src/main/java/com/brad/pms/config/NotificationConfigurationValidator.java`
- Modify: `docs/operations/enterprise-upgrade-runbook.md`
- Modify: `docs/operations/release-checklist.md`
- Test: `src/test/java/com/brad/pms/config/MysqlRuntimeConfigurationTest.java`
- Test: `src/test/java/com/brad/pms/config/NotificationConfigurationValidatorTest.java`

**Deliverable:** 目标企业部署使用 `PMS_DEPLOYMENT_ENV=production`、强随机 JWT、正式 SMTP、最小权限 `pms_app`，且不会回显敏感 Token。

- [ ] **Step 1: 生成目标环境密钥并通过密钥管理注入**

使用目标环境的密钥管理器生成至少 32 字节 `PMS_JWT_SECRET`、`pms_app`/`pms_migrator` 密码和 SMTP 凭据；只将变量名写入 `.env.mysql.example`，禁止把值写入 Git。

- [ ] **Step 2: 固定生产安全变量**

生产环境必须设置：`PMS_DEPLOYMENT_ENV=production`、`PMS_NOTIFICATION_STARTUP_CHECK=true`、`PMS_PASSWORD_RESET_EXPOSE_TOKEN=false`、`PMS_INVITATION_EXPOSE_TOKEN=false`，并将 `PMS_CORS_ALLOWED_ORIGINS` 限制为正式前端来源。

- [ ] **Step 3: 执行配置和敏感信息检查**

```bash
cd .
mvn -q -Dtest=MysqlRuntimeConfigurationTest,NotificationConfigurationValidatorTest test
git grep -nE 'AKIA[0-9A-Z]{16}|-----BEGIN (RSA|OPENSSH|EC) PRIVATE KEY-----' -- ':!target' ':!backups' || true
```

Expected: 配置测试通过，仓库不包含真实凭据；生产启动在缺少 SMTP 或弱 JWT 时明确失败。

- [ ] **Step 4: review 配置文档**

逐项核对运行手册中的账号、JWT、CORS、SMTP、HTTPS 反代、Token 回显和 Actuator 内网限制，确保示例默认值不会被误用于生产。

- [ ] **Step 5: Commit**

```bash
git add .env.mysql.example docker-compose.example.yml src/main/resources/application-mysql.yml docs/operations/enterprise-upgrade-runbook.md docs/operations/release-checklist.md
git commit -m "chore: finalize production configuration contract"
```

---

### Task 4: 完成 MySQL 备份、恢复和故障演练

**Files:**
- Verify: `scripts/backup-mysql.sh`
- Verify: `scripts/verify-backup.sh`
- Verify: `scripts/restore-mysql.sh`
- Verify: `scripts/enterprise-preflight.sh`
- Verify: `scripts/verify-enterprise-migration.sh`
- Create: `docs/operations/drill-records/mysql-backup-restore.md`
- Modify: `docs/operations/infrastructure-status.md`

**Deliverable:** 在不触碰现有 `pms` 数据的前提下完成一次可验证的备份、校验、空库恢复、迁移校验和应用切换演练。

- [ ] **Step 1: 对现有数据库执行只读预检**

```bash
cd .
set -a; . ./.env.mysql.local; set +a
./scripts/enterprise-preflight.sh
./scripts/verify-enterprise-migration.sh
```

Expected: 预检通过，记录 schema V6、关键表/索引/外键和唯一根组织数量；脚本不修改数据。

- [ ] **Step 2: 创建带校验值的备份**

```bash
export PMS_BACKUP_DIR=/secure/pms-backups/2026-08-31
./scripts/backup-mysql.sh
backup_file=$(find "$PMS_BACKUP_DIR" -maxdepth 1 -type f -name 'pms-*.sql.*' ! -name '*.sha256' | sort | tail -n 1)
./scripts/verify-backup.sh "$backup_file"
```

Expected: 生成压缩备份、`.sha256` 和 `.meta`，校验通过；备份目录权限为 700。

- [ ] **Step 3: 恢复到明确的空目标库**

创建一次性空库 `pms_restore`，设置 `MYSQL_DB=pms_restore` 后执行：

```bash
MYSQL_DB=pms_restore ./scripts/restore-mysql.sh --allow-empty-target "$backup_file"
MYSQL_DB=pms_restore ./scripts/verify-enterprise-migration.sh
```

Expected: 空库恢复和 V6 校验通过；脚本拒绝非空目标，也不会覆盖 `pms`。

- [ ] **Step 4: 执行故障路径演练**

在隔离环境短暂停止数据库或阻断连接，确认 `/api/health/ready` 返回 503、应用不泄露 SQL/密码；恢复数据库后就绪探针返回 200，登录和只读项目查询恢复。

- [ ] **Step 5: 记录证据并 review**

在恢复演练文档记录备份文件名、SHA-256、schema 版本、恢复耗时、探针响应、失败路径和回滚联系人，不记录数据库密码；更新 `infrastructure-status.md` 的 Task 2 和 Task 8。

- [ ] **Step 6: Commit**

```bash
git add docs/operations/drill-records/mysql-backup-restore.md docs/operations/infrastructure-status.md
git commit -m "docs: record MySQL recovery drill"
```

---

### Task 5: 目标环境部署和全流程浏览器验收

**Files:**
- Verify: `docker-compose.example.yml`
- Verify: `docs/operations/release-checklist.md`
- Verify: `../pms-front/tests/e2e/auth-and-project.spec.ts`
- Create: `docs/operations/drill-records/2026-08-31-application-acceptance.md`

**Deliverable:** 在一套接近生产的目标环境完成从登录到注销的业务冒烟，确认前后端字段、权限、组织、导入和项目协作链路一致。

- [ ] **Step 1: 按部署手册启动目标环境**

使用正式密钥注入后执行 `docker compose -f docker-compose.example.yml up -d --build`；确认后端仅使用 `mysql` profile，前端通过 `/api` 访问后端，上传目录使用持久卷。

- [ ] **Step 2: 执行服务级 smoke**

```bash
PMS_SMOKE_BASE_URL=http://127.0.0.1:8080/api ./scripts/smoke-test.sh
```

Expected: liveness、readiness 和配置的邮箱管理员登录全部通过；历史英文名兼容登录按兼容性用例验证。

- [ ] **Step 3: 执行浏览器验收路径**

使用桌面和 390px 窄屏分别验证：登录/刷新/注销、工作台、项目列表、项目详情、节点排期、业务线负责人联动、人员主归属、组织画布拖拽缩放、角色数据范围、导入预览/回滚、审计检索和无权限 API 返回 403。

- [ ] **Step 4: 检查视觉和交互回归**

确认中文主文案、英文小字、不同导航图标、日期两行布局、36px 控件高度、全屏可拖拽画布、任务卡片删除交互以及移动端无横向溢出。

- [ ] **Step 5: 记录验收和 review**

将每条路径的 URL、账号类型、结果、截图/日志证据和剩余风险写入验收记录；任何失败项先修复并重跑，不用“忽略失败”完成验收。

- [ ] **Step 6: Commit**

```bash
git add docs/operations/drill-records/2026-08-31-application-acceptance.md docs/operations/release-checklist.md
git commit -m "docs: record application acceptance"
```

---

### Task 6: 打版本并准备开源交付

**Files:**
- Modify: `README.md`
- Modify: `docs/operations/release-checklist.md`
- Modify: `docs/operations/infrastructure-status.md`
- Modify: `../pms-front/README.md`
- Create: `docs/operations/releases/v0.1.0.md`

**Deliverable:** 新用户可从 README 以 MySQL 启动项目，发布版本包含迁移、回滚、备份、默认账号安全说明、CI 证据和已知限制。

- [ ] **Step 1: 对齐两仓库 README**

说明远程分支为 `main`、单企业部署边界、MySQL 前置条件、`pms_app`/`pms_migrator` 职责、生产环境变量、健康检查和首次管理员初始化方式；明确测试也不使用内存数据库。

- [ ] **Step 2: 生成发布说明**

在 `docs/operations/releases/v0.1.0.md` 写入版本提交、迁移版本 V1–V7、验证命令、CI 运行链接、部署步骤、回滚限制、备份恢复证据、已知缺口和安全注意事项。

- [ ] **Step 3: 最终检查并创建 tag**

```bash
git -C . diff --check
git -C ../pms-front diff --check
git -C . tag -a v0.1.0 -m "PMS enterprise self-hosted release"
git -C ../pms-front tag -a v0.1.0 -m "PMS frontend release"
```

本次本机演练记录齐全后创建发布 tag；跨仓库 Secret、生产密钥和目标企业验收仍在发布说明中明确标注，不以本机结果冒充生产认证。

- [ ] **Step 4: 最终 review**

核对代码、数据库、权限、日志、响应式布局、文案、文档和镜像扫描结果；确认没有随机生产 JWT、共享默认密码、内存数据库运行入口或未说明的人工步骤。

- [ ] **Step 5: Commit and push**

```bash
git add README.md docs/operations/release-checklist.md docs/operations/infrastructure-status.md docs/operations/releases/v0.1.0.md
git commit -m "chore: prepare PMS enterprise v0.1.0 release"
git push origin main --follow-tags
```

---

## 暂不执行的工作

需求管理、测试管理、缺陷管理等研发管理扩展放到 `v0.1.0` 发布并完成一轮真实部署后再拆分独立产品计划；多租户 V1–V5 迁移不执行，当前架构明确保持单企业本地部署。

## 执行顺序

按 Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 顺序执行。每项完成后先跑验收命令，再进行代码/配置/浏览器 review，更新文档后才进入下一项；任何 P1 失败都停在当前任务修复。
