# Development Item Workflow Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为专题和故事建立独立详情页，使用各自配置的专题/故事流程模板，并支持节点、任务和子任务的实际推进。

**Architecture:** 每个专题/故事首次创建或首次访问时绑定对应流程类型的默认已发布模板版本，并物化为独立流程实例与节点实例。新增独立事项任务存储，节点按顺序推进；通过所属项目复用现有权限边界。前端提供共用的研发事项详情页及节点任务看板，专题/故事列表分别路由到各自详情。

**Tech Stack:** Spring Boot、MyBatis-Plus、Flyway、Vue 3、TypeScript、Ant Design Vue、现有项目权限与流程模板服务。

**Spec:** `docs/superpowers/specs/2026-09-23-development-item-workflow-details-design.md`

## Global Constraints

- 专题详情只绑定 `topic-management` 类型，故事详情只绑定 `story-management` 类型。
- 每个事项固定使用首次绑定的默认已发布模板版本；后续模板修改不得覆盖已创建的实例节点或状态。
- 专题/故事任务与来源项目的项目级任务分离，任务和子任务只归属于一个事项流程节点。
- 流程节点按顺序推进；所有未完成节点均允许提前维护负责人、排期、模板字段及节点任务；只有当前进行中的节点且其全部任务/子任务完成后才能完成节点。
- 负责人和排期变更后自动保存；模板字段失焦或点击编辑区外时自动保存；不提供单独的保存按钮；已完成节点只读。
- 继续保留事项的来源项目、来源项目节点、专题/故事父子关系和研发状态信息。
- 不改变项目自身的 `project_node`、项目流程和项目级任务。
- 保留共享工作区已有更改；不提交、推送、合并、重置或清理。
- 每个阶段结束执行构建/类型检查（适用时）、`git diff --check` 和规格对照自审；自审通过后才进入下一阶段。

## Review Focus

- 同一事项并发首次打开只能生成一个实例和一份节点快照；唯一约束与冲突后重读保证幂等。
- 专题、故事及来源项目/节点 ID 必须在服务端一起核对，任何详情/节点/任务接口都不能跨项目读写。
- 子任务必须与父任务处于同一事项和节点，不能把另一节点或另一事项的 ID 传入关联。
- 当前默认流程为空时允许继续创建专题/故事；详情清楚显示未配置，后续配置完成后才允许首次绑定。
- 完成节点需要校验任务和子任务全完成，并只解锁顺序中的下一节点。
- 删除旧专题/故事时清理对应事项流程实例及其节点和任务，避免遗留不可访问数据。
- 列表的流程状态/进度来自事项流程实例；研发/测试状态仍单独保留，不相互覆盖。

---

### Task 1: Stage 1 — 建立事项流程实例、节点和任务的持久化模型

**Files:**
- Create: `src/main/resources/db/migration/V51__development_item_workflows.sql`
- Create: `src/main/java/com/brad/pms/entity/DevelopmentItemWorkflowDO.java`
- Create: `src/main/java/com/brad/pms/entity/DevelopmentItemWorkflowNodeDO.java`
- Create: `src/main/java/com/brad/pms/entity/DevelopmentItemTaskDO.java`
- Create: 对应三个 MyBatis mapper
- Create: `src/main/java/com/brad/pms/workflow/DevelopmentItemType.java`
- Create: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify: `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`
- Modify: 对应已有事项删除/替换逻辑

**Interfaces:**
- Consumes: `WorkflowTemplateService` 的默认版本与定义查询、现有 `ProjectNodeDevelopmentTopicDO` / `ProjectNodeDevelopmentStoryDO`。
- Produces: 按 `itemType + itemId` 唯一的流程实例；保存所属项目、来源项目节点、模板版本；保存带模板字段快照的流程节点；保存节点任务/子任务。

- [x] 定义 migration 表：实例表唯一约束 `(item_type, item_id)`；节点表唯一约束 `(workflow_id, node_key)`；任务表保存 `workflow_id`、`node_id`、`parent_id`、标题/说明、状态、负责人、截止日期、排序、软删除与乐观锁版本。项目 ID、来源项目节点 ID、模板版本 ID 添加外键；多态事项 ID 由 service 验证对应 topic/story 存在且属于同一项目/节点。
- [x] 建立 `DevelopmentItemType`，只允许 `TOPIC`、`STORY`，将模板类型 code 分别固定映射到 `topic-management`、`story-management`。
- [x] 实现幂等绑定：先确认事项、来源项目及当前权限；读取事项类型对应的默认已发布模板版本；无默认版本时不创建实例并返回空结果，由 Stage 2 API 映射为结构化“流程未配置”状态；有版本时复制定义中的节点顺序、key、名称、说明、交付物并将首节点设为进行中，其余节点设为未开始。唯一键冲突时读取已创建实例返回，不重复生成。
- [x] 在 `NodeDevelopmentControlService.save` 新建专题/故事行后，若默认模板可用，则同一事务创建流程实例；替换删除专题/故事时同步删除事项工作流、节点、任务和子任务。
- [x] 阶段自审：V51 版本唯一；所有唯一/外键与 service 归属校验一致；事项删除与源节点级联删除均清理多态事项工作流和下属节点/任务；未触及 `project_node` 与 `project_task`。
- [x] 阶段自审通过：`git diff --check`、`mvn -q -DskipTests package` 通过；未运行测试。

---

### Task 2: Stage 2 — 实现详情读取、节点流转和事项任务 API

**Files:**
- Create: 事项详情、流程实例、流程节点、任务的 request/response DTO
- Create: `src/main/java/com/brad/pms/controller/DevelopmentItemWorkflowController.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemWorkflowService.java`
- Modify: `src/main/java/com/brad/pms/service/DevelopmentItemService.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentTopicListDTO.java`
- Modify: `src/main/java/com/brad/pms/dto/response/DevelopmentStoryListDTO.java`

**Interfaces:**
- `GET /development/topics/{id}`、`GET /development/stories/{id}`：返回事项、项目/来源节点上下文、流程实例、节点和节点任务。
- `PUT /development/items/{type}/{id}/nodes/{nodeId}`：更新节点负责人及排期。
- `POST /development/items/{type}/{id}/nodes/{nodeId}/complete`：校验当前活动节点和节点任务完成情况，然后解锁下一节点。
- `POST /development/items/{type}/{id}/nodes/{nodeId}/tasks`、`PUT/DELETE /development/items/{type}/{id}/tasks/{taskId}`：事项节点任务及子任务 CRUD/状态更新。
- 列表 DTO 返回流程状态/进度和独立的研发状态摘要。

- [x] 为详情 GET 增加服务端项目权限检查；旧事项第一次访问时执行幂等绑定，响应包含绑定模板版本及模板未配置状态。
- [x] 增加节点负责人/排期/模板字段更新：负责人必须属于来源项目成员；开始日期不得晚于结束日期；未开始和进行中节点可编辑，已完成节点不可编辑。
- [x] 增加完成节点操作：仅允许完成当前活动节点；未删除任务或子任务有未完成项时拒绝；成功后解锁唯一的下一个节点。
- [x] 增加任务/子任务创建、编辑、完成/恢复与删除；每次从 URL 重新解析事项和节点归属；子任务父级必须是同一节点下的顶层任务，禁止跨事项/跨节点引用。
- [x] 增加实例进度聚合：`已完成节点数 / 总节点数`；列表状态/进度显示流程摘要，研发状态仍来自旧 topic/story 记录。旧事项只在详情 GET 首次绑定；列表对未绑定事项显示默认流程对应的未开始/未配置摘要，不产生绑定副作用。
- [x] 阶段自审：详情读取需要项目读取权限；所有写接口都校验项目写权限和事项/节点/任务归属；乐观锁冲突返回刷新提示。`git diff --check` 与 `mvn -q -DskipTests package` 通过；未运行测试。
- [x] 阶段自审通过后再进入 Stage 3。

---

### Task 3: Stage 3 — 增加专题/故事详情前端及列表跳转

**Files:**
- Create: `src/views/development/detail/DevelopmentItemDetailPage.vue`
- Create: `src/views/development/detail/components/DevelopmentItemFlow.vue`
- Create: `src/views/development/detail/components/DevelopmentItemTaskBoard.vue`
- Create: 专题/故事轻量 route wrapper
- Create: `src/api/development-item-workflow.ts`
- Modify: `src/router/index.ts`
- Modify: `src/views/development/DevelopmentListPage.vue`
- Modify: `src/types/domain.ts`
- Modify: `src/locales/zh-CN.ts`
- Modify: `src/locales/en-US.ts`

**Interfaces:**
- Routes: `/development/topics/:id`、`/development/stories/:id`。
- Shared detail page accepts `itemType: 'topic' | 'story'`, loads the matching detail API, shows project/source node context, flow rail, active-node panel, and node task/subtask board.

- [x] 将列表项目名称和“详情”操作改为事项详情路由；项目上下文链接仍进入来源项目并定位来源研发节点；故事行和故事详情中的所属专题链接进入专题详情。
- [x] 新增详情请求客户端及 domain types，展示页头、模板版本、未配置提示、流程进度、节点说明/负责人/排期和任务/子任务列表。
- [x] 按已完成流程节点、当前活动节点、未开始后续节点呈现节点导航；所有未完成节点显示编辑控件与节点任务操作，只有当前活动节点显示完成操作；负责人使用项目成员列表及现有人员选择组件。
- [x] 实现任务与子任务的新增、编辑、完成/恢复、删除；请求成功后以服务端详情响应刷新当前页面，返回列表时重新拉取列表摘要；并发冲突提示刷新后重试。
- [x] 为中文和英文补齐路由标题、流程状态、节点操作、任务操作、未配置模板、无权限/加载错误等文案。
- [x] 阶段自审：路由与 API 类型映射一致；来源项目与节点链接保留；项目详情页和项目级任务看板代码未被本阶段修改；流程 rail 和任务区均有窄屏横向/纵向布局适配。`pnpm typecheck`、`git diff --check` 通过；未运行测试。
- [x] 阶段自审通过后再进入 Stage 4。

---

### Task 4: Stage 4 — 全局构建、持久化边界和规格自审

**Files:**
- Modify only scoped files from Stages 1–3 if review exposes a defect.
- Update: `docs/superpowers/specs/2026-09-23-development-item-workflow-details-design.md` only if implementation requires a documented clarification.

**Interfaces:**
- Consumes: all backend persistence/API and frontend route/detail outputs.
- Produces: successful backend package, frontend typecheck/build, final scoped diff review, and implementation notes.

- [x] `mvn -q -DskipTests package`、`pnpm typecheck`、`pnpm build` 均通过；未执行测试。
- [x] 规格逐项自审通过：专题/故事详情各自绑定正确类型的固定发布版本；未完成节点可编辑节点资料、模板字段和节点任务，完成节点只读；只有当前活动节点可以完成；任务只属于事项节点且受项目权限保护；子任务同事项同节点；首次绑定与事项删除通过事项行锁及唯一键保持幂等/清理；无默认模板有明确提示。
- [x] 两个仓库 `git diff --check` 均通过；确认本次没有修改项目详情页或项目级任务看板，之前已有改动仍留在各自工作区；不纳入提交。
- [x] 最终自审完成并更新执行台账；已在登录态专题详情验证未开始节点显示负责人、排期和任务编辑入口；不提交、不推送、不合并。

## Plan Self-Review

- 所有 8 条规格验收项都有对应实现阶段：路由/详情在 Stage 3，类型隔离和版本固定在 Stages 1–2，幂等/权限/删除边界在 Stages 1–2，节点预编辑、自动保存与顺序完成在 Stages 2–3，构建与范围审查在 Stage 4。
- 风险输入已映射到阶段 Review Focus：并发绑定、跨项目引用、跨节点子任务、无默认模板、未完成任务阻断、旧事项删除和列表状态分离。
- 迁移版本采用 V51；当前 V43 已由 AI 命令审计迁移占用，V50 已由流程类型筛选功能使用。
- 本计划沿用用户此前要求的阶段自审节奏；当前共享工作区保留使用，不创建 worktree，不执行提交、推送或合并。
