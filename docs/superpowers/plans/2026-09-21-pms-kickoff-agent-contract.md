# PMS 项目立项节点 Agent 契约接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Feishu 文档中“项目立项与启动”节点的契约沉淀为可版本化、可校验、可被 DSH 获取的 PMS 领域资产，并让 PMS 项目助手在进入该节点时把它作为必读指令使用。契约明确 PMS 项目助手是唯一主 Agent，不再引入产品/开发/测试等专业 Agent。

**Architecture:** PMS 后端保存契约的机器可读源文件和人类可读副本；运行时加载并校验契约，契约异常时只关闭该 Agent 的契约能力，不影响 PMS 核心服务启动。工作流中的真实节点键当前是 `kickoff`，契约业务标识使用 `project-kickoff`，二者通过显式绑定关联，不能靠字符串猜测。通过现有 `/integration/dsh/v1` 鉴权边界暴露只读契约查询能力，DSH 在选中 `project_assistant` 且当前节点为 `kickoff` 时自动拉取契约并注入当前会话的 system prompt。契约查询是 DSH 内部桥接能力，不注册成模型可以选择跳过的普通工具。PMS 仍是业务规则、权限和写入操作的唯一权威，契约只约束 Agent 的决策、确认、工具边界和完成判断。

**Tech Stack:** Spring Boot 3.5、Java 17、Jackson、Spring MVC、JUnit 5、AssertJ、现有 PMS DSH integration facade、DSH system-prompt section API。

**Spec:** `https://ocnb2q1vy6sl.feishu.cn/wiki/V3FQwgCtEiEhwMkpjezcnSi8nOm`（当前修订版 273）。

## 设计约束与闭环

- 契约的唯一业务身份为 `pms-project-assistant/project-kickoff`，版本独立于工作流模板版本；后续修改必须升 `contractVersion`，不得覆盖运行中的旧执行记录。
- 只读能力、写入命令、需要用户确认的动作、完成条件、异常处理、终止条件全部进入契约；不能只写“工作说明”而遗漏安全边界。
- 所有写入继续走现有 preview → 用户明确确认 → execute → 幂等校验 → 结果核验链路，契约不得绕过 `PmsCommandRegistry` 或 PMS 权限检查。
- 批量写入完成后只产生一个刷新指令/刷新批次，避免每个子操作分别刷新 PMS 页面。
- 契约查询接口只返回业务规则，不返回本地文件路径、服务密钥、JWT、数据库连接信息或未授权的内部实现细节。
- “必读”必须可观测：DSH 注入时记录 `contractId`、`contractVersion`、内容摘要和当前节点；拉取失败时阻止节点写入，不允许静默降级成普通 Agent。
- 契约中的业务能力名与实际工具/命令名分层管理：`readCapabilities` 描述业务能力，`toolBindings` 绑定当前已注册的 DSH 工具；`writeCommands` 必须使用 `CommandName` 的真实 code。尚未实现的能力只能标为未启用，不能写入“可执行”列表。
- 契约版本必须进入预览和执行审计，不能只保存在 DSH 内存缓存中；不能把已有的页面 `contextVersion` 偷换成契约版本。

## Phase 0：实现前兼容性核对

- [x] 以当前代码为准建立能力矩阵：工作流节点键、DSH 查询工具、PMS `CommandName`、命令 scope、刷新范围逐项对照 Feishu 契约。
- [x] 将契约身份拆开：`contractId=pms-project-assistant/project-kickoff`，`workflowNodeKeys=[kickoff]`；接口按 `agentId + contractKey` 查询，响应同时返回真实 `workflowNodeKeys`。
- [x] 将目前已经注册、可用于第一个节点的写命令写入第一版：`project.create`、`member.add`、`node.owner.update`、`node.schedule.update`、`task.create`、`task.assign`、`task.update`、`node.complete`。`project.update`、`project.watcher.add`、`project.node.update` 等当前不存在的命令不得假装已经可用；如确有需要，作为后续独立命令补充阶段。
- [x] 将当前可用读取能力绑定到 `pms_project_get`、`pms_project_list`、`pms_task_list`、`pms_query` 及其真实返回字段；成员、节点、模板、用户和组织查询若没有独立 DSH 工具，已收窄能力声明并按缺失输入规则处理，不把未发布能力宣称为可执行能力。
- [x] 确认契约接口沿用现有 `pms:query:read` scope（当前 DSH scope policy 没有 `pms:context:read`），不要在计划中引用一个未注册的 scope。

**Phase 0 review gate:** 通过代码和测试中的真实注册表完成能力矩阵；任何契约能力找不到对应工具、命令或 scope 时，先修正契约/绑定，不进入实现阶段。

## Phase 1：建立契约资产与领域模型

- [x] 新增机器可读契约资源 `src/main/resources/agent-contracts/pms/project-kickoff.yaml`。
  - 元数据：`contractId=pms-project-assistant/project-kickoff`、`agentId=project_assistant`、`contractKey=project-kickoff`、`workflowNodeKeys=[kickoff]`、`contractVersion=1.0.0`、`required=true`、`locale=zh-CN`。
  - 角色：PMS 项目助手独立完成项目经理职责，`specializedAgents=[]`。
  - 入口条件、输入、缺失信息处理、执行步骤、完成标准、失败策略、状态字段、终止条件与 Feishu 文档保持一致。
  - 明确读取能力及绑定：项目详情/节点/成员/任务优先绑定到当前已存在的 `pms_project_get`、`pms_project_list`、`pms_task_list`、`pms_query`；未有真实工具绑定的用户、组织和流程模板查询标为待补充，不作为第一版可执行前置条件。
  - 明确写入命令：`project.create`、`member.add`、`node.owner.update`、`node.schedule.update`、`task.create`、`task.assign`、`task.update`、`node.complete`；不把尚未注册的 `project.update`、`project.watcher.add`、`project.node.update` 放入可执行列表。
  - 对写入动作标注确认策略：关键项目字段、批量任务写入、节点完成/推进必须预览并等待用户明确确认；查询与校验不需要确认。
- [x] 新增人类可读副本 `docs/agent-contracts/pms/project-kickoff.md`，说明来源、版本、适用范围、运行流程和示例，供产品/研发 review；副本中保留 Feishu 白板引用，不把外部链接当作运行时依赖。
- [x] 新增不可变领域模型（建议放在 `src/main/java/com/brad/pms/ai/contract/`）：契约元数据、能力边界、确认策略、完成条件、失败策略和终止条件使用 record/不可变集合表达，避免 Controller 直接操作 YAML Map。
- [x] 新增 `PmsAgentContractLoader` 与 `PmsAgentContractRegistry`。
  - 使用 Spring `ResourcePatternResolver` 扫描 `classpath*:agent-contracts/**/*.yaml`。
  - 使用现有 Jackson 体系解析；若项目依赖中没有 YAML 模块，补充 `jackson-dataformat-yaml`，不要引入第二套序列化方案。
  - 启动时校验必填字段、标识格式、版本格式、重复契约、空能力名、非法确认策略和非法终止条件。
  - 将写入能力与 `CommandName`/`PmsCommandRegistry` 的真实 code 对齐；将读取绑定与实际 DSH capability catalog 对齐。
  - 发现契约无效时，标记 contract registry unhealthy、隐藏该契约的 Agent 注入/写入能力并输出明确健康检查错误；不让整套 PMS 因单份提示契约拼写错误而无法启动。
  - 提供按 `agentId + nodeKey` 查询的方法，并返回内容摘要（SHA-256）和版本。
- [x] 为 loader/registry 增加单元测试：正常加载、重复标识、缺字段、非法命令、非法确认策略、内容摘要稳定性、契约集合不可变。

**Phase 1 review gate:** 运行 `./mvnw -q -DskipTests compile` 和契约单元测试；人工核对 YAML 与 Feishu 文档的 角色、工具、确认、完成、失败、终止六个部分没有缺项，并确认每一项能力都有真实绑定或明确标记为未启用。

## Phase 2：接入 PMS–DSH 契约发现接口

- [x] 新增 DTO，例如 `DshAgentContractDTO`，只输出 DSH 需要的字段：身份、节点、版本、摘要、必读正文、读能力、写能力、确认策略、完成标准、失败策略、终止策略。
- [x] 在 capability discovery 中增加契约描述/查询地址（或等价的兼容扩展），并复用当前已注册的 `pms:query:read` 权限边界；不把契约查询注册为模型可见的普通工具，不新增绕过 Delegation Token 的认证方式。
- [x] 在 `DshIntegrationController` 或独立的 `DshAgentContractController` 增加只读接口：
  - `GET /integration/dsh/v1/agent-contracts/{agentId}/{contractKey}`
  - 仅允许注册 Agent 和已加载契约；不存在返回明确 404/业务错误。
  - 请求必须经过现有 DSH delegation route policy；DSH token 的 scope 不能扩大到契约未声明的写入能力；响应返回绑定的 `workflowNodeKeys`，由 DSH 据此判断当前节点是否匹配。
- [x] 为接口增加 controller/service 测试：合法 Agent/节点返回当前版本、未知节点拒绝、响应不含 secrets、DSH scope 路由策略拒绝不足权限的请求。
- [x] 在 capabilities 相关测试中确认原有 `tools`、`commands`、`queries` 兼容，新增工具不会破坏旧 DSH 版本。
- [x] 在预览请求和 `pms_ai_operation` 审计记录中增加独立的 `contractId`/`contractVersion`（可选字段向后兼容），执行时校验预览使用的契约版本仍然是该 Agent 当前允许的版本；新增 Flyway migration，不复用 `context_version`。

**Phase 2 review gate:** 用 MockMvc 或现有 integration test 验证“登录用户 → DSH 委派 token → 拉契约”闭环，并确认 PMS 后端是唯一权限裁决方；再验证契约版本能随 preview/execute 审计记录保存。

## Phase 3：在 DSH 中实现“节点进入即必读”

工作目录为 `deepseek-harness`；不把 PMS 的 YAML 复制到 DSH，避免两份规则漂移。契约获取由 DSH PMS plugin 的内部 client 完成，不加入 `ctx.tools.register` 的模型工具集合。

- [x] 在 `packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts` 增加内部 `getAgentContract(agentId, contractKey)` 方法，复用现有 PMS session/token 生命周期、capability snapshot 和错误分类；更新 `PmsCapabilities` 类型以读取契约描述。
- [x] 在 `PmsContextLocator`/上下文快照中补充可稳定获取的 `currentNodeKey`，或明确通过一次 `pms_project_get` 解析节点键；不能只用 `nodeId` 与契约字符串匹配。
- [x] 在 PMS 项目助手 Agent preset 的运行上下文中，根据当前 PMS 会话的 `agentId`、实际 `currentNodeKey=kickoff` 拉取 `project-kickoff` 契约，并作为独立且可追踪的 system-prompt section 注入，顺序为：Agent 身份 → PMS 全局规则 → 节点契约 → skill/tool catalog。
- [x] 先用 DSH 现有“请求上下文/system prompt 更新”机制做一个最小 spike 和快照测试；已使用会话/请求组装阶段注入，而不是静态启动文本或 user message。
- [x] 契约内容只在节点变化或契约版本变化时重新拉取；同一会话内复用相同 `contractVersion`，避免每轮聊天重复请求。
- [x] 注入 section 的 metadata 中记录 `contractId`、版本、摘要、稳定获取时间和项目/节点上下文；不得把契约正文伪装成用户消息。
- [x] 拉取失败、摘要不匹配、契约版本缺失或当前节点没有契约时：
  - 允许只读诊断和向用户解释；
  - 禁止执行该节点的写入命令；
  - 给出可定位的错误码和 PMS/DSH request id。
- [x] 复用 DSH 的 system-message 会话持久化记录实际注入的契约版本、摘要和获取时间；写入操作另外由 PMS `pms_ai_operation.contract_version` 审计，避免把页面 `contextVersion` 当成契约版本。
- [x] 增加 DSH 单元/集成测试：首次拉取、缓存命中、节点解析、接口失败 fail-closed、契约正文出现在模型可见 system prompt、契约版本进入写入预览、批量写入统一刷新。

**Phase 3 review gate:** 用真实 PMS 登录态启动 DSH，打开 PMS 项目并进入真实节点 `kickoff`，确认 system prompt 中可看到契约 section；尝试未确认的创建/任务批量写入必须被阻止，确认后才允许走 preview/execute；契约拉取失败时仍允许只读，但写入必须 fail-closed。

## Phase 4：用第一个节点完成端到端验证

- [x] 准备只读场景：用户询问当前项目、节点、成员、任务，Agent 只能使用契约声明的读取能力，并在回答中引用本次查询结果。
- [ ] 准备信息不完整场景：只给项目名称和截止日期时，Agent 询问项目经理、成员、业务线、优先级、等级、开始日期等缺失信息，不擅自猜测身份、权限和关键日期。
- [x] 准备写入场景：
  1. 生成项目/任务预览；
  2. 展示字段、风险、后端警告、操作编号；
  3. 用户明确确认；
  4. 用同一操作编号和幂等键执行；
  5. 核验 PMS 返回结果；
  6. 批量操作统一刷新一次当前 PMS 页面；
  7. 输出结果和下一节点预览，不自动推进节点。
- [x] 准备失败场景：鉴权失败立即停止写入；系统繁忙先按幂等键查状态，不盲目重复；参数校验失败回到补充信息；执行结果未知时标记待核验并禁止重复创建。
- [x] 准备节点完成场景：缺少必填字段、未完成任务、未填交付物、未完成检查项时不得完成节点；全部满足后先展示完成/推进预览，等待用户确认。
- [x] 运行 PMS 后端相关 Maven 测试、DSH 相关 Vitest/E2E 测试，并记录测试时间、契约版本、PMS/DSH commit、请求编号。

**Phase 4 review gate:** 形成一份可复现的 E2E 记录，证明“契约加载 → Agent 注入 → 只读查询 → 预览确认 → 幂等写入 → 结果核验 → 统一刷新 → 节点完成判断”完整闭环。

### Phase 4 执行记录（2026-09-21）

- 记录文档：`docs/agent-contracts/pms/project-kickoff-e2e-record.md`；驱动脚本：`deepseek-harness/packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts`（无 `PMS_E2E_*` 环境变量时自动跳过）。
- 真实联调结果：`6 passed`（登录 → 授权码 → 委派 token → 契约加载 → 注入 → 只读 → 预览 → 确认门禁 → 幂等执行 → 结果核验 → 单次刷新 → 节点完成判断），契约 `1.0.0`，摘要 `557dd9be…`。
- 全量回归：PMS `mvn test` 562 项 / 0 error（1 项 `CommandPreviewServiceScopeTest` 为 HEAD 上已存在的失效断言，与本计划无关）；DSH `dsh-pms` 45 项通过。
- 联调发现并修复一处缺陷：命令预览会把当前页面的 `projectId/nodeId` 注入所有命令，导致项目详情页的 `project.create` 被 PMS 拒绝（`不支持的 project.create 参数: projectId`）。现改为按 PMS 能力目录声明的参数注入，并保持“无契约不发起任何 PMS 请求”的 fail-closed 顺序。
- 未勾选的一项：`信息不完整场景` 只验证到契约层（必读文本包含入口条件、必需输入、缺失信息处理规则），未执行模型自然语言轮次——当前环境没有 DSH 所需的模型凭据。待具备模型凭据的 DSH 会话补齐后勾选。

## Phase 5：补齐项目字段更新命令（2026-09-21 追加）

背景：Phase 4 联调中发现 Agent 无法修改项目描述和项目整体排期——PMS 的 AI 命令层当时只有 `project.create`/`project.archive`/`project.delete`，没有项目字段更新命令（页面侧的 `PUT /projects/{id}` 存在，只是没有包装成命令）。按 Phase 0 的约定，这属于"后续独立命令补充阶段"。

- [x] PMS 新增 `project.update` 命令 `src/main/java/com/brad/pms/ai/command/project/UpdateProjectCommand.java`。
  - 只接受 `projectId`（必填）、`name`、`description`、`priority`、`projectLevel`、`orgUnitId`、`startDate`、`endDate`，其它参数一律拒绝。
  - 部分更新语义：未提供的字段保留原值，显式传 `null` 清空描述或日期；日期顺序校验与页面一致。
  - 复用页面同一条写入链路 `ProjectPermissionService.requireProjectWritable` + `ProjectService.update`，不新增绕过权限的写入通道。
  - 预览快照携带 `projectVersion`，执行时做乐观并发校验（`PmsCommandVersionGuard`），过期预览返回冲突而不是覆盖别人的修改。
  - 明确不碰项目成员、关注人和项目经理（`memberIds`/`followerIds`/`projectManagerId` 一律不传），保证改字段不会顺手清掉成员。
- [x] `PmsCommandMetadata` 增加 `project.update` 元数据：`pms:project:write` + 预览/执行权限、risk `high`、refreshScopes `project-detail/project-list/project-dashboard`。
- [x] 契约 `src/main/resources/agent-contracts/pms/project-kickoff.yaml` 的 `writeCommands`、`confirmationPolicies` 增加 `project.update`；人类可读副本 `docs/agent-contracts/pms/project-kickoff.md` 同步说明部分更新语义。
- [x] DSH 工具白名单 `packages/pms/dsh-pms/src/tools/command.ts` 的 `COMMANDS` 增加 `project.update`（否则工具参数枚举会先拦下）。
- [x] 测试：新增 `UpdateProjectCommandTest`（7 项：预览只改给定字段、拒绝未知参数、拒绝空名称、拒绝倒挂排期、执行合并当前值且不动成员、过期预览冲突、显式 null 清空描述）；`PmsAgentContractRegistryTest` 更新为 9 个写入命令；live E2E 增加"改项目描述 + 项目截止日期"一步。
- [x] 验证：`mvn -q -o -DskipTests package`；命令/契约/控制器测试通过；live E2E `7 passed`（含新增场景，证据见 `docs/agent-contracts/pms/project-kickoff-e2e-evidence.json` 的 `project-update.preview` / `project-update.verified`）。

## 兼容与回滚

- 第一版只新增契约发现能力和 DSH 注入，不修改既有 PMS 工作流模板结构，不改变现有命令的请求/响应格式。
- 契约版本升版采用新增资源或版本目录，旧版本保留到所有运行中的执行记录结束；不能原地改写正在使用的版本。
- 可通过关闭 DSH 的 contract injection 开关回退到旧 prompt，但 PMS 后端仍保留只读契约接口，便于诊断；不能通过回退开关绕过 PMS 的权限、preview/execute 和幂等校验。
- 不修改当前用户已有的 `infra/keycloak/realm-pms-dev.json`、`docs/product-specs/pms-dsh-agent-platform.md` 和 `docs/superpowers/plans/2026-09-18-pms-dsh-agent-platform.md`。

## 实施顺序

1. Phase 0：真实节点/工具/命令/scope 能力矩阵。
2. Phase 1：契约资源、模型、加载校验、单元测试。
3. Phase 2：PMS DSH 契约发现接口、权限、审计字段和接口测试。
4. Phase 3：DSH client、动态 system-prompt 注入、会话持久化、DSH 测试。
5. Phase 4：端到端验证、每阶段 review 结论和最终回归。

每个阶段完成后先执行对应 review gate，再进入下一阶段；发现契约语义、权限范围或执行结果不闭环时暂停，不把问题带到下一阶段。
