# OceanBase 备份恢复演练记录（2026-08-31）

## 范围

本次仅针对本机已有 `brad_pms` 做只读连通性与迁移验收预检；不覆盖、不删除现有数据库，也不创建生产恢复目标库。

## 已执行

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 读取 `.env.oceanbase.local` | 通过 | 仅确认变量名存在，未输出任何密码或 JWT |
| 通过已有 OceanBase 容器执行 `SELECT 1` | 通过 | `fsclaw-oceanbase` 内 `/usr/bin/obclient` 返回 `1` |
| `enterprise-preflight.sh` | 阻塞 | 主机未安装 `mysql`/`obclient`，脚本尝试拉取 `mysql:8.4`；Docker Hub 请求超时 |
| `verify-enterprise-migration.sh` | 未执行 | 同一 SQL 客户端依赖缺口；避免在未完成预检时误报 |
| `backup-oceanbase.sh` | 未执行 | 当前容器没有 `mysqldump`，`mysql:8.4` 镜像未缓存且 Docker Hub 不可达 |
| 空库恢复与故障切换 | 未执行 | 没有明确的空目标库和可用 dump 工具，不触碰 `brad_pms` |

## 阻塞与后续动作

1. 在具备 Docker Hub 镜像缓存或已安装 MySQL/OceanBase 客户端的部署机执行：
   `enterprise-preflight.sh`、`verify-enterprise-migration.sh`。
2. 设置权限为 `700` 的备份目录执行 `backup-oceanbase.sh`，保存 `.sha256` 与 `.meta`。
3. 仅创建明确的空库（例如 `brad_pms_restore`）后执行 `restore-oceanbase.sh --allow-empty-target`，再重复迁移验收。
4. 记录备份文件名、SHA-256、schema 版本、恢复耗时和就绪探针响应；禁止在记录中写入密码、JWT 或 Token。

本记录不代表备份恢复已通过；在上述阻塞解除前，发布清单中的备份、恢复和故障演练项目保持未勾选。
