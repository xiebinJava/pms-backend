# Agent Resource Progression Baseline

记录日期：2026-10-16（Asia/Shanghai）

## 当前代码基线

- backend commit：`2ee93a69606fd7b82c551f26ed778a05571d4e79`
- connector commit：`210ec0a1845597f80e97960940f0add0ea3bc4ea`
- connector package version：`0.1.1`
- 当前能力：`story-testing/v1` saveContract、`development-item.node.complete-with-data`、pms-cli automatic operation、节点回读验证。

## 代码验证

Backend 定向测试：

```text
mvn -q -Dtest=AiDevelopmentComponentReaderTest,TopicDevelopmentTestingPolicyTest,StoryNodeContractResolverTest,CompleteDevelopmentItemNodeWithDataCommandTest test
```

结果：4 个测试类共 25 个测试，0 failures，0 errors，0 skipped。

Connector 类型与合同测试：

```text
pnpm typecheck
pnpm test -- tests/contract
```

结果：59 个 test files passed，253 个 tests passed，8 个隔离 E2E skipped。

隔离资源场景 E2E 在未设置一次性数据库和写入门禁时未执行写入；启用专用验收脚本会按安全策略阻止缺少配置的运行。未使用共享数据库替代隔离目标。

## 真实 PMS 链路证据

故事：`项目管理标准`（ID `34`）

- 测试节点：`386`（测试中）
- 初始节点版本：`1`
- 写入命令：`development-item.node.complete-with-data`
- 测试结果：`PASSED`
- 构建版本：`1.0.0`
- 测试报告：`内部测试记录`
- 遗留问题：`[]`
- 节点完成后当前节点：`387`（验收中）
- 节点完成后工作流进度：`57%`
- operationId：`c7bede1a-4dd6-476d-8a38-908226fbdccd`
- 回读结果：测试详情已持久化，节点状态为已完成，当前节点已切换到验收中。

## 尚未验证的部分

- 尚未完成四类资源统一 `agent-progression/v1` 外层协议。
- 尚未完成故事验收节点的独立隔离 E2E。
- 尚未运行 Claude/Codex 的自然语言影子评估，因此没有声明自然语言效率收益。
- Orchestrator 的真实隔离验收仍需一次性数据库、账号和清理门禁。

本基线只记录已观察到的代码和真实链路证据，不把固定 Mock、静态契约或共享开发数据当作自然语言质量或生产可靠性的证明。
