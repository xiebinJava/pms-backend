# 需求分析编排闭环验收记录

日期：2026-10-15（Asia/Shanghai）

## 验收范围

本轮实现的场景为：外部 AI 工作台生成结构化 `AnalysisDraft`，Connector 校验事实、推断、假设和待决策事项，读取 PMS 真实上下文，预览需求分析保存，等待确认，执行后回读资源和上下文。

PMS Backend 仍然是权限、版本、工作流和写入规则的唯一来源。第一期没有把模型 SDK、异步 operation event 或聊天 UI 放进后端。

## 代码级证据

- Connector 场景契约测试：`pnpm exec vitest run tests/contract/requirement-analysis-contract.test.ts`，4 个通过。
- Connector 场景编排测试：`pnpm exec vitest run tests/unit/requirement-analysis-orchestrator.test.ts`，4 个通过。
- Connector 全量测试：106 个通过，3 个真实 E2E 跳过。
- Connector `typecheck` 和 CLI 构建通过。
- Backend 上下文快照测试和现有上下文单测通过。
- Backend 隔离上下文集成测试报告 3 个跳过，未把 skipped 计为通过。

## 真实环境验收

Connector 的 `verify:real-pms:analysis-save` 已执行，但因为当前环境没有完整的隔离写入配置而安全跳过。没有发生浏览器认证、PMS 写入或清理请求。

真实验收需要明确设置 `PMS_E2E_BASE_URL`、`PMS_E2E_ALLOWED_BASE_URL`、`PMS_E2E_AUTH_ORIGIN`、`PMS_E2E_ACCOUNT_ID`、`PMS_E2E_RUN_ID`、`PMS_E2E_REQUIREMENT_ID` 和 `PMS_E2E_NODE_ID`，并同时启用 `PMS_E2E_WRITE=true`、`PMS_E2E_ISOLATED=true`。

## 决策

当前可以进入隔离环境真实验收，但不能宣称自然语言质量和真实保存回读已经通过。真实验收通过前，不扩大到项目计划自动推进、无人值守 Agent 或内置聊天页面。
