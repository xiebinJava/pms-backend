# PMS 立项节点契约端到端验证记录

- 契约：`pms-project-assistant/project-kickoff`，`contractVersion=1.0.0`，`workflowNodeKeys=[kickoff]`
- 契约内容摘要（SHA-256）：`d05ae4761eda2a9fef22710fac733e8dd675b1e8e6916b17ea2115d047b2d090`（Phase 5 追加 `project.update` 后的当前版本；Phase 1–4 时为 `557dd9be…`）
- 执行时间：2026-09-21 14:28（Asia/Shanghai）
- PMS 后端：`4887031`（分支 `feature/configurable-project-workflows`，工作区含未提交的契约交付）
- DSH：`15e07a1`（分支 `codex/pms-dsh-capability-protocol`，工作区含未提交的契约交付）
- 驱动脚本：`/Users/fs/Desktop/Project/deepseek-harness/packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts`
- 原始证据：`docs/agent-contracts/pms/project-kickoff-e2e-evidence.json`（18 步，含每一步的时间、requestId、预览编号与返回内容）

## 结论

“契约加载 → Agent 注入 → 只读查询 → 预览确认 → 幂等写入 → 结果核验 → 统一刷新 → 节点完成判断”闭环已在真实 PMS 后端上跑通：真实登录、真实 DSH 委派 token、真实契约接口、真实命令预览与落库写入。过程中发现并修复了一处会导致“创建项目”预览必然失败的参数注入缺陷（见下文）。

## 环境与复现步骤

1. Docker（本机是 Colima，socket 不在默认位置）：

   ```bash
   export DOCKER_HOST="unix://$HOME/.colima/fsclaw/docker.sock"
   export TESTCONTAINERS_RYUK_DISABLED=true
   cd /Users/fs/Desktop/Project/pms-backend && bash scripts/start-local-mysql.sh
   ```

2. 启动后端（MySQL profile，读取 `.env.mysql.local`）：

   ```bash
   cd /Users/fs/Desktop/Project/pms-backend
   set -a && source .env.mysql.local && set +a
   java -jar target/pms-backend-1.0.7.jar --spring.profiles.active=mysql --spring.flyway.validate-on-migrate=false
   ```

3. 运行 live E2E（缺少 env 时该 spec 自动跳过，不影响默认测试套件）：

   ```bash
   cd /Users/fs/Desktop/Project/deepseek-harness
   set -a && source /Users/fs/Desktop/Project/pms-backend/.env.mysql.local && set +a
   PMS_E2E_BASE_URL=http://127.0.0.1:8080 \
   PMS_E2E_SERVICE_KEY="$PMS_DSH_SERVICE_KEY" \
   PMS_E2E_USER_EMAIL=e2e.kickoff@pms.com \
   PMS_E2E_USER_PASSWORD='KickoffE2e!2026' \
   PMS_E2E_SESSION_ID=pms-kickoff-e2e-live \
   PMS_E2E_RECORD_PATH=/tmp/pms-kickoff-e2e/evidence.json \
   ./node_modules/.bin/vitest run packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts
   ```

   结果：`6 passed`。

## 场景结果

| 场景 | 真实观察到的证据 | 结果 |
| --- | --- | --- |
| 只读查询 | `pms_project_list` 返回 21 个项目、其中 11 个处于 `kickoff`；`pms_project_get` 解析出 `currentNode.nodeKey=kickoff`（9 个节点、1 名成员）；`pms_task_list` 返回该项目真实任务数 | 通过 |
| 契约加载与必读注入 | system prompt 出现 `pms:agent-contract` section（1619 字符），含契约标识、`契约版本：1.0.0`、内容摘要、`当前节点：kickoff`、确认策略与终止条件；连续两次装配文本一致（同会话不重复拉取） | 通过 |
| 必读缺失即 fail-closed | 清空已加载契约后调用 `pms_command_preview` 被拒绝：`PMS 当前节点契约未加载，禁止执行写入`，且未发出任何 PMS 请求；重新装配后恢复 ready | 通过 |
| 写入预览 | `project.create` 预览返回 `operationId=177dbf55-54a8-4dda-a41d-09748a40894d`、中文警告、字段级 changes、过期时间 | 通过 |
| 用户确认门禁 | 未确认直接调用 `pms_command_execute` 被 DSH 审批门禁拦下：`PMS 写操作即将落库。请确认预览中的具体变更后再执行。` | 通过 |
| 执行与结果核验 | 用同一 `operationId` + 稳定幂等键执行 → `SUCCEEDED`，创建项目 44（`PRJ-000044`），再读 `pms_project_get` 核验名称一致 | 通过 |
| 幂等重试 | 同一 `operationId` 再次执行仍返回 `SUCCEEDED`，数据库中该操作只对应一个项目（未重复创建） | 通过 |
| 统一刷新 | 两次成功写入只产生一个刷新批次：`revision=1, scopes=[project-list, project-dashboard, project-detail]`；flush 后再查无新信号 | 通过 |
| 失败处理 | 非法参数预览被 PMS 拒绝（`不支持的 project.create 参数: unexpectedField`）；未知操作返回 404 `操作预览不存在`（requestId `21d8e021-c8f8-401b-821b-9cc50ca8a3cd`）；无效委派 token 拉契约返回 401（requestId `f08bc76b-cb06-4e80-8565-b3a24cd116f9`） | 通过 |
| 节点完成判断 | 对新项目 kickoff 节点预览 `node.complete`：PMS 完成校验后返回完成预览（`fromStatus=1 → toStatus=2`，`projectVersion/nodeVersion` 快照齐全、refreshScopes 为 `project-detail/project-dashboard/task-board`），未自动推进，仍需用户确认；对非当前节点 `requirement` 的完成预览被拒绝：`仅当前节点负责人或项目负责人可以完成进行中的节点` | 通过 |
| 项目字段更新（Phase 5 新增） | `project.update` 预览：`fromDescription null → toDescription 基于当前达模型进行调整`、`fromEndDate null → toEndDate 2026-10-30`、携带 `projectVersion 0`；执行 `SUCCEEDED` 后回读描述与截止日期一致，成员数 `1 → 1` 未被改动，刷新范围 `project-detail/project-list/project-dashboard` | 通过 |

登录请求编号：`a14a5fd7-1c41-449b-b611-23339caa67bf`（用户 `e2e.kickoff@pms.com`，userId 19）；刷新批次编号：`pms-refresh-batch-1789972578537`。

## 审计落库证据

`pms_ai_operation` 中契约版本独立于页面 `context_version` 保存：

| command_name | status | contract_id | contract_version | context_id | context_version |
| --- | --- | --- | --- | --- | --- |
| project.create | SUCCEEDED | pms-project-assistant/project-kickoff | 1.0.0 | pms:project-detail:43:379 | v1 |
| node.complete | PREVIEW | pms-project-assistant/project-kickoff | 1.0.0 | pms:project-detail:44:388 | v1 |
| project.update | SUCCEEDED | pms-project-assistant/project-kickoff | 1.0.0 | pms:project-detail:45:397 | v1 |

（`SELECT * FROM pms_ai_operation WHERE contract_id IS NOT NULL` 共 16 行；`contract_version` 始终独立于页面 `context_version`。）

同一批写入里，用真实 DSH 委派 token 直接调用命令预览、但**不携带** `contractId/contractVersion` 时，PMS 返回 `409 DSH 写入请求缺少当前有效节点契约，或契约未声明该命令`——写入链路的契约绑定由 PMS 侧强制，不依赖 DSH 自觉。

## 联调发现与修复

**缺陷**：`pms_command_preview` 会把当前页面的 `projectId/nodeId` 无条件注入所有命令参数。在项目详情页请求“创建项目”时，PMS 因不认识 `projectId` 直接拒绝预览（`不支持的 project.create 参数: projectId`），该节点的创建能力在真实页面上不可用。

**修复**：改为按 PMS 能力目录声明的命令参数注入——只补齐 PMS 实际声明的 `projectId/nodeId`；同时把契约 fail-closed 校验保持在能力查询之前，确保无契约时依旧不发起任何 PMS 请求。回归测试先行（新增用例先失败，再实现后通过），DSH `dsh-pms` 测试套件 `45 passed / 6 skipped`，`tsc -b packages/pms/dsh-pms/tsconfig.host.json` 通过。

## 覆盖边界与遗留

- 本记录覆盖的是真实后端 + 真实 DSH 插件代码路径；**没有**执行模型自然语言轮次（当前环境没有 DSH 所需的模型凭据），因此“信息不完整时先追问而不猜测”只验证到契约层：注入的必读文本包含入口条件、必需输入与缺失信息处理规则，工具层也不会替用户编造参数。
- `packages/pms/dsh-pms/src/tools/command.ts` 未达到 DSH 仓库“每个源码文件 100% 覆盖”的门禁：HEAD 上即为 90% statements / 71.05% branches（未覆盖 `pms_command_execute` 工具体与其 render、以及并发声明），本次改动后 statements 88.7% / branches 89.28%。该缺口与本契约交付无关，但会挡住 `pnpm run check:ci:coverage`，需要单独决定是补测试（要组合审批 seam 与并发调度）还是给该包做覆盖率豁免。
- 联调会在开发库中真实创建项目（多次运行累计产生 40–44 号 `立项联调-*`），并新增一个联调账号 `e2e.kickoff@pms.com`（userId 19，角色 ADMINISTRATOR）。如需清理，删除这些项目与该账号即可。
- 全量 PMS Maven 回归在本机需要正确的 Docker socket；`DOCKER_HOST` 未设置时会因 Testcontainers 找不到 Docker 而整批报错。
