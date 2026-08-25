# PMS Backend — 项目管理系统后端

基于 Spring Boot + MyBatis-Plus 的开源项目管理系统后端，采用 Cmd/Qry/DTO + Service 编排的 CQRS 风格分层，单模块组织，便于开源维护。

## 技术栈

- Java 17 + Maven
- Spring Boot 2.7.5
- MyBatis-Plus 3.4.1（分页插件 + 公共字段自动填充）
- MySQL 8 / H2（开发环境内存库，开箱即用）
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

默认使用 **H2 内存库**，无需任何外部依赖：

```bash
mvn spring-boot:run
# 或
mvn package -DskipTests && java -jar target/pms-backend-0.1.0.jar
```

启动后访问 `http://localhost:8080/api`，H2 控制台：`http://localhost:8080/api/h2-console`。

### 使用 MySQL

```sql
CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4;
```

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql \
  -Dspring-boot.run.arguments="--MYSQL_HOST=localhost --MYSQL_PORT=3306 --MYSQL_DB=pms --MYSQL_USER=root --MYSQL_PASSWORD=root"
```

## 默认账号

| 用户名 | 密码 |
| --- | --- |
| admin | admin123 |
| zhangsan / lisi / wangwu | admin123 |

首次启动会自动初始化种子数据（2 个项目、6 个任务、2 个里程碑、4 名成员、1 条动态）。

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

除登录外所有接口需携带请求头：`Authorization: Bearer <token>`。

## 配置

关键配置在 `application.yml`，JWT 密钥可通过环境变量覆盖：

```
PMS_JWT_SECRET=<生产环境请设置强随机密钥>
```
