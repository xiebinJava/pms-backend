# 本地固化默认版本测试

固化读取流程类型当前默认的已发布版本；它不会发布草稿、切换默认版本或迁移已有事项。
只有备份恢复验证成功后才在当前环境测试。不要清空数据库，不要执行 `docker compose down -v`。

## 1. 备份

在后端仓库目录执行以下命令。数据库密码只在容器内部读取，不写入命令输出。

```sh
task_backup_dir=$(mktemp -d /tmp/pms-default-backup.XXXXXX)
docker exec pms-backend-mysql-1 sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --events --hex-blob --no-tablespaces "$MYSQL_DATABASE"' > "$task_backup_dir/database.sql"
test -s "$task_backup_dir/database.sql"
cp -R src/main/resources/workflow-defaults "$task_backup_dir/workflow-defaults"
docker cp pms-backend-backend-1:/var/lib/pms/uploads "$task_backup_dir/uploads"
echo "$task_backup_dir"
```

检查每一步退出码。把备份复制到持久保存的位置，再将 SQL 恢复到单独的 MySQL 实例核对项目、需求、任务、节点和模板数据。不要把恢复命令指向当前数据库。

## 2. 开启本地能力

默认配置不开启源码写入。新增的 `docker-compose.workflow-defaults.local.yml` 是显式本地覆盖文件。
它只挂载 `src/main/resources/workflow-defaults`，容器进程 UID 10001 必须对这个目录有写权限。
如果目录不可写，页面不会展示固化按钮。可在独立仓库副本中配置该目录权限并测试，避免修改当前源码文件。

```sh
docker compose --env-file .env.mysql.local -f docker-compose.example.yml -f docker-compose.workflow-defaults.local.yml up -d --build backend
```

此命令不删除数据卷。进行隔离测试时使用独立的 Compose 项目、数据库卷和端口，不能直接复用示例配置里的固定 MySQL 数据卷名。

## 3. 验证固化

1. 打开流程模板 → 需求管理 → 当前默认模板，记录默认版本号。
2. 如果页面提示未保存，先决定是否保存/发布这些修改。固化只导出默认已发布版本；若希望固化新版本，发布后还需设为默认。
3. 没有未保存修改时点击“固化默认版本”，核对确认框中的版本号。
4. 成功后用 `git diff -- src/main/resources/workflow-defaults` 查看 JSON，版本号、节点、字段和工作台配置应匹配默认已发布版本。
5. 核对事项、节点填写内容、任务、模板版本及默认版本绑定均未改变；审计日志应新增一条固化记录。

## 4. 验证初始化

固化生成的是源码资源。必须重新打包/构建后，启动初始化才会读取更新的文件。

- 空测试库：出现固化的模板和节点配置。
- 已有测试库：已有已发布版本、默认绑定及填写内容保持不变。
- 同号草稿/归档版本：保留旧版本，初始化使用新的空闲版本号。
- 重复启动两次：不重复插入已发布模板版本。
- 写入开关关闭或生产环境：无法固化，原文件不变。

文件和数据库是不同存储介质。普通事务失败会恢复旧文件，但进程强制退出/机器断电无法保证跨介质原子性；保留 JSON 和数据库备份。
