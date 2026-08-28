# PMS Backend — 项目管理系统后端

基于 Spring Boot + MyBatis-Plus 的开源项目管理系统后端，采用 Cmd/Qry/DTO + Service 编排的 CQRS 风格分层，单模块组织，便于开源维护。

## 技术栈

- Java 17 + Maven
- Spring Boot 2.7.5
- MyBatis-Plus 3.4.1（分页插件 + 公共字段自动填充）
- OceanBase（MySQL 兼容模式）
- JWT（jjwt）轻量登录鉴权，无第三方权限平台依赖
- Lombok

## 模块与分层

```
com.brad.pms
├── common      统一返回/分页/异常/枚举
├── config      MyBatis-Plus、CORS、种子数据
├── security    JWT 生成与解析、登录拦截器、用户上下文
├── controller  REST 接口
├── service     业务编排（create/update/page 等用例）
├── mapper      MyBatis-Plus Mapper
├── entity      DataObject
├── dto         request（Cmd/Qry）/ response（DTO）
└── convertor   实体 <-> DTO 转换
```

## 快速启动

默认使用本机 **OceanBase**（MySQL 兼容模式）。请先准备 `brad_pms` 数据库并注入连接账号：

```bash
export OCEANBASE_HOST=127.0.0.1
export OCEANBASE_PORT=2881
export OCEANBASE_DATABASE=brad_pms
export OCEANBASE_USER=<业务账号>
export OCEANBASE_PASSWORD=<业务密码>

mvn spring-boot:run
# 或
mvn package -DskipTests && java -jar target/pms-backend-0.1.0.jar
```

启动后访问 `http://localhost:8080/api`。OceanBase profile 不在运行时启用 Flyway（OceanBase 4.x 对外报告 MySQL 5.7，而 Flyway Community 不支持该版本）；首次部署或升级请先执行仓库中的迁移脚本，再启动应用。

### Docker Compose 快速启动

复制示例环境变量并设置真实的 OceanBase 密码和随机 JWT 密钥（不要提交 `.env`）：

```bash
cp .env.oceanbase.example .env
# 编辑 .env：至少设置 OCEANBASE_PASSWORD 和 32 字节以上 PMS_JWT_SECRET
docker compose -f docker-compose.example.yml up --build
```

Compose 会先等待 OceanBase，再在空库执行核心表和企业迁移，最后启动后端与前端。前端地址为
`http://localhost:5173`，健康检查为 `http://localhost:8080/api/health`。初始化服务只使用
`pms_schema_bootstrap_marker` 做一次性标记；已有业务库请按升级手册执行预检和迁移，不要直接套用示例 Compose。

自动化测试使用独立的嵌入式测试数据库，不会改变 OceanBase 数据：

```bash
mvn test
```

### 使用 OceanBase

PMS 使用 OceanBase 的 MySQL 兼容模式，默认连接本机 `2881` 端口和 `brad_pms` 数据库。账号密码只通过运行时环境注入，不要写入仓库或启动日志：

```bash
export OCEANBASE_HOST=127.0.0.1
export OCEANBASE_PORT=2881
export OCEANBASE_DATABASE=brad_pms
export OCEANBASE_USER=<业务账号>
export OCEANBASE_PASSWORD=<业务密码>

mvn spring-boot:run -Dspring-boot.run.profiles=oceanbase
```

当前本地 PMS 数据已迁移至 `brad_pms`。导入器中的 H2 快照处理仅用于一次性历史迁移，不参与应用运行。

## 开发账号与生产初始化

| 用户名 | 密码 |
| --- | --- |
| admin | admin123 |
| zhangsan / lisi / wangwu | admin123 |

上表仅在自动化测试配置用于种子数据。OceanBase 环境不要使用共享默认密码；空库首次启动前请注入
`PMS_BOOTSTRAP_ADMIN_USERNAME`、`PMS_BOOTSTRAP_ADMIN_NAME_ZH` 和至少 12 位的
`PMS_BOOTSTRAP_ADMIN_PASSWORD`。管理员随后通过邀请或 Excel/CSV 导入员工。

所有用户展示为 `中文名（English.Name）`，登录使用不区分大小写的英文名。每名员工有一个主归属，可拥有多个兼职/项目归属。

## 核心接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 登录，返回 JWT |
| GET | `/auth/me` | 当前用户 |
| GET | `/users/search?keyword=` | 用户搜索 |
| POST | `/projects/page` | 项目分页查询（keyword/status） |
| POST | `/projects` | 新建项目 |
| GET/PUT/DELETE | `/projects/{id}` | 项目详情/更新/删除 |
| GET/POST | `/projects/{id}/tasks` | 任务列表/新建 |
| PUT/DELETE | `/tasks/{id}` | 任务更新/删除 |
| PUT | `/tasks/{id}/move` | 任务拖拽改状态 |
| GET/POST | `/projects/{id}/milestones` | 里程碑列表/新建 |
| GET/POST | `/projects/{id}/members` | 成员列表/添加 |
| GET/POST | `/projects/{id}/comments` | 动态列表/发布 |
| GET/POST/PUT/DELETE | `/admin/org/tree`、`/admin/org`、`/admin/org/{id}` | 组织树与组织单元维护 |
| GET/POST | `/admin/users`、`/admin/users/{id}/disable` | 人员、邀请、禁用与会话撤销 |
| GET/POST/PUT/DELETE | `/admin/roles`、`/admin/roles/{id}` | 角色、权限点和数据范围 |
| POST | `/admin/import/preview/organizations`、`/admin/import/preview/users`、`/admin/import/{id}/commit` | Excel/CSV 组织与员工导入 |
| GET | `/admin/audit` | 审计日志筛选和分页（操作、资源、人员、时间、`currPage`、`pageSize`） |
| GET | `/health`、`/healthz` | 应用与 OceanBase 数据库健康检查（无需登录） |

除登录外所有接口需携带请求头：`Authorization: Bearer <token>`。

## 配置

关键配置在 `application.yml`，JWT 密钥可通过环境变量覆盖：

```
PMS_JWT_SECRET=<生产环境请设置强随机密钥>
```

其他企业部署参数：`PMS_ACCESS_EXPIRE_MINUTES`、`PMS_REFRESH_EXPIRE_DAYS`、
`PMS_PASSWORD_RESET_EXPOSE_TOKEN=false`。完整备份、Flyway 迁移、预检和验收步骤见
[`docs/operations/enterprise-upgrade-runbook.md`](docs/operations/enterprise-upgrade-runbook.md)。
正式发布前请逐项执行 [`docs/operations/release-checklist.md`](docs/operations/release-checklist.md)。

业务不变量、数据关系、权限/数据范围、认证、导入和 OceanBase 约束统一沉淀在
[`docs/business-specification.md`](docs/business-specification.md)。

企业级基础设施（数据库账号隔离、备份恢复、升级、容器、可观测性、CI 和开源交付）的当前状态见
[`docs/operations/infrastructure-status.md`](docs/operations/infrastructure-status.md)，逐项执行计划见
[`docs/superpowers/plans/2026-08-28-enterprise-infrastructure-hardening.md`](docs/superpowers/plans/2026-08-28-enterprise-infrastructure-hardening.md)。
