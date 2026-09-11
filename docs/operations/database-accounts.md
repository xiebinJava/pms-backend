# MySQL 账号职责

PMS 采用单企业、单 MySQL 实例部署。Compose 用 `MYSQL_ROOT_PASSWORD` 初始化实例，并用 `MYSQL_USER` / `MYSQL_PASSWORD` 创建应用账号。后端启动时由 Flyway 执行迁移，不再使用独立的 migrator 镜像。

## 账号职责

| 账号 | 使用场景 | 权限 |
| --- | --- | --- |
| MySQL `root` | 首次初始化、逻辑备份、隔离恢复 | 不注入 backend |
| `MYSQL_USER`（默认 `pms`） | backend 日常运行和 Flyway 迁移 | 业务库 `pms` 上的 DML 与迁移所需 DDL |

不要把 `MYSQL_ROOT_PASSWORD` 写入 backend 环境变量。生产环境的 `MYSQL_USER` 不能是 `root`。

## 首次初始化

生成独立的强密码（建议使用 `openssl rand -hex 32`），只写入本地 `.env.mysql.local` 或密钥管理系统：

```bash
cp .env.mysql.example .env.mysql.local
# 编辑 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD、PMS_JWT_SECRET、PMS_BOOTSTRAP_ADMIN_PASSWORD
docker compose --env-file .env.mysql.local -f docker-compose.example.yml up --build
```

Compose 启动顺序为：MySQL → `uploads-init` → backend（Flyway）→ frontend。

## 已有数据库

已有 `pms` 数据库时，先备份再启动新版本后端。不要删除或重建业务库。

```bash
./scripts/backup-mysql.sh
./scripts/enterprise-preflight.sh
```
