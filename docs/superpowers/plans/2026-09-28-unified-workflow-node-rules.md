# Unified Workflow Node Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 让项目、专题、故事详情页共享一致的节点完成、回滚、必填校验和操作布局，并保证新建流程模板仍然自动复用这套规则。

**Architecture:** 后端以一个固定元数据校验器统一负责人和排期规则，分别由项目节点服务和研发事项工作流服务调用；模板字段仍由模板版本快照和现有字段校验器驱动。前端复用现有项目详情的右侧操作语义，在研发事项详情中补齐完成/回滚操作，不复制流程模板节点定义。

**Tech Stack:** Spring Boot 3、MyBatis-Plus、JUnit 5/AssertJ、Vue 3、TypeScript、Ant Design Vue、Node test、Playwright fallback smoke。

**Spec:** `docs/superpowers/specs/2026-09-28-unified-workflow-node-rules-design.md`

## Global Constraints

- 已发布流程模板版本是研发事项运行时的不可变快照；已绑定实例不受新模板影响，尚未创建实例的事项首次进入详情时才按当前默认模板初始化。
- 完成节点前必须校验负责人、完整排期、可见必填字段和当前节点全部任务/子任务。
- 只有已完成节点可以回滚；回滚需要非空原因，并恢复后续节点为未开始。
- 项目、专题、故事使用相同的用户可见操作语义和错误提示。
- 不通过直接数据库写入绕过业务服务。

## Review Focus

- 缺少负责人或只填写一端日期时，完成请求必须被后端拒绝；由任务 2/3 的服务测试覆盖。
- 已完成节点回滚后再次完成，且后续节点解锁顺序正确；由任务 2/3 的回滚测试覆盖。
- 模板版本切换或新建模板后，已绑定实例仍使用绑定快照并执行同一固定规则；由任务 2 的模板快照测试覆盖。
- 完成请求重复提交或回滚并发执行时，不能越过状态校验或覆盖节点顺序；由任务 2 的事务锁和状态测试覆盖。
- UI 点击回滚后未填写原因、保存中的节点或已完成节点编辑，必须有明确阻断且不能产生半保存状态；由任务 4/5 的交互测试覆盖。

### Task 1: 固定节点完成策略

**Files:**
- Create: `src/main/java/com/brad/pms/service/WorkflowNodeCompletionPolicy.java`
- Test: `src/test/java/com/brad/pms/service/WorkflowNodeCompletionPolicyTest.java`

**Interfaces:**
- Produces `validateOwnerAndSchedule(Long ownerId, LocalDate startDate, LocalDate endDate)`，失败时抛出业务错误。
- Produces `requireReason(String reason)`，用于回滚原因校验。

- [ ] 写测试：负责人为空、开始/结束日期任一为空、日期倒序、合法输入和空回滚原因分别断言。
- [ ] 运行 `mvn -q -Dtest=WorkflowNodeCompletionPolicyTest test`，确认先失败。
- [ ] 实现策略类，错误文案与现有项目节点中文提示保持一致。
- [ ] 重跑同一测试，确认通过。
- [ ] 自检：策略只负责固定规则，不读取模板或数据库。

### Task 2: 研发事项后端完成与回滚闭环

**Files:**
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify: `src/main/java/com/brad/pms/controller/DevelopmentItemWorkflowController.java`
- Reuse: `src/main/java/com/brad/pms/mapper/DevelopmentItemWorkflowNodeMapper.java` existing `selectByWorkflowIdsForUpdate(...)`
- Test: `src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceNodeEditTest.java`
- Test: `src/test/java/com/brad/pms/service/DevelopmentItemWorkflowServiceRollbackTest.java`

**Interfaces:**
- Add `POST /development/items/{type}/{id}/nodes/{nodeId}/rollback` with body `{reason}`.
- Add `rollbackNode(DevelopmentItemType itemType, Long itemId, Long nodeId, String reason)` returning `DevelopmentItemWorkflowDetailDTO`.
- Update `completeNode(...)` to call the shared fixed-rule policy before template required-field and task checks.

- [ ] 先补服务测试：缺负责人、缺排期、未完成字段、未完成任务均拒绝；合法节点完成并激活下一节点。
- [ ] 先补回滚测试：仅已完成节点可回滚，原因不能为空，状态序列为“之前完成/目标进行中/之后未开始”。
- [ ] 运行定向 Maven 测试，确认新增断言失败。
- [ ] 实现回滚事务、状态迁移、权限检查和详情返回；使用已有 `selectByWorkflowIdsForUpdate(...)` 锁定流程节点集合，继续使用工作流实例绑定的模板版本，不增加客户端版本参数。
- [ ] 接入统一策略并保持 `WorkflowFieldValueValidator` 与任务完成校验。
- [ ] 运行定向测试，确认通过。
- [ ] 自检：不会从当前默认模板重新创建节点，不会修改模板版本快照；同一流程实例的并发完成/回滚不会覆盖状态顺序。

### Task 3: 项目节点规则对齐

**Files:**
- Modify: `src/main/java/com/brad/pms/service/NodeService.java`
- Test: `src/test/java/com/brad/pms/service/NodeServiceCompletionTest.java`
- Extend: `src/test/java/com/brad/pms/service/NodeServiceScheduleTest.java`

**Interfaces:**
- `NodeService.complete(...)` 在现有负责人、项目字段、自定义字段和任务校验前调用统一负责人/排期策略。
- 保留既有 `NodeService.rollback(...)` API 和权限语义。

- [ ] 写项目完成测试：负责人存在但排期不完整时拒绝，合法输入继续完成。
- [ ] 运行定向测试确认失败。
- [ ] 接入策略并保留项目专属校验。
- [ ] 运行项目节点测试，确认通过。
- [ ] 自检项目回滚、AI 节点命令和已有权限测试不受影响。

### Task 4: 前端研发事项节点操作对齐

**Files:**
- Modify: `pms-front/src/api/development-item.ts`
- Modify: `pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Modify: `pms-front/src/locales/zh-CN.ts` and corresponding locale file if required
- Test: `pms-front/src/views/development/development-item-detail-visual.test.mjs`

**Interfaces:**
- Add `rollbackDevelopmentItemNode(itemType, itemId, nodeId, reason)`.
- Development detail header actions: active node shows right-aligned complete button; completed node shows right-aligned rollback button; locked node has no mutation action.

- [ ] 先补静态契约测试，锁定按钮位置、回滚 API、回滚原因弹窗、已完成节点动作和统一错误提示。
- [ ] 运行 `node --test src/views/development/development-item-detail-visual.test.mjs`，确认新增断言失败。
- [ ] 实现回滚状态、原因弹窗、保存未完成编辑后再回滚、成功后刷新详情并选择目标节点。
- [ ] 保证负责人/排期控件在进行中节点可编辑、已完成节点只读；完成按钮不再出现在左侧或非当前节点。
- [ ] 运行前端 typecheck 和目标测试，确认通过。

### Task 5: UI 与浏览器交互验证

**Files:**
- Modify only if validation exposes a real issue in the shared detail styles/components.
- Evidence: `/tmp/pms-workflow-node-review-desktop.png`, `/tmp/pms-workflow-node-review-mobile.png`

- [ ] 启动或复用本地前后端，使用可登录测试账号进入项目、专题、故事详情。
- [ ] 验证进行中节点操作位在标题行最右侧，负责人/排期编辑后自动保存。
- [ ] 验证不填写负责人或排期时点击完成有明确错误，填写后且任务完成才能完成。
- [ ] 验证已完成节点可回滚，未填写原因不能提交，成功后节点状态和按钮恢复正确。
- [ ] 采集桌面和移动宽度截图，检查无溢出、遮挡和颜色偏差。
- [ ] 运行前端 visual lint/Playwright smoke；若 Browser 插件不可用，记录原因并使用 Playwright fallback。

### Task 6: 全量回归与阶段 review

- [ ] 后端运行 `mvn -q test`。
- [ ] 前端运行 `pnpm typecheck`、前端测试和构建。
- [ ] 检查 `git diff --check`，逐条对照本计划的 Global Constraints 和 Review Focus。
- [ ] Review 项目、专题、故事三类路径及模板新建/发布后的快照行为。
- [ ] 只有所有验证有新鲜命令输出后，才报告完成；如有失败，先修复再进入下一阶段。
