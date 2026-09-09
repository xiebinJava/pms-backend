# 开发测试与项目控制工作台实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to execute this plan task-by-task with review checkpoints.

**Goal:** 将开发节点详情中的静态开发树 Demo 升级为数据库驱动、可保存、可受权限控制的轻量开发测试与项目控制工作台。

**Architecture:** 后端以开发基线、专题、故事三张表保存规范化数据，通过一个 GET/PUT 聚合接口返回树和派生汇总；前端保留当前树形视觉结构，把静态 seed 替换成接口状态，并以小型弹窗承载新增和状态编辑。专题和故事负责人均复用项目成员选择，并在树表统一负责人列展示。

**Tech stack:** Spring Boot, MyBatis-Plus, Flyway, H2/OceanBase-compatible SQL, React/Vue-compatible existing Vue frontend, Ant Design Vue, Vitest/Node assertions, Playwright fallback smoke test.

## Task 1: 固化规格并建立数据库模型

**Files:** `docs/superpowers/specs/2026-09-07-development-control-workbench-design.md`, `src/main/resources/db/migration/V27__development_control_workbench.sql`, `src/main/resources/schema.sql`

- [x] 先写迁移测试，覆盖三张表、唯一键、状态/进度字段和外键关联。
- [x] 添加开发基线、专题、故事表及索引。
- [x] 同步 H2 基线 schema，保证新环境和迁移环境一致。

## Task 2: 实现后端领域对象、聚合接口和权限校验

**Files:** `src/main/java/com/brad/pms/domain/development/**`, `src/main/java/com/brad/pms/mapper/**`, `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`, `src/main/java/com/brad/pms/controller/NodeDevelopmentControlController.java`

- [x] 先写服务测试：空工作台、汇总进度、非法进度、跨专题故事、只读权限、版本冲突。
- [x] 添加 DO、Mapper、DTO/Command 和枚举。
- [x] 实现 GET 聚合和 PUT 全量保存，后端派生汇总并做版本检查。
- [x] 接入既有 `ProjectPermissionService` 和开发节点校验。
- [x] 更新 OpenAPI 文档。

## Task 3: 替换前端静态 Demo 为真实数据

**Files:** `src/api/node-development-control.ts`, `src/views/project/detail/development-control.ts`, `src/views/project/detail/components/DevelopmentControlWorkbench.vue`, `src/views/project/detail/index.vue`

- [x] 先补充前端聚合/映射测试，覆盖空数据、状态展示和专题进度。
- [x] 添加 API client 和请求错误处理。
- [x] 将工作台改为接收项目/节点上下文，加载接口并显示 loading/empty/error。
- [x] 保留树形结构和右侧专题摘要，删除硬编码 Demo seed。

## Task 4: 实现轻量编辑闭环

**Files:** `src/views/project/detail/components/DevelopmentControlWorkbench.vue`, `src/views/project/detail/development-control.test.mjs`

- [x] 先补测试：新增专题、新增故事、更新状态后重新计算汇总。
- [x] 增加“新增专题”和“新增故事”入口，编辑范围只限于必要字段。
- [x] 用“更新开发状态”弹窗批量保存当前专题故事的状态、进度和阻塞原因。
- [x] 保存成功后重新加载并保留当前专题/展开状态；无权限时只读。

## Task 5: 验证与回归

**Files:** no new production files

- [x] 运行后端测试、前端测试、类型检查、生产构建和 `git diff --check`。
- [x] 启动前后端，用 Playwright fallback 验证开发节点、空状态、新增和更新路径。
- [x] 完成桌面端截图检查，并复核窄屏布局的响应式 CSS 分支，未发现明显回归。
- [x] 完成一次代码 review，重点检查权限、版本冲突、空数据和旧数据库升级兼容性。

## Task 6: 对齐树表并增加故事负责人

**Files:** `src/main/resources/db/migration/V28__development_control_story_owner.sql`, `src/main/java/com/brad/pms/service/NodeDevelopmentControlService.java`, `src/views/project/detail/components/DevelopmentControlWorkbench.vue`

- [x] 先补测试：故事负责人必须是项目成员，V28 必须提供增量字段迁移，树表使用五列统一网格。
- [x] 增加故事负责人字段的请求、响应、持久化和项目成员校验链路。
- [x] 在项目/专题/故事树中统一显示负责人列，并将故事行缩进放在名称单元格内，保持各层级列线对齐。
- [x] 在轻量编辑弹窗中增加故事负责人选择，并随现有保存动作提交。
- [x] 完成定向测试、全量测试、构建和 Playwright 页面回归。
