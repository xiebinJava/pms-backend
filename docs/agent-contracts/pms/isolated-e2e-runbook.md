# PMS AI 隔离 E2E 运行手册

这套脚本创建独立 MySQL 数据库、独立后端端口和独立 Docker volume。它不复用本地开发数据库 `pms`，结束时会删除本次 run 的后端进程、容器和 volume。

## 1. 准备一次性凭据

在当前 shell 中设置凭据；脚本不会把这些值写入仓库或标准输出：

```bash
export PMS_E2E_RUN_ID="run-$(date +%Y%m%d%H%M%S)"
export PMS_E2E_MYSQL_USER="pms_e2e"
export PMS_E2E_MYSQL_PASSWORD="<temporary-mysql-password>"
export PMS_E2E_MYSQL_ROOT_PASSWORD="<temporary-root-password>"
export PMS_E2E_JWT_SECRET="<at-least-32-character-temporary-secret>"
export PMS_E2E_ADMIN_EMAIL="pms-e2e@example.invalid"
export PMS_E2E_ADMIN_PASSWORD="<temporary-admin-password>"
export PMS_E2E_ADMIN_USERNAME="admin"
export PMS_E2E_ADMIN_NAME_ZH="AI E2E 管理员"
export PMS_E2E_MYSQL_PORT=13306
export PMS_E2E_API_PORT=18080
```

`PMS_E2E_RUN_ID` 只允许字母、数字、下划线和连字符；它同时用于数据库名、Docker project 和幂等键命名空间。

## 2. 启动隔离服务

```bash
cd pms-backend
./scripts/start-ai-e2e.sh
```

启动成功后，脚本会输出 `PMS_E2E_BASE_URL`、`PMS_E2E_AUTH_ORIGIN`、`PMS_E2E_DATABASE_NAME` 和 readiness 状态。将这些值导出给 Connector，并设置安全门：

```bash
export PMS_E2E_BASE_URL="http://127.0.0.1:${PMS_E2E_API_PORT}/api"
export PMS_E2E_ALLOWED_BASE_URL="$PMS_E2E_BASE_URL"
export PMS_E2E_AUTH_ORIGIN="http://127.0.0.1:${PMS_E2E_API_PORT}"
export PMS_E2E_DATABASE_NAME="pms_ai_e2e_${PMS_E2E_RUN_ID//-/_}"
export PMS_E2E_ALLOWED_DATABASE_NAME="$PMS_E2E_DATABASE_NAME"
export PMS_E2E_WRITE=true
export PMS_E2E_ISOLATED=true
```

`PMS_E2E_ACCOUNT_ID` 必须填写 BrowserAuth 登录后 capabilities 返回的实际 viewer ID。不能猜测或沿用共享开发账号 ID。

## 3. 验证服务再运行写入测试

先确认 API 只属于本次 run：

```bash
curl -sS "$PMS_E2E_BASE_URL/auth/me"
```

然后在 Connector 仓库运行：

```bash
cd pms-cli
PMS_E2E_CREATE_FIXTURE=true pnpm run verify:real-pms:analysis-save
```

真实测试必须通过 `isolatedE2EEnabled` 和 `assertIsolatedE2EConfig`；目标 URL、数据库名、用户身份任一不匹配，测试会在 BrowserAuth 之前跳过或失败。

## 4. 停止并销毁

无论测试成功还是失败，都执行：

```bash
cd pms-backend
./scripts/stop-ai-e2e.sh
```

脚本会停止后端、执行 `docker compose down -v` 并删除本次 run 目录。若清理失败，不得继续使用同一个 run ID 写入。
