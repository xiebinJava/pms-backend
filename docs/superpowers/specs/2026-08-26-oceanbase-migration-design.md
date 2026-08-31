# PMS OceanBase 数据迁移设计

## 目标

将 PMS 从当前默认的 H2 内存数据库迁移到 OceanBase 的 MySQL 兼容模式，目标数据库固定为 `brad_pms`，并保留当前运行实例中的项目、节点、任务、人员、里程碑、动态及权限相关数据。

## 当前状态

- PMS 默认连接 `jdbc:h2:mem:pms`，数据只存在于当前后端 JVM 的内存中。
- 本机 OceanBase SQL 端口为 `2881`，使用 MySQL Connector/J 连接。
- 现有 `schema.sql` 使用 H2/MySQL 兼容语法，包含 9 张业务表及索引。
- `DataInitializer` 仅在 `sys_user` 为空时创建演示数据，因此导入数据后重启不会重复插入种子数据。

## 推荐方案

采用“运行中 H2 快照 + 空目标库导入 + 行数校验”的方案：

1. 保持当前 PMS 后端运行，先通过 H2 Console 执行 `SCRIPT TO` 导出当前内存库。
2. 从本机已有 OceanBase 配置中读取连接参数，仅在进程环境中使用，不写入仓库、不输出密码。
3. 连接 OceanBase 做预检查：验证连接、查询 `brad_pms` 是否存在以及业务表是否为空。
4. 目标库不存在时创建 `brad_pms`；目标库存在且非空时立即停止，不覆盖已有数据。
5. 在 `brad_pms` 中执行现有 schema，并将 H2 快照中的数据按依赖顺序导入，保留原始 ID。
6. 对 9 张业务表逐表比较源快照和目标库行数，同时检查项目、节点、任务的关键外键关系。
7. 通过 `oceanbase` profile 启动 PMS，验证登录、项目详情、节点、任务看板和权限接口。

## 配置规范

新增 `application-oceanbase.yml`，只允许从以下环境变量读取连接信息：

- `OCEANBASE_HOST`，默认 `127.0.0.1`
- `OCEANBASE_PORT`，默认 `2881`
- `OCEANBASE_DATABASE`，默认 `brad_pms`
- `OCEANBASE_USER`
- `OCEANBASE_PASSWORD`

OceanBase profile 禁用 H2 Console，避免生产配置暴露调试入口；默认 profile 仍保留 H2，便于本地开发和失败回退。

## 数据安全规则

- 不执行 `DROP DATABASE`、`TRUNCATE` 或覆盖已有业务数据。
- 非空目标库不自动合并，必须停止并由用户决定下一步。
- 导入前保留 H2 快照文件；迁移成功前不停止现有 H2 后端。
- 数据导入失败时保留失败现场和日志，不自动删除目标库。
- 账号密码只能来自运行时环境或本机已有配置，不能进入 YAML、脚本输出、Git 历史或最终报告。

## 验证标准

- `brad_pms` 中 9 张业务表均存在。
- 每张表的导入行数与 H2 快照一致。
- `project_member`、`project_follower`、`project_task`、`project_milestone`、`project_comment`、`project_node` 的关联记录完整。
- 后端使用 `oceanbase` profile 启动成功，前端可以正常读取项目详情。
- 项目创建人、项目经理、节点负责人、任务负责人相关权限行为与迁移前一致。
