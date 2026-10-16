# Story Node Orchestrator 真实验收

这份验收覆盖外部 Agent 通过 `pms-cli` 推进故事当前节点的完整闭环：读取
focused context、生成 Agent Draft、发现非法字段、进入 `NEEDS_INPUT`、预览、
确认执行、回读验证、同幂等键重放、独立 CLI 进程恢复以及旧版本冲突。

夹具说明：新故事默认停在无必填字段的 `writing` 节点，只有 `development`
变体声明了必填写字段。夹具通过既有命令链
（`development-item.node.owner.update` → `development-item.node.schedule.update`
→ `development-item.node.complete`）推进到 development 节点后再进入正式
验收，不引入任何新的写入协议。

## 安全前提

真实测试必须使用一次性数据库和明确的目标白名单。除了既有隔离配置外，还必须
提供只读数据库连接，供 `operationAudit.ts` 查询已经存在的
`pms_ai_operation` 表：

```bash
export PMS_E2E_STORY_ORCHESTRATOR=true
export PMS_E2E_WRITE=true
export PMS_E2E_ISOLATED=true
export PMS_E2E_CREATE_STORY_FIXTURE=true
export PMS_E2E_DB_URL='mysql://readonly_user:password@127.0.0.1:13306/pms_ai_e2e_<run-id>'
```

仍需设置 `PMS_E2E_BASE_URL`、`PMS_E2E_ALLOWED_BASE_URL`、
`PMS_E2E_AUTH_ORIGIN`、`PMS_E2E_DATABASE_NAME`、
`PMS_E2E_ALLOWED_DATABASE_NAME`、`PMS_E2E_ACCOUNT_ID` 和
`PMS_E2E_RUN_ID`。如果不创建夹具，可改为设置 `PMS_E2E_STORY_ID`。

`PMS_E2E_DB_URL` 必须使用只读账号；查询只按本次 run 的时间窗口和唯一标记匹配，
不会写数据库，也不会新增生产查询接口。

## 运行

```bash
cd /Users/fs/Desktop/Project/pms-ai-connector
pnpm run verify:real-pms:story-orchestrator --maxWorkers=1 --minWorkers=1
```

缺少真实验收开关、隔离目标、账号、故事夹具或数据库连接时，命令会失败；普通
`pnpm test` 不会执行该真实写入测试。

故事能力目录当前没有声明 `story.delete`，因此夹具 ID 会被记录并随一次性隔离
数据库销毁。测试不会猜测删除接口，也不会直接修改业务数据库。

## 通过标准

- `NEEDS_INPUT` 不生成 preview，也不调用 execute；同一会话内分轮补充字段时，
  服务端合并前一 Plan 的有效 patch 与本轮 patch，最终生成完整 `PLAN_READY`。
- 合法草稿必须经过 `PLAN_READY -> WAITING_CONFIRMATION -> EXECUTING -> VERIFYING -> SUCCEEDED`。
- 回读必须证明资源、上下文、保护字段和工作台 patch 均符合预期。
- 回读无法证明结果时必须进入 `WRITE_RESULT_UNKNOWN`，禁止把操作响应直接当成成功。
  该场景依赖伪造真实回读失败，无法从只读验收账号安全诱导，因此真实环境验收不
  直接触发它；其转移规则由 Backend 单测
  （`AiOrchestratorVerificationServiceTest`、`AiOrchestratorServiceTest`）与
  状态机测试覆盖。
- 同幂等键重放返回相同 operation，不产生第二次业务写入；同一 Operation 使用
  不同幂等键返回 409 冲突。
- 第二个客户端（代表新的 CLI 进程，不携带任何本地 plan/operation 状态）只凭
  conversation/session 读取到 operation、verification 和事件游标。
- 非法字段、无效 sourceRef 和旧 context version 返回错误，不创建新的写操作。
- 预览之后节点版本被并发修改时，旧 preview 无法强行写入：confirm 返回计划
  `CONFLICT` 状态（同步响应体内呈现，不是新增一次写操作）；409 冲突码仍在
  同一 Operation 使用不同幂等键重放时返回。
- 只读操作审计恰好包含一次 `development-item.node.field.update` 成功记录。
  无权限 relation 的拒绝由 Backend 单测与契约测试覆盖（隔离账号为管理员，
  无法在只读验收中构造授权差异）。
