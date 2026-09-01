# PMS Helm Chart

Deploys the backend and frontend into an existing cluster. **It does not install OceanBase.** Point `backend.env.oceanbaseHost` at a MySQL-compatible instance you already run, then apply schema with `./scripts/oceanbase-upgrade.sh` before the first start.

Single-node hosts should keep using `docker-compose.example.yml`. This chart is optional.

## Images

Build them yourself and load them into the cluster:

```bash
docker build -t pms-backend:1.0.0 .
docker build -t pms-front:1.0.0 ../pms-front
```

## Install

Create a Secret with `jwtSecret`, `oceanbasePassword`, `bootstrapAdminEmail`, `bootstrapAdminPassword`, and `bootstrapAdminNameZh`. Then:

```bash
helm upgrade --install pms deploy/helm/pms \
  --set existingSecret=pms-secrets \
  --set backend.env.oceanbaseHost=oceanbase.example.com \
  --set backend.env.corsAllowedOrigins=https://pms.example.com \
  --set backend.env.publicBaseUrl=https://pms.example.com
```

The frontend image proxies `/api` to a Service named `backend`. Install only one release per namespace unless you rebuild nginx.

## Metrics

Actuator stays on a ClusterIP management port (`8081`). Do not put that Service on Ingress.

If the cluster already has prometheus-operator:

```bash
helm upgrade --install pms deploy/helm/pms \
  --set existingSecret=pms-secrets \
  --set serviceMonitor.enabled=true
```

Local Compose can overlay Prometheus and Grafana without Kubernetes. See the repository README.
