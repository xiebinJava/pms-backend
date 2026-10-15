# AI Orchestrator v1 隔离验收报告

日期：2026-10-15（Asia/Shanghai）

## 本轮结论

隔离验收代码已经补齐，但本轮本机没有启动一次性 MySQL/API 环境，也没有配置 `PMS_E2E_WRITE=true`、`PMS_E2E_ISOLATED=true` 及独立数据库清理确认，因此真实写入用例保持 **SKIPPED**。没有把共享开发数据或结构化单元测试当作真实验收通过证据。

确定性编排器的服务、控制器、Connector 客户端和 CLI 契约测试已经通过；真实验收必须按下表所列命令在独立数据库中运行。

## 验收入口

- Connector：`tests/e2e/orchestrator-requirement-analysis.e2e.test.ts`
- 安全门：`tests/e2e/support/isolatedSession.ts` 与 `tests/unit/e2e-safety.test.ts`
- 后端编排器：`/integration/ai/v1/orchestrator` 路由及 `AiOrchestratorService`
- 报告所需字段：用户 ID、conversation/turn/plan/operation ID、前后版本、事件序列、回读验证、幂等重放、冲突结果、清理结果和跳过原因

## 验收命令

```bash
PMS_E2E_WRITE=true \
PMS_E2E_ISOLATED=true \
PMS_E2E_CREATE_FIXTURE=true \
PMS_E2E_DATABASE_CLEANUP_CONFIRMED=true \
PMS_E2E_BASE_URL=http://127.0.0.1:<isolated-port>/api \
PMS_E2E_ALLOWED_BASE_URL=http://127.0.0.1:<isolated-port>/api \
PMS_E2E_AUTH_ORIGIN=http://127.0.0.1:<isolated-port> \
PMS_E2E_DATABASE_NAME=pms_ai_e2e_<run-id> \
PMS_E2E_ALLOWED_DATABASE_NAME=pms_ai_e2e_<run-id> \
PMS_E2E_ACCOUNT_ID=<isolated-user-id> \
PMS_E2E_RUN_ID=<run-id> \
pnpm exec vitest run tests/e2e/orchestrator-requirement-analysis.e2e.test.ts --no-file-parallelism --cache=false
```

`PMS_E2E_CREATE_FIXTURE=true` 时，运行器必须在 finally 阶段销毁整个一次性数据库；测试只允许在已经确认会被销毁的数据库中创建需求夹具。若只使用已有隔离需求，应提供 `PMS_E2E_REQUIREMENT_ID` 和 `PMS_E2E_NODE_ID`，并仍然执行数据库生命周期清理。

## 必须通过的检查

1. 有效 Draft 读取真实上下文后进入 `PLAN_READY`，预览后进入 `WAITING_CONFIRMATION`。
2. 首次确认产生一个 operation，并在回读中确认 patch、生效资源、当前节点和受保护字段。
3. 使用同一幂等键重放返回相同 operation，不产生第二次业务写入。
4. 使用旧 `contextVersion` 和新幂等键返回 `409/CONFLICT`，回读中没有第二份内容。
5. 事件序列从 1 递增，包含上下文、草稿、计划、预览、确认、执行和验证阶段。
6. 失败时仍执行登出和一次性数据库清理；清理失败必须使验收失败并记录残留资源。

## 当前证据

| 检查 | 本轮结果 | 证据 |
| --- | --- | --- |
| 后端状态/持久化/服务/控制器测试 | PASS | `mvn -q -Dtest=AiOrchestratorStateTest,AiOrchestratorPersistenceTest,AiAgentDraftValidatorTest,DefaultAiConnectorPortTest,AiOrchestratorServiceTest,AiOrchestratorControllerTest,AiConnectorControllerTest test` |
| Connector 编排器客户端和 CLI 测试 | PASS | 5 个新增测试通过 |
| Connector 类型检查 | PASS | `pnpm typecheck` |
| CLI 构建 | PASS | `pnpm build:cli` |
| 真实隔离写入/幂等/冲突/回读 | SKIPPED | 本轮未启动一次性环境，未配置隔离写入门禁 |

完整测试中的认证监听和安装测试受当前沙箱禁止监听 localhost、写入 `dist`/Vitest 缓存影响，不能作为本功能失败证据；真实环境运行时需单独记录这些测试结果。
