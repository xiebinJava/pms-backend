# 统一节点完成协议 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让需求、专题、故事和项目节点通过同一套可发现、可校验的 `plan / preview / execute` 完成协议推进，同时区分可由明确完成意图自动确认的基线和必须保留业务审批的节点。

**Architecture:** 保留现有项目节点命令和研发事项命令的领域实现，不把四类资源强行合并成一套数据库模型。新增稳定的完成策略元数据，由 PMS 上下文公布；命令执行仍由各自领域服务完成校验。对于“完成意图本身就是确认”的基线，使用工作台完成钩子在同一事务内确认；涉及评审、验收或人工结论的工作台继续返回明确前置条件。

**Tech Stack:** Java 21, Spring Boot, MyBatis-Plus, JUnit 5, AssertJ, Mockito, Maven, Vitest E2E CLI harness.

**Spec:** 本计划基于当前已验证的 `node.complete-with-data` 和 `development-item.node.complete-with-data` 协议，以及需求、专题、故事、项目上下文的保存契约。

## Global Constraints

- PMS 后端仍是状态和权限的唯一事实来源。
- AI/CLI 只能通过 `plan → preview → execute` 写入，不能直接访问数据库。
- 明确的“完成节点”请求可以确认当前需求范围草稿，但不能伪造评审人、验收人或业务签字。
- 上下文展示名称优先于内部 ID；写入协议仍保留机器可用的引用字段。
- 每个任务完成后必须运行对应单元测试和 `git diff --check`。

## Review Focus

- 完成合同是否准确暴露“自动确认”和“需要业务确认”的差异；由 Task 1 的合同测试覆盖。
- 缺少负责人、排期或必填字段时是否只产生可执行前置条件；由 Task 2 的 `plan/preview` 测试覆盖。
- 同一请求重复执行是否保持幂等或返回冲突；由 Task 2 的 execute 测试覆盖。
- 项目、需求、专题、故事的上下文是否使用名称和稳定合同，而不是 UI DTO 偶然字段；由 Task 3 的上下文契约测试覆盖。
- Claude/Codex 真实调用是否能在隔离 PMS 中完成并回读；由 Task 4 的 E2E 覆盖。

### Task 1: 统一完成策略合同

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/connector/context/AiProjectComponentReader.java`
- Modify: `src/main/java/com/brad/pms/ai/connector/context/AiDevelopmentComponentReader.java`
- Create/modify: `src/main/java/com/brad/pms/ai/connector/context/CompletionContract.java`
- Test: `src/test/java/com/brad/pms/ai/connector/context/*ComponentReaderTest.java`

**Interfaces:**
- Produces `completionMode` with values `AUTO_CONFIRM`, `VALIDATE_ONLY`, `REQUIRES_DOMAIN_CONFIRMATION`.
- Produces `completionRequiredMetadata` and a human-readable `completionBlockReason` when domain confirmation is required.

- [x] Write failing contract tests for requirement scope, story development/testing, project review/acceptance, and topic research/design review.
- [x] Run the focused tests and verify they fail because the new metadata is absent.
- [x] Implement a small immutable contract helper and emit it from both context readers without changing persisted data.
- [x] Run focused tests and `git diff --check`.

### Task 2: 对齐完成命令的前置条件和幂等性

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/command/node/CompleteNodeWithDataCommand.java`
- Modify: `src/main/java/com/brad/pms/ai/command/development/CompleteDevelopmentItemNodeWithDataCommand.java`
- Modify: relevant domain services only where a completion hook is required.
- Test: corresponding command and service tests.

**Interfaces:**
- `plan` and `preview` expose the same completion mode and required inputs.
- `execute` keeps domain approval guards; automatic confirmation is limited to baseline workbenches that explicitly opt in.

- [x] Add failing tests for missing domain confirmation and repeated completion.
- [x] Implement the minimal policy-aware precondition/result behavior.
- [x] Run focused tests and backend packaging; the broader Maven suite was attempted previously and remains dependent on the local Docker environment.

### Task 3: 扩展需求、专题、故事场景

**Files:**
- Modify: development-item context and contract resolver classes as needed.
- Test: requirement/topic/story component and contract tests.

- [x] Add contract tests for read → fill → complete → readback for each resource type.
- [x] Add only the missing adapters or validation; reuse `CompleteDevelopmentItemNodeWithDataCommand`.
- [x] Verify IDs are retained only in machine fields and display projections use names.

### Task 4: 真实 CLI 闭环

**Files:**
- Modify: E2E documentation and test fixture only.
- Test: isolated PMS + `pms-cli` scenario.

- [x] Start a disposable PMS stack and create one fixture for each resource type.
- [x] Run natural-language-equivalent plan/preview/execute calls through the CLI.
- [x] Read back context and assert persisted fields, node transition, and protected-field invariants.
- [x] Remove the disposable data and report any environment-only limitations.

