# PMS Backend — 项目管理系统后端

单企业、本地部署的开源项目管理后端。一个实例只服务一家企业，没有 `tenant_id`，也不拆微服务。许可证 Apache-2.0，贡献前请读 [CONTRIBUTING.md](CONTRIBUTING.md)。

兄弟仓库是 [`pms-front`](../pms-front)。演示身份只用 `张伟` / `Alex.Zhang` / `alex.zhang@example.com`。

## 现在能做什么

- 邮箱登录、组织树、RBAC、数据范围、审计日志
- 项目、任务看板、里程碑、成员、动态
- 工作台、任务详情（子任务 / 评论 / 附件）、站内通知、范围内搜索
- 可选 OIDC / LDAP（默认关；只给已经邀请且已激活的邮箱建会话）
- 可选 S3/MinIO 附件与签名 Webhook（默认本地盘、不出站）
- 企业运行时：OceanBase MySQL 兼容模式，库名 `brad_pms`
- 贡献者本机：MySQL 8 轻量路径，库名 `pms`（不要拿它替换企业库）

完整业务约定见 [`docs/business-specification.md`](docs/business-specification.md)。OpenAPI 合同在 `src/main/resources/openapi/pms-api.yaml`。

## 技术栈

- Java 17 + Maven
- Spring Boot 3.5.14、MyBatis-Plus 3.5.17、JWT（jjwt）
- OceanBase（企业默认）或 MySQL 8（仅贡献者）
- Actuator 管理口默认 `127.0.0.1:8081`（`health` / `metrics` / `prometheus`）

## 模块

```
com.brad.pms
├── auth        OIDC / LDAP / 本地登录提供者（默认只开本地）
├── common      统一返回 / 分页 / 异常 / 枚举
├── config      MyBatis-Plus、CORS、种子数据、观测
├── security    JWT、登录拦截、用户上下文、数据范围
├── storage     本地盘或 S3 兼容对象存储
├── webhook     可选签名出站事件
├── controller  REST
├── service     业务编排
├── mapper      MyBatis-Plus Mapper
├── entity      DataObject
├── dto         request（Cmd/Qry）/ response（DTO）
└── convertor   实体 <-> DTO
```

## 本机启动

先准备一份本地 env（不要提交）：

```bash
cp .env.oceanbase.example .env.oceanbase.local   # 企业 / 本机 OceanBase
# 或
cp .env.mysql.example .env.mysql.local           # 仅贡献者
```

至少填入数据库密码、至少 32 字节的 `PMS_JWT_SECRET`，以及至少 12 位的 `PMS_BOOTSTRAP_ADMIN_PASSWORD`。空库首次启动会创建演示管理员 `alex.zhang@example.com` / 张伟。之后用邀请或 Excel/CSV 加人，不要在文档里写测试用的共享口令。

### 贡献者：MySQL 8

只起一个本机 MySQL 8.4 容器。`mysql` profile 会用 Flyway 把空库升到当前迁移（含附件 V8、通知 V9）。库名是 `pms`，不是 `brad_pms`。

```bash
./scripts/start-local-mysql.sh
```

前端在 `pms-front` 执行 `pnpm dev`，代理到 `http://localhost:8080`。停止：`./scripts/stop-local-mysql.sh`；连容器一起关（保留数据卷）：`./scripts/stop-local-mysql.sh --down`。

默认绑定 `127.0.0.1:3306`。端口冲突时改 `.env.mysql.local` 里的 `MYSQL_PORT`。

### 企业默认：OceanBase

本机已有 `brad_pms` 时：

```bash
PMS_ENV_FILE=.env.oceanbase.local ./scripts/start-local-oceanbase.sh
```

OceanBase profile **不会**在运行时开 Flyway（OceanBase 4.x 对外报告 MySQL 5.7）。升表用 `./scripts/oceanbase-upgrade.sh`，账号必须是 **`pms_migrator`**。应用运行用 `pms_app`。步骤见 [`docs/operations/enterprise-upgrade-runbook.md`](docs/operations/enterprise-upgrade-runbook.md)。

没有启动脚本、只想直接跑 jar 时，自行导出 `OCEANBASE_*` 后：

```bash
mvn package -DskipTests && java -jar target/pms-backend-1.0.0.jar
```

启动后 API 前缀是 `http://localhost:8080/api`。

## Docker Compose

单机试用（会拉起 OceanBase、迁移、后端、前端）：

```bash
cp .env.oceanbase.example .env
# 编辑 .env：OceanBase 密码、32 字节以上 JWT、引导管理员密码
docker compose -f docker-compose.example.yml up --build
```

前端 `http://localhost:5173`，后端只绑 `127.0.0.1:8080`。Compose 按顺序跑 `accounts-init` → `schema-init`（V1–V10）→ `uploads-init`，再启动应用。健康检查：`/api/health/live`、`/api/health/ready`。

示例默认是 `development`：SMTP 校验关闭，响应里可能带回本地重置/邀请 token。生产必须改成 HTTPS 公网地址、打开 SMTP 校验，并关掉 token 回显。已有业务库不要直接套这份 Compose，走升级手册。

可选观察栈（本机 loopback，不强迫上 Kubernetes）：

```bash
# .env 里还要有 GRAFANA_ADMIN_PASSWORD
docker compose -f docker-compose.example.yml -f docker-compose.observability.yml up --build
```

Prometheus `http://127.0.0.1:9090`，Grafana `http://127.0.0.1:3000`。叠层会把容器内管理口改成 `0.0.0.0:8081` 供刮取，对外仍只发布 `127.0.0.1:8081`。默认 jar / 未叠层的 Compose **不会**把 8081 暴露到公网。

自动化测试用嵌入式库，不动 OceanBase：

```bash
mvn test
./scripts/validate-openapi.sh
./scripts/check-privacy.sh
```

## Kubernetes（可选）

单机继续用 Compose。已经有集群时，用 [`deploy/helm/pms`](deploy/helm/pms/README.md)：

- 只部署后端 + 前端，**不内置 OceanBase**
- 镜像自己 build（`pms-backend:1.0.0` / `pms-front:1.0.0`）
- 密钥用 `existingSecret`，values 里只有占位符
- Ingress 默认关；管理口只有 ClusterIP，不要挂到 Ingress
- 集群里已有 prometheus-operator 时再 `--set serviceMonitor.enabled=true`

## 身份与账号

邮箱是唯一核心身份，登录不区分大小写。展示优先 `中文名（English.Name）`，缺姓名时回退邮箱。每名员工一个主归属，可以有多个兼职/项目归属。

OIDC / LDAP 默认关闭。打开后**不会**从目录自动开账号。常用开关：`PMS_OIDC_ENABLED`、`PMS_OIDC_ISSUER`、`PMS_OIDC_CLIENT_ID`、`PMS_OIDC_CLIENT_SECRET`、`PMS_OIDC_REDIRECT_URI`；以及 `PMS_LDAP_ENABLED`、`PMS_LDAP_URL`、`PMS_LDAP_BASE_DN`。文档主机用 `idp.example.com`、`dc=example,dc=com`。

## 存储与 Webhook

附件默认写 `PMS_UPLOAD_DIR`。对象存储：`PMS_STORAGE_TYPE=s3`，再配 `PMS_S3_ENDPOINT`（例如 `http://minio.example.com:9000`）、`PMS_S3_BUCKET`、`PMS_S3_ACCESS_KEY`、`PMS_S3_SECRET_KEY`。

出站 Webhook 默认关。`PMS_WEBHOOK_ENABLED=true` 后，任务指派和评论会向 `PMS_WEBHOOK_URL` POST，带头 `X-PMS-Signature`。生产必须 HTTPS，`PMS_WEBHOOK_SECRET` 至少 16 位。投递失败只记日志，不回滚站内通知。

## 核心接口

除登录与健康检查外，请求头带 `Authorization: Bearer <token>`。完整合同以 OpenAPI 为准。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 邮箱登录，返回 JWT |
| GET | `/auth/providers` | 当前启用的登录方式 |
| GET | `/auth/oidc/start` | 开始 OIDC 授权码登录 |
| POST | `/auth/oidc/callback` | 用授权码换本系统会话 |
| POST | `/auth/ldap/login` | 目录账号登录 |
| GET | `/auth/me` | 当前用户 |
| GET | `/users/search?keyword=` | 用户搜索 |
| GET | `/workbench` | 工作台（我的任务、参与项目、最近动态） |
| GET | `/notifications` | 站内通知；另有 unread-count / read / read-all |
| GET | `/search?q=` | 可读范围内搜索项目、任务、评论 |
| POST | `/projects/page` | 项目分页 |
| POST | `/projects` | 新建项目 |
| GET/PUT/DELETE | `/projects/{id}` | 项目详情 / 更新 / 删除 |
| GET/POST | `/projects/{id}/tasks` | 任务列表 / 新建 |
| GET | `/tasks/{id}` | 任务详情（子任务、评论、附件） |
| PUT/DELETE | `/tasks/{id}` | 任务更新 / 删除 |
| POST/GET/DELETE | `/tasks/{id}/attachments` | 附件上传 / 下载 / 删除 |
| PUT | `/tasks/{id}/move` | 拖拽改状态 |
| GET/POST | `/projects/{id}/milestones` | 里程碑 |
| GET/POST | `/projects/{id}/members` | 成员 |
| GET/POST | `/projects/{id}/comments` | 动态 |
| GET/POST/PUT/DELETE | `/admin/org`… | 组织树 |
| GET/POST | `/admin/users`… | 人员、邀请、禁用 |
| GET/POST/PUT/DELETE | `/admin/roles`… | 角色与数据范围 |
| POST | `/admin/import/…` | Excel/CSV 导入 |
| GET | `/admin/audit` | 审计日志 |
| GET | `/health`、`/healthz`、`/health/live`、`/health/ready` | 存活 / 就绪（无需登录） |
| GET | `/actuator/health`、`/actuator/metrics`、`/actuator/prometheus` | 管理口，默认 `127.0.0.1:8081` |

## 运维文档

- 升级与预检：[`docs/operations/enterprise-upgrade-runbook.md`](docs/operations/enterprise-upgrade-runbook.md)
- 发布清单：[`docs/operations/release-checklist.md`](docs/operations/release-checklist.md)
- 扩展边界（限流、多副本、对象存储）：[`docs/operations/scaling-readiness.md`](docs/operations/scaling-readiness.md)
- 基础设施状态：[`docs/operations/infrastructure-status.md`](docs/operations/infrastructure-status.md)

生产覆盖：`PMS_JWT_SECRET`、`PMS_ACCESS_EXPIRE_MINUTES`、`PMS_REFRESH_EXPIRE_DAYS`、`PMS_PASSWORD_RESET_EXPOSE_TOKEN=false`、`PMS_INVITATION_EXPOSE_TOKEN=false`。
