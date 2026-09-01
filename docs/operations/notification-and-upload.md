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

任务指派和评论在写入站内收件箱后，可额外向企业自己的接收地址 POST 一条 JSON。
默认关闭。打开 `PMS_WEBHOOK_ENABLED=true` 后必须配置 `PMS_WEBHOOK_URL` 与至少 16 位的
`PMS_WEBHOOK_SECRET`。生产环境 URL 必须是 HTTPS。请求头包含 `X-PMS-Event`、
`X-PMS-Delivery` 和 `X-PMS-Signature: sha256=<HMAC-SHA256(body)>`。
接收方应以 2xx 应答；非 2xx 或超时只写日志，不回滚站内通知。

## 运维检查

1. 生产启动前确认上传目录由独立卷挂载且权限仅授予后端进程。
2. 容器重建后访问一张旧图片，确认持久卷可见。
3. 上传伪造 MIME、双扩展名、路径穿越和超配额文件，均应返回安全业务错误，不返回服务器真实路径。
4. SMTP 配置变更后重启后端，确认通知器启动检查通过，再执行邀请和密码重置演练。
5. 备份时同步快照 `pms-uploads` 卷；恢复到临时环境后同时校验数据库记录和图片可读性。
