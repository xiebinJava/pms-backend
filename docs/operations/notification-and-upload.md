# 通知与上传生产配置

## 通知器

邀请激活和密码重置均通过 `PasswordResetNotifier` / `InvitationNotifier` 抽象发送。默认不返回原始令牌；生产环境应配置 SMTP（后续也可替换为飞书或企业微信适配器），并设置：

```bash
export PMS_NOTIFICATION_STARTUP_CHECK=true
export PMS_MAIL_ENABLED=true
export PMS_MAIL_HOST=smtp.example.com
export PMS_MAIL_PORT=587
export PMS_MAIL_USERNAME='pms@example.com'
export PMS_MAIL_PASSWORD='只注入运行环境，不要提交'
export PMS_MAIL_FROM='pms@example.com'
export PMS_PUBLIC_BASE_URL='https://pms.example.com'
export PMS_PASSWORD_RESET_EXPOSE_TOKEN=false
export PMS_INVITATION_EXPOSE_TOKEN=false
```

生产配置默认开启启动检查；如果找回密码或邀请没有通知器，或者 SMTP 主机、端口、认证凭据、发件人缺失，后端会在启动阶段失败，避免生成无法送达的令牌。生产 SMTP 的 `PMS_PUBLIC_BASE_URL` 必须是 HTTPS 绝对地址。未知账号的找回请求仍返回通用结果，不泄露账号是否存在。邮件正文和日志不会输出令牌哈希或密码。

开发环境若确实没有邮件服务，可显式设置 `PMS_NOTIFICATION_STARTUP_CHECK=false`，并设置 `PMS_PASSWORD_RESET_EXPOSE_TOKEN=true` / `PMS_INVITATION_EXPOSE_TOKEN=true`，只在本地调试使用。

## 文件上传

项目图片统一经 `FileStorageService` 保存。`LocalFileStorageService` 具备以下边界：

- 只接受 PNG、JPEG、GIF、WEBP，读取文件魔数并校验声明的 MIME，不能靠扩展名伪造。
- 单文件不超过 5MB；目录总配额由 `PMS_UPLOAD_QUOTA_BYTES` 控制，默认 500MB。
- 原始文件名只用于界面展示，存储键使用随机 UUID，不会写入路径；读取时拒绝路径穿越。
- 默认目录为 `/var/lib/pms/uploads`，Compose 使用独立 `pms-uploads` 持久卷；容器重建不会丢失图片。
- `pms-uploads` 卷必须纳入宿主机/卷级备份；数据库恢复和图片卷恢复应作为同一批次演练，否则图片 URL 可能存在但文件缺失。

对象存储接入时只需实现 `FileStorageService` 并替换本地 Bean，无需修改项目图片接口。
默认 `PMS_STORAGE_TYPE=local`。设为 `s3` 时使用 S3 兼容实现（内网 MinIO 或云厂商桶），
并注入 `PMS_S3_ENDPOINT`、`PMS_S3_BUCKET`、`PMS_S3_ACCESS_KEY`、`PMS_S3_SECRET_KEY`。
`PMS_S3_PATH_STYLE` 默认 true，适配 MinIO。对象存储侧的容量由桶策略管理，本地配额不生效。

## 出站 Webhook

任务指派、评论、节点完成和节点回滚在写入站内收件箱后，可额外向企业自己的接收地址 POST 一条 JSON。项目评论和节点事件会发给项目经理、相关节点负责人和关注人。
默认关闭。打开 `PMS_WEBHOOK_ENABLED=true` 后必须配置 `PMS_WEBHOOK_URL` 与至少 16 位的
`PMS_WEBHOOK_SECRET`。生产环境 URL 必须是 HTTPS。请求头包含 `X-PMS-Event`、
`X-PMS-Delivery` 和 `X-PMS-Signature: sha256=<HMAC-SHA256(body)>`。
接收方应以 2xx 应答；非 2xx 或超时只写日志，不回滚站内通知。

事件 JSON 会同时携带 `projectId`、`taskId` 和 `nodeId`（没有对应对象时为 `null`）。
`NODE_COMPLETED` 与 `NODE_ROLLED_BACK` 必须使用 `nodeId` 定位项目流程节点，接收方可用它生成节点详情深链；
旧事件类型仍按原字段处理。`recipientIds` 是站内通知收件人 ID 列表，不代表 Webhook 接收方权限。

## 任务临期/逾期提醒

这是现有站内通知的定时补充，不是邮件或 Webhook 推送。配置默认关闭，权限收口和通知 SQL 可见性验收完成后才允许打开：

~~~yaml
pms:
  notification:
    task-reminder:
      enabled: false
      cron: "0 0 9 * * *"
      zone: Asia/Shanghai
      due-soon-days: 7
~~~

也可以使用环境变量 PMS_NOTIFICATION_TASK_REMINDER_ENABLED、PMS_NOTIFICATION_TASK_REMINDER_CRON、PMS_NOTIFICATION_TASK_REMINDER_ZONE 和 PMS_NOTIFICATION_TASK_REMINDER_DUE_SOON_DAYS 覆盖。提前天数必须是 1–30；非法时区、cron 或提前天数会阻止应用启动。

启用前检查：

1. 项目权限收口已发布并验收：具备 project:read 的账号能读全公司未删除项目及其任务、节点、评论、附件等内容；没有该权限的账号不能进入通知中心。
2. V15 已执行，user_notification.dedupe_key 可空字段和 uk_user_notification_dedupe 唯一索引存在；旧通知的 dedupe_key 保持 NULL。
3. /notifications、/notifications/page、/notifications/unread-count 和顶部预览都已经验证删除项目过滤是在 SQL 中完成，不存在先取固定数量再在应用层丢弃导致缺页/错总数。
4. 先在测试账号上手动执行作业服务测试，确认不调用 WebhookPublisher；第一期提醒只能出现在站内通知中。

调度和候选规则：

- 每天当地 09:00 计算 today。默认临期窗口是 [today, today + 7]，但只有 dueDate = today + 7 的任务写入一次临期通知；dueDate = today 当天不写临期。
- 只有 dueDate = today - 1 的未完成任务写入逾期通知；历史逾期、首日历史欠账、停机漏跑不补发。
- 新建、改期、重开任务只有在当天恰好位于上述边界时才可能写入；窗口内部不补发。
- 只处理当前 assigneeId，过滤无负责人、DONE、已删除任务、已删除/终止项目和停用/逻辑删除员工。终止项目的历史通知仍可读，但终止后不再生成新提醒；删除项目通知不进列表和未读数。
- 临期去重键是 TASK_DUE_SOON:taskId:dueDate；逾期去重键是 TASK_OVERDUE:taskId:today。重复执行不会增加通知；换负责人不撤回旧通知、不改旧通知已读状态。

作业日志只应包含配置时区下的扫描日期、扫描结果和数量，不输出密码、令牌、Webhook secret 或通知正文。宿主机时区不是 Asia/Shanghai 时不影响作业日期；工作台 dueSoon 本期仍按 JVM 默认时区，午夜附近可能与通知相差一天。

## 运维检查

1. 生产启动前确认上传目录由独立卷挂载且权限仅授予后端进程。
2. 容器重建后访问一张旧图片，确认持久卷可见。
3. 上传伪造 MIME、双扩展名、路径穿越和超配额文件，均应返回安全业务错误，不返回服务器真实路径。
4. SMTP 配置变更后重启后端，确认通知器启动检查通过，再执行邀请和密码重置演练。
5. 备份时同步快照 `pms-uploads` 卷；恢复到临时环境后同时校验数据库记录和图片可读性。
