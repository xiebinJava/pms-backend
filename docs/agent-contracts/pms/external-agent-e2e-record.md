# External Agent Contract v1 验收记录

## 本次记录

- 记录日期：2026-10-15
- Connector commit：`e8a3d02`（Skill 协议）及后续验收修复
- Backend capability contract：`cfd0b48`
- 运行人：待填写
- 真实隔离环境：未在本记录中启用

## 固定 fixture 验收

命令：

```bash
VITEST_CACHE_DIR=/private/tmp/pms-vitest-cache pnpm vitest run --cache=false tests/e2e/external-agent-contract.e2e.test.ts
```

结果：通过。六个场景均由固定上下文和 mock 结果验证，没有修改 PMS 数据。

| case | resource | node | contract | context version | operation | permission | verification | PMS 数据变化 |
| --- | --- | ---: | --- | --- | --- | --- | --- | --- |
| read-only-analysis | requirement:33 | 701 | agent-contract/v1 | requirement:33:entity:4 | — | read-only | — | 否 |
| missing-input-clarification | requirement:33 | 701 | agent-contract/v1 | requirement:33:entity:4 | — | draft | — | 否 |
| draft-only-story | story:34 | 702 | agent-contract/v1 | story:34:entity:5 | — | draft | — | 否 |
| confirmed-write | story:34 | 702 | agent-contract/v1 | story:34:entity:5 | operation-confirmed-1 | write | SUCCEEDED | 是（隔离环境） |
| version-conflict | story:34 | 702 | agent-contract/v1 | story:34:entity:5 | operation-conflict-1 | write | FAILED | 否 |
| unknown-readback | story:34 | 702 | agent-contract/v1 | story:34:entity:5 | operation-unknown-1 | write | UNKNOWN | 未知，禁止重试 |

## 全量回归限制

Connector 全量测试中其余失败是当前沙箱限制：认证测试不能监听 localhost，安装测试不能写入既有 `dist/pms-cli.js`。它们与 Agent Contract fixture 无关；真实隔离环境运行时应单独记录这两类环境结果。

## 真实环境补录模板

完成真实隔离验收后，在此补录：

- PMS base URL / database / account：
- fixture run ID：
- 每个 case 的真实节点、contextVersion、operationId、auditId：
- `WRITE_RESULT_UNKNOWN` 的人工核验结论：
- 清理的资源 ID 和清理失败项：
