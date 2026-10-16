# Story Node Orchestrator v1：发布前整体评审记录

评审方式：采用多代理并行评审流程（5 个独立视角 + 置信度过滤），针对两个仓库的
未提交变更（backend 21 个文件 / connector 13 个文件）整体执行。

- 评审时间：2026-10-16
- 评审范围：pms-backend 与 pms-ai-connector 全部未提交 diff 与新增文件
- 参照文档：`docs/superpowers/specs/2026-10-16-story-node-orchestrator-design.md`、
  `docs/superpowers/plans/2026-10-16-story-node-orchestrator.md`、
  `docs/agent-contracts/orchestrator/ai-orchestrator-v1.md`、
  connector `docs/orchestrator-session-contract.md`、历史评审记录与 `fix:` 提交。

## 评审视角与确认无误的结论

| 视角 | 结论 |
| --- | --- |
| 规格与计划合规（领域不变量、明确不做、验收标准） | 单一写入协议、预览先行、幂等与回读链路完整；需求路径无 story 字段泄漏（`@JsonInclude(NON_NULL/NON_EMPTY)` + 回归测试）；事件游标 `(conversation_id, sequence_no)` 端到端一致 |
| 变更面浅层缺陷扫描（空指针/状态机/幂等/序列化） | `AiOperationService` 幂等预留顺序、回读验证空值语义、状态机新增转移、TS 类型收窄等高风险点均验证无恙；未发现高一等级缺陷 |
| git 历史对照（历史加固是否会退化） | 69d7b4c 并发幂等预留、88c68e6 场景绑定持久化预览、35de944 honest state 的精神在新代码中保持或推进 |
| 历史评审意见回归 | 无 skip 伪装、无生产查询 API、门店胜出审计控制合规；两个高置信度问题见下文 |
| 代码注释与实现一致性 | reserve-before-execute、effectiveWritableFields=merge、原 Plan 不可变三处注释与实现一致 |

## 置信度评分（0-100，可信阈值 80）

| # | 问题 | 评分 | 处理 |
| --- | --- | --- | --- |
| 1 | `ai-orchestrator-v1.md` 状态图/事件表/规则 3 未同步新故事语义（NEEDS_INPUT、WRITE_RESULT_UNKNOWN、INPUT_REQUIRED、READBACK_UNKNOWN） | 75 | ✅ 本轮修复（条款 3 重写为“进入 WRITE_RESULT_UNKNOWN 并只能用原幂等键重验”，条款 4 对齐 REQUIRES_NEW 事务边界） |
| 2 | connector 会话契约事件表遗漏 `INPUT_REQUIRED` | 50 | ✅ 本轮顺带修复 |
| 3 | `scripts/release-consistency.test.sh` 硬编码 V69 基线 | 0 | 不处理：V70/V71 在历史提交即已存在，与本次改动无关的遗留问题 |
| 4 | story e2e 创建不可删除夹具前未检查 `PMS_E2E_DATABASE_CLEANUP_CONFIRMED` | 75 | ✅ 本轮修复（测试内 gate + script 增加 env，对齐需求分析验收） |
| 5 | `storyPlanView` catch-all 静默降级无契约字段 | 50 | 未改：上下文快照持久化、resolve 为纯函数，仅在历史数据损坏的可辨识场景触发；保留观察 |
| 6 | development 必填字段缺“新值持久化”merge 回归断言（spec 150 行要求） | 75 | ✅ 本轮修复（`developmentRequiredFieldsPersistNewContentThroughMerge`） |
| 7 | e2e 冲突场景硬编码 `ownerId: 1` | 50 | ✅ 本轮顺带修复（改用已校验的 viewer id） |
| 8 | 多轮补充时用户在中间轮写空值得覆盖后省略字段会丢草稿值 | 25 | 不处理：仅草稿链内、规格以 latest Plan 为最新事实，语义可辩护 |
| 9 | `operationAudit.ts` SQL `?` 替换顺序隐患（首个参数恒为数字） | 50 | 不处理：测试工具、当前调用无注入路径 |
| 10 | spec 119 行对 FieldContract 形状的描述超出实际 record | 50 | 不处理：已提交目标文档中的措辞级偏差，无功能影响 |

## 修复后的回归验证

- backend：StoryNodeWorkbenchPolicyTest 34、AiOrchestratorServiceTest 18、
  FlywayMigrationVersionTest 5、HealthControllerTest 5 —— 全部通过。
- backend 全量套件：1235 例 0 失败 0 错误（DOCKER_HOST + TESTCONTAINERS_RYUK_DISABLED 环境）。
- connector：typecheck 通过；全量 211 passed / 7 skipped（skip 均为需真实环境的 e2e）。
- `git diff --check` 两仓库干净。
- 真实环境验收（一次性隔离数据库）：`verify:real-pms:story-orchestrator` 全链路
  通过，含 NEEDS_INPUT 不产出 operation、跨轮 patch 合并、预览-确认-回读、同键
  重放、异键 409、第二个客户端 session 恢复、旧版本并发冲突与只读操作审计。

## 遗留观察（不阻塞发布）

1. 需求测试脚本基线 `release-consistency.test.sh` 的 V69 硬编码是历史遗留，建议
   在独立提交中将基线改为从迁移目录推导。
2. 计划 T5 的“两个独立 CLI 进程 / 无权限 relation / 模拟回读未知”以进程内第二
   客户端与 backend 单测替代，已在验收文档中声明，但 plan 文档尚未按实际交付改写。
3. Orchestrator 层的 `LABELS`/`questions` 展示文案目前硬编码在服务层，spec 期望
   长期由 policy/上下文投影；可在后续 story intent 扩展时一并整治。
