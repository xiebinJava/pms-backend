# 全流程 E2E 三处缺陷修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复故事完成状态不同步、连续编辑用例丢失输入、独立专题无法创建故事三处已复现缺陷。

**Architecture:** 保留现有节点工作台、不可变模板版本与自动挂载机制。故事业务状态在节点完成/回滚事务内同步；前端保持可编辑并利用现有版本控制与排队保存；独立专题下的故事按真实父子范围校验，不直接取消安全校验。

**Tech Stack:** Spring Boot / MyBatis-Plus / MySQL / Vue 3 / Playwright / JUnit。

**Spec:** 用户本聊天的修复授权；复现证据 `/private/tmp/pms-full-e2e.6L0f20/verified-run.log`（9 个场景：6 通过、3 失败）。

## Global Constraints

- 保留两个仓库现有未提交改动，不重置、覆盖或提交无关文件。
- 不清空原数据库、不批量迁移现有业务记录、不修改用户模板绑定版本。
- 三处修复均先增加失败测试，确认失败原因，再改业务代码。
- 本次不提交、不推送代码；真实数据库验证使用隔离副本，关闭邮件/通知/Webhook。
- 既有节点负责人、排期、任务看板和工作台绑定关系不变。

## Review Focus

- 回滚已完成故事后，业务状态不能继续为 DONE；项目完成守卫应再次阻止推进。
- 完成中间节点不能提前标记故事 DONE，失败事务不能留下半更新状态。
- 保存过程中继续输入、连续增删用例不能被旧响应覆盖；网络失败仍保留未保存草稿。
- 独立专题下故事只能绑定自身专题流程节点，不能跨专题或跨项目挂载。
- 原本没有专题的独立故事仍可创建；已删除专题、错误挂载节点仍应拒绝。

## Task 1：故事流程状态与业务状态同步

**Files:**
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify if needed: `src/main/java/com/brad/pms/mapper/ProjectNodeDevelopmentStoryMapper.java`
- Create: `src/test/java/com/brad/pms/service/StoryWorkflowStatusIntegrationTest.java`

**Interfaces:**
- Consumes: `completeNode(DevelopmentItemType, Long, Long)`、`rollbackNode(DevelopmentItemType, Long, Long, String)` 与事务锁定后的节点集合。
- Produces: 同一事务中故事 status/progress 与实际已完成节点一致；全部完成为 DONE/100，回滚恢复 IN_PROGRESS 与按完成数计算的进度。

- [ ] Step 1: 建立真实数据库测试：两节点故事先完成第一节点，不得 DONE；完成末节点后查询真实故事表为 DONE/100，详情和项目开发汇总一致。
- [ ] Step 2: 增加末节点和较早节点回滚测试；断言状态 IN_PROGRESS、进度按完成节点数降低，原字段和任务保留；任务阻塞完成不得改业务状态。
- [ ] Step 3: 运行 `./mvnw -Dtest=StoryWorkflowStatusIntegrationTest test`，确认现状因缺少状态同步而失败；若仓库无 wrapper，用 `mvn`。
- [ ] Step 4: 在 completeNode / rollbackNode 成功更新节点后同步故事业务状态。只更新 status/progress，避免回写旧实体覆盖身份字段；写入失败抛出冲突并回滚事务。
- [ ] Step 5: 重跑新增测试及现有 rollback、node edit、story workbench 集成测试。

## Task 2：连续编辑时不丢失草稿

**Files:**
- Modify: `../pms-front/src/views/development/detail/DevelopmentItemDetailPage.vue`
- Modify if needed: `../pms-front/src/views/development/detail/StoryNodeWorkbenchComponent.vue`
- Create: `../pms-front/tests/workflows/story-autosave.spec.mjs`
- Create: `../pms-front/tests/workflows/story-autosave.config.mjs`

**Interfaces:**
- Consumes: 现有 `onNodeFieldValuesChange`、`saveNode(): Promise<boolean>`、nodeFormEditRevision、queuedNodeSave 与乐观版本字段。
- Produces: 保存未完成时仍接受有权限用户的后续输入；后续修改排队保存，旧响应不覆盖新草稿。真正已完成/锁定节点保持只读。

- [ ] Step 1: 用真实 Vue 页面在可控延迟的保存接口边界复现：填用例名触发保存，响应前填写预期结果并失焦。断言第二次有效保存包含两字段，刷新后两字段保留。
- [ ] Step 2: 增加保存过程中新增/删除另一条用例、保存失败草稿保留、真正只读节点禁止修改三个场景；断言 UI 状态与保存 payload，不能仅断言源代码文本。
- [ ] Step 3: 运行 `pnpm exec playwright test --config=tests/workflows/story-autosave.config.mjs`，确认预期结果现状丢失。
- [ ] Step 4: 取消将 savingNode 当作故事工作台的编辑权限。复核父层现有保存队列/修订号与子层 modelValue 回填，必要时只调整导致旧响应覆盖新草稿的分支；不移除实际节点权限限制。
- [ ] Step 5: 重跑新增浏览器测试、`pnpm test`、`pnpm typecheck`、`pnpm build`。

## Task 3：独立专题下创建故事的范围校验

**Files:**
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Create: `src/test/java/com/brad/pms/service/StandaloneTopicStoryIntegrationTest.java`

**Interfaces:**
- Consumes: `createWithTemplate(...)` / `createIfDefaultExists(...)` 的项目范围与故事真实 topicId/topicWorkflowNodeId。
- Produces: projectId/sourceNodeId 均为空的合法独立专题故事正常初始化工作流，仍绑定该专题的挂载节点和选定已发布版本。

- [ ] Step 1: 通过真实服务建立独立专题并创建子故事；断言故事创建成功、项目为空、topicId 正确、挂载节点属于该专题、工作流版本正确。
- [ ] Step 2: 增加纯独立故事正常创建，以及父专题删除、跨专题节点、项目范围不一致等拒绝测试。
- [ ] Step 3: 运行 `./mvnw -Dtest=StandaloneTopicStoryIntegrationTest test`，确认合法子故事因现有 requireItemScope 分支被拒绝。
- [ ] Step 4: 修正 requireItemScope 的 projectId 为空分支：允许合法独立专题关系，验证父专题存在且未删除、项目与来源范围均为空、挂载节点确实属于父专题工作流；不放宽其他范围限制。
- [ ] Step 5: 重跑新增测试及挂载绑定/专题故事迁移测试。

## Final verification

- [ ] 运行后端完整 `./mvnw test`（或 `mvn test`）；记录已有失败，不把未运行当通过。
- [ ] 构建当前前后端源码并在独立容器网络 + 数据库副本运行；不能继续验证旧运行镜像。
- [ ] 复跑真实需求→项目→专题→故事链路，移除原测试中手工将故事标记 DONE 的绕过；项目必须自然完成全部节点。
- [ ] 复跑连续编辑真实服务场景和独立专题创建故事场景；检查刷新持久化、API 错误、页面运行错误。
- [ ] 复核差异没有误改模板、原数据、无关用户文件；清理测试容器/临时凭据并确认原服务健康。
- [ ] 向用户报告实际通过结果、剩余限制；未获得额外授权不提交/推送、不部署正式环境。
