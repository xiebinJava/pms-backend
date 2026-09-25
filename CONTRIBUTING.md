# 贡献指南

PMS 是单企业、自部署的项目管理系统。后端和前端分别在兄弟仓库中维护。

## 环境要求

- `pms-backend`：Java 17 和 Maven
- `pms-front`：Node.js 22 和 pnpm 9.15.9
- 本地与生产运行时：通过 `docker-compose.example.yml` 或 `./scripts/start-local-mysql.sh` 使用 MySQL 8
- 自动化测试使用 Testcontainers MySQL 8，不支持 H2

Colima 用户在执行 `mvn test` 前请先导出：

```bash
export DOCKER_HOST=unix://$HOME/.colima/<profile>/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1
```

## 提交 Pull Request 之前

```bash
# 后端
./scripts/check-privacy.sh
./scripts/validate-openapi.sh
mvn -q test

# 前端
./scripts/check-privacy.sh
pnpm test
pnpm typecheck
pnpm build
```

## 约定

- 不要加入公司品牌名、内部主机名或真实员工身份。演示账号使用 `张伟` / `Alex.Zhang` / `alex.zhang@example.com`。
- 密钥、JWT 和数据库密码不要进入 Git。
- 权限校验留在服务端；前端只负责隐藏控件。
- 沿用现有 CQRS 命名：Cmd / Qry / DTO / Service。
- OIDC / LDAP 必须放在 `PMS_OIDC_ENABLED` / `PMS_LDAP_ENABLED` 后面。不要根据目录自动创建用户。
- 对象存储和 Webhook 必须放在 `PMS_STORAGE_TYPE=s3` / `PMS_WEBHOOK_ENABLED` 后面。默认使用本地磁盘，且不向外发送事件。
- Helm 以及 Prometheus / Grafana 叠加层是可选的。不要改默认 Actuator 绑定（`PMS_MANAGEMENT_ADDRESS=127.0.0.1`）。Chart 取值继续使用 `example.com` 主机和占位密钥。
