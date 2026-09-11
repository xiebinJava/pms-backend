# PMS Helm Chart

把后端和前端部署到已有集群。**不会安装 MySQL。** 请把 `backend.env.mysqlHost` 指向你已经运行的 MySQL 8。后端首次启动时由 Flyway 执行迁移。

单机环境请继续使用 `docker-compose.example.yml`。本 chart 是可选的。

## 镜像

自行构建并加载到集群：

```bash
docker build -t pms-backend:1.0.0 .
docker build -t pms-front:1.0.0 ../pms-front
```

## 安装

先创建包含 `jwtSecret`、`mysqlPassword`、`bootstrapAdminEmail`、`bootstrapAdminPassword`、`bootstrapAdminNameZh`、`bootstrapAdminUsername` 的 Secret，然后：

```bash
helm upgrade --install pms deploy/helm/pms \
  --set existingSecret=pms-secrets \
  --set backend.env.mysqlHost=mysql.example.com \
  --set backend.env.corsAllowedOrigins=https://pms.example.com \
  --set backend.env.publicBaseUrl=https://pms.example.com
```

前端镜像会把 `/api` 代理到名为 `backend` 的 Service。同一个命名空间里只安装一份，除非你重新构建 Nginx。

## 指标

Actuator 留在 ClusterIP 管理端口（`8081`）。不要把该 Service 挂到 Ingress。

如果集群里已经有 prometheus-operator：

```bash
helm upgrade --install pms deploy/helm/pms \
  --set existingSecret=pms-secrets \
  --set serviceMonitor.enabled=true
```
