# External Agent Contract v1 验收运行手册

这组验收验证 Claude、Codex 等外部 Agent 通过 `pms-cli` 读取上下文、生成草稿、预览、确认执行和回读验证的安全边界。PMS 前端不参与主路径，模型也不在 PMS 或 CLI 内置配置中调用。

## 本地契约验收

在 `pms-ai-connector` 目录执行：

```bash
VITEST_CACHE_DIR=/private/tmp/pms-vitest-cache pnpm vitest run --cache=false tests/e2e/external-agent-contract.e2e.test.ts
```

该测试使用固定 fixture 和 mock 场景结果，覆盖：

| 场景 | 预期 | 允许写入 PMS |
| --- | --- | --- |
| 只读需求分析 | `permissionMode=read-only`，无 operation | 否 |
| 缺少输入追问 | `permissionMode=draft`，保留 questions | 否 |
| 故事草稿 | `permissionMode=draft`，只生成 patch | 否 |
| 用户确认写入 | `permissionMode=write`，有 preview、operation、成功 verification | 是 |
| 版本冲突 | operation 为 `CONFLICT`，不得覆盖 | 否 |
| 回读未知 | operation 为 `WRITE_RESULT_UNKNOWN`，verification 为 `UNKNOWN` | 状态未知，禁止盲目重试 |

## 真实隔离环境验收

真实写入只允许使用一次性隔离环境，并同时设置以下变量：

```bash
PMS_E2E_WRITE=true
PMS_E2E_ISOLATED=true
PMS_E2E_BASE_URL=http://127.0.0.1:18080
PMS_E2E_ALLOWED_BASE_URL=http://127.0.0.1:18080
PMS_E2E_AUTH_ORIGIN=http://127.0.0.1:18080
PMS_E2E_DATABASE_NAME=pms_ai_e2e_<run-id>
PMS_E2E_ALLOWED_DATABASE_NAME=pms_ai_e2e_<run-id>
PMS_E2E_ACCOUNT_ID=<isolated-account-id>
PMS_E2E_RUN_ID=<run-id>
```

确认服务、数据库和账号均为一次性隔离资源后，再运行：

```bash
pnpm vitest run --cache=false tests/e2e/external-agent-contract.e2e.test.ts tests/e2e/pms-cli.e2e.test.ts
```

每个写入场景必须保存 `operationId`、`contextVersion`、`idempotencyKey`、回读结果和清理结果。版本冲突和 `WRITE_RESULT_UNKNOWN` 不得自动重试；先停止并人工核验操作记录。

## 外部 Agent 手工路径

1. `pms capabilities --format json`
2. `pms context <resourceType> <resourceId> --format agent`
3. 由主 Agent 或只读子 Agent 生成结构化 draft，保留事实、推断、假设和 evidence refs。
4. 使用场景 plan 或 `pms operation preview` 展示目标、节点、字段、版本和变更。
5. 在当前对话取得用户明确确认后，使用幂等键执行。
6. 重新读取 context/get，检查字段、保护字段、版本和操作状态。

子 Agent 只接收 snapshot 和 task，不接收 PMS Token、auth store 路径或写回调；主 Agent 是唯一调用 `pms-cli` 的 Agent。
