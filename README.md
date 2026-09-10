# PMS（Project Management System）后端

[![CI](https://github.com/xiebinJava/pms-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/xiebinJava/pms-backend/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

PMS 是一个面向单个企业、本地部署的项目管理系统。一个部署实例只服务一家企业，不使用 `tenant_id`，也不拆分成微服务。运行时和测试都只使用 MySQL 8。

如果你只是想安装并使用 PMS，请走独立发行仓库 [`pms-distribution`](https://github.com/xiebinJava/pms-distribution)：它使用预构建镜像，不需要本机安装 Java、Maven、Node.js 或 pnpm。本仓库面向后端开发和从源码构建。

## 最终用户安装（推荐）

```bash
git clone https://github.com/xiebinJava/pms-distribution.git
cd pms-distribution
./scripts/bootstrap.sh
```

打开 <http://localhost:5173>。脚本会生成管理员密码并写入本地、未跟踪的 `.pms-bootstrap-secrets`。生产配置、升级和备份见发行仓库文档。

## 从源码启动（开发者）

### 1. 准备环境

- Docker 20+ 和 Docker Compose v2
- Git
- 至少 2 GB 可用内存（本地 MySQL 8 与后端）

前后端需要放在同一个父目录，Compose 才能找到前端构建上下文：

```bash
mkdir pms && cd pms
git clone https://github.com/xiebinJava/pms-backend.git
git clone https://github.com/xiebinJava/pms-front.git
cd pms-backend
```

### 2. 创建本地配置

```bash
cp .env.mysql.example .env
openssl rand -hex 32
```

编辑 `.env`，至少替换下面这些占位值。`.env` 只在本机使用，永远不要提交：

| 配置                           | 用途                             |
| ------------------------------ | -------------------------------- |
| `MYSQL_ROOT_PASSWORD`          | Compose 初始化 MySQL root        |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 后端运行账号                     |
| `PMS_JWT_SECRET`               | JWT 签名密钥，至少 32 字节随机值 |
| `PMS_BOOTSTRAP_ADMIN_PASSWORD` | 首次创建管理员的密码，至少 12 位 |

本地示例的管理员邮箱由 `PMS_BOOTSTRAP_ADMIN_EMAIL` 指定，默认是 `alex.zhang@example.com`。登录时使用邮箱，不区分大小写；中文名和英文名只是展示信息。

### 3. 启动完整系统

```bash
docker compose -f docker-compose.example.yml up -d --build
docker compose -f docker-compose.example.yml ps
```

首次启动会按以下顺序完成 MySQL、附件目录、后端 Flyway 迁移（V1–V41）和前端：

```text
MySQL → uploads-init → backend（Flyway V1–V41）→ frontend
```

打开 <http://localhost:5173>。后端健康检查地址：

```text
http://127.0.0.1:8080/api/health/live
http://127.0.0.1:8080/api/health/ready
```

查看日志：

```bash
docker compose -f docker-compose.example.yml logs -f backend
```

停止服务但保留数据卷：

```bash
docker compose -f docker-compose.example.yml down
```

只有确认要删除本地数据库和附件时，才使用 `down -v`。

## 系统结构

```text
浏览器
  ↓ http://localhost:5173
pms-front（Vue 3 + Nginx/Vite）
  ↓ /api 代理
pms-backend（Spring Boot，8080）
  ↓
MySQL 8 `pms`（3306）
```

后端主要职责：

- 登录、JWT 会话、邮箱身份和账号生命周期
- RBAC 权限、数据范围和统一错误响应
- 组织架构、组织负责人、员工主归属/兼职归属
- 项目、任务、里程碑、成员、评论、附件和通知
- Excel/CSV 预览、校验、幂等提交、错误报告和事务回滚
- 审计日志、请求追踪、健康检查和 Prometheus 指标

前端页面和操作说明见兄弟仓库 [`pms-front`](https://github.com/xiebinJava/pms-front) 以及 [使用手册](https://github.com/xiebinJava/pms-front/blob/main/docs/user-manual.md)。

## 数据库与账号

运行时只使用 MySQL 8 数据库 `pms`。Compose 会创建 `MYSQL_USER` 应用账号；Flyway 在后端启动时执行迁移，不再使用独立的 migrator 镜像。

| 账号                 | 用途                     |
| -------------------- | ------------------------ |
| `MYSQL_USER`（默认 `pms`） | 后端业务读写和 Flyway 迁移 |
| MySQL `root`         | 仅初始化、备份和隔离恢复 |

组织负责人和员工主归属是两套关系：

- `sys_org_unit.leader_user_id`：某个组织由谁负责；
- `sys_user_position.is_primary`：员工的主归属组织；
- 同一员工还可以有多个兼职或项目归属。

不要通过修改组织负责人来替代员工归属调整。

### 已有 MySQL 的升级

已有企业数据库时，不要重新套用示例 Compose，也不要删除或重建业务库。先备份，再启动带新镜像/JAR 的后端，让 Flyway 补齐缺失版本：

```bash
set -a
source .env.mysql.local
set +a
./scripts/backup-mysql.sh
./scripts/enterprise-preflight.sh
# 启动新版本后端后：
./scripts/verify-enterprise-migration.sh
```

当前基线为 V1–V41。详细步骤见 [升级手册](docs/operations/enterprise-upgrade-runbook.md)。

## 本地直接启动后端（已有 MySQL 时）

不使用完整 Compose、只启动本地 JAR 时：

```bash
mvn -q -DskipTests package
PMS_ENV_FILE=.env.mysql.local ./scripts/start-local-mysql.sh
```

后端 API：`http://127.0.0.1:8080/api`；管理和就绪端口默认只监听 `127.0.0.1:8081`。

停止本地后端：

```bash
./scripts/stop-local-mysql.sh
```

本地文件 `.env.mysql.local` 不会被 Git 跟踪。启动前必须提供非空的 `PMS_JWT_SECRET`；生产环境还必须关闭 token 回显并启用 HTTPS、受限 CORS 和 SMTP 校验。

## 开发与验证

自动化测试使用 Testcontainers MySQL 8，本机需要可用的 Docker。Colima 用户请先导出：

```bash
export DOCKER_HOST=unix://$HOME/.colima/<profile>/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1
```

```bash
mvn -q test
./scripts/validate-openapi.sh
bash -n scripts/*.sh docker/*.sh
./scripts/check-privacy.sh
```

真实数据库验收使用：

```bash
set -a
source .env.mysql.local
set +a
./scripts/verify-enterprise-migration.sh

PMS_SMOKE_USERNAME=alex.zhang@example.com PMS_SMOKE_PASSWORD='<本地测试密码>' \
  ./scripts/smoke-test.sh
```

不要把密码、JWT、SMTP、对象存储密钥或导入文件放进仓库。提交前可执行隐私扫描：

```bash
./scripts/check-privacy.sh
```

## 目录速览

```text
src/main/java/com/brad/pms/
├── controller/  REST API 与权限注解
├── service/     业务规则、事务和数据范围
├── mapper/      MyBatis-Plus 数据访问
├── entity/      数据库对象
├── dto/         请求命令与响应对象
├── security/    JWT、会话、RBAC、数据范围
├── config/      数据源、初始化、CORS、观测
├── storage/     本地盘或 S3 兼容对象存储
└── webhook/     可选签名出站事件

src/main/resources/
├── db/migration/  V1–V41 MySQL / Flyway 迁移脚本
└── openapi/       pms-api.yaml 接口合同

docs/
├── business-specification.md       业务规则和数据关系
└── operations/                     升级、备份、发布和故障演练
```

## 常用接口

除登录和健康检查外，请求头都需要 `Authorization: Bearer <JWT>`。完整接口以 [OpenAPI 合同](src/main/resources/openapi/pms-api.yaml) 为准。

| 方法   | 路径                                   | 用途                 |
| ------ | -------------------------------------- | -------------------- |
| `POST` | `/api/auth/login`                      | 邮箱登录             |
| `GET`  | `/api/auth/me`                         | 当前登录用户         |
| `GET`  | `/api/workbench`                       | 我的任务、项目和动态 |
| `POST` | `/api/projects/page`                   | 项目分页             |
| `GET`  | `/api/projects/{id}`                   | 项目详情             |
| `GET`  | `/api/org/tree`                        | 项目页可用的组织树   |
| `GET`  | `/api/admin/org/tree`                  | 管理组织树           |
| `GET`  | `/api/admin/org/{id}/history`          | 组织变更历史         |
| `POST` | `/api/admin/import/preview/organizations` | 组织 Excel/CSV 预览和校验 |
| `POST` | `/api/admin/import/preview/users`       | 员工 Excel/CSV 预览和校验 |
| `POST` | `/api/admin/import/{jobId}/commit`     | 幂等提交导入任务     |
| `GET`  | `/api/admin/import/{jobId}/errors.csv` | 下载服务端错误报告   |
| `GET`  | `/api/feedback/tickets`               | 查询反馈工单         |
| `POST` | `/api/feedback/tickets`               | 提交反馈工单         |
| `GET`  | `/api/health/live`                     | 存活检查             |
| `GET`  | `/api/health/ready`                    | 数据库就绪检查       |

## 生产部署前必须完成

本 README 只解决本地快速启动；生产上线前请逐项完成 [发布验收清单](docs/operations/release-checklist.md)：

- 使用企业自己的 MySQL 8 和 `MYSQL_USER` 凭据，不要用 root 跑应用；
- `PMS_JWT_SECRET` 使用密钥管理器注入，至少 32 字节且不出现在日志；
- `PMS_DEPLOYMENT_ENV=production`，关闭密码重置/邀请 token 回显；
- 使用 HTTPS 反向代理，`PMS_CORS_ALLOWED_ORIGINS` 只允许正式前端来源；
- 配置 SMTP、备份恢复、RPO/RTO、对象存储和监控告警；
- 首次登录后更换演示管理员密码，并执行完整桌面端/移动端验收。

更多内容：

- [业务规范](docs/business-specification.md)
- [升级与预检](docs/operations/enterprise-upgrade-runbook.md)
- [备份与恢复](docs/operations/mysql-backup-restore.md)
- [扩展与观测](docs/operations/scaling-readiness.md)
- [基础设施状态](docs/operations/infrastructure-status.md)
- [贡献指南](CONTRIBUTING.md)
- [安全策略](SECURITY.md)

## 许可证

Apache-2.0。贡献前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。
