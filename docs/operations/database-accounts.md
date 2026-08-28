# OceanBase 账号职责

PMS 采用单企业、单 OceanBase 租户部署，但不让应用使用 `root@sys`。账号初始化脚本只负责创建账号和授予权限，业务数据不会被删除或覆盖。

## 账号职责

| 账号 | 使用场景 | 权限 |
| --- | --- | --- |
| `root@sys` | DBA 人工维护、首次账号初始化 | OceanBase 系统管理权限，不注入 backend |
| `pms_migrator` | schema-init 和版本升级任务 | `brad_pms.*` 上的迁移 DDL 与必要 DML |
| `pms_app` | backend 日常运行 | `brad_pms.*` 上的 `SELECT/INSERT/UPDATE/DELETE` |

`pms_app` 没有建表、改表结构、建索引或删除表权限。`pms_migrator` 不应作为长期应用连接账号使用，迁移任务完成后应退出或停止。

## 首次初始化

生成三个独立的强密码（建议使用 `openssl rand -hex 32`），只写入本地 `.env.oceanbase.local` 或密钥管理系统：

```bash
cp .env.oceanbase.example .env.oceanbase.local
# 编辑 OCEANBASE_ROOT_PASSWORD、PMS_APP_PASSWORD、PMS_MIGRATOR_PASSWORD
docker compose --env-file .env.oceanbase.local -f docker-compose.example.yml up --build
```

Compose 启动顺序为：OceanBase → `accounts-init` → `schema-init` → backend → frontend。重复运行账号初始化是幂等的；只有显式设置 `PMS_ROTATE_ACCOUNT_PASSWORDS=true` 才会轮换已存在账号的密码。

## 已有数据库切换

已有 `brad_pms` 数据库时，先执行只读预检，再运行 `accounts-init` 创建账号，最后用 `pms_app` 启动 backend。不要把 `OCEANBASE_ROOT_PASSWORD` 写入 backend 的环境变量，也不要在迁移脚本中保存任何密码。

```bash
PMS_DB_USER=pms_app PMS_DB_PASSWORD="$PMS_APP_PASSWORD" \
  bash scripts/enterprise-preflight.sh

docker compose --env-file .env.oceanbase.local -f docker-compose.example.yml run --rm accounts-init
```

完成后用以下命令验证权限边界：

```bash
PMS_APP_PASSWORD="$PMS_APP_PASSWORD" \
PMS_MIGRATOR_PASSWORD="$PMS_MIGRATOR_PASSWORD" \
OCEANBASE_ROOT_PASSWORD="$OCEANBASE_ROOT_PASSWORD" \
  bash scripts/check-oceanbase-privileges.sh
```

OceanBase 当前版本不支持 MySQL 临时表，因此脚本会创建一个带时间戳的权限探针表，再使用 root 清理；探针不会写入业务数据。
