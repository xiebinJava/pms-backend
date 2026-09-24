# PMS Agent 自然语言操作加固实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让「用自然语言操作 PMS」在安全上不留缺口、在体验上不依赖运气：把"用户确认"变成 PMS 侧可审计的状态、把"当前项目/节点"变成服务端可恢复的绑定、把"有哪些命令"变成单一来源。

**Architecture:** 写入链路从 `preview → execute` 改为 `preview → confirm → execute`，三态由 PMS 落库并把关；会话上下文新增 `pms_dsh_session_context` 绑定表，DSH 本地上下文缺失时从服务端恢复；DSH 的命令白名单不再静态硬编码，改为按 PMS capability 目录在调用时校验，契约加载时增加"契约 ↔ 命令元数据"一致性交叉校验。

**Tech Stack:** PMS 侧 Java 17 / Spring Boot 3.5 / MyBatis-Plus / Flyway / JUnit5 + AssertJ + Mockito；DSH 侧 TypeScript / Cordis 插件 / vitest / tsdown。

**Spec:** 本计划实现的是 2026-09-21 设计评审确认的三项（服务端确认状态机、会话上下文服务端化、命令元数据单一来源）。评审依据：本项目既有计划 `docs/superpowers/plans/2026-09-21-pms-kickoff-agent-contract.md`、E2E 记录 `docs/agent-contracts/pms/project-kickoff-e2e-record.md`，以及 MCP tools 规范（人工确认/human-in-the-loop）、OpenAI Agents SDK（Human in the loop）、LangGraph（interrupt + durable resume）、jackwener/OpenCLI（知识层与 verify 工程化）的对照结论。

## 背景与问题（已复现，非推测）

1. **"确认"目前只是客户端约定。** `AiOperationService.execute` 只校验"本人 + 状态 PREVIEW + 未过期 + 契约/上下文版本一致"。任何拿到 operationId 的客户端都能直接执行，PMS 无法区分"用户点过确认"与"模型自己决定执行"。用户实际遇到的是：DSH 重启后上下文丢失，写入被拒；而换一个客户端就能绕过。
2. **会话上下文只活在 DSH 进程内存里。** 项目/节点来自内嵌 PMS 工作区发布的 locator，进程重启即丢，用户看到的是 `PMS 当前节点没有已注册契约（节点：undefined）`——既不可定位，也无法由 Agent 主动恢复。
3. **加一条命令要改四处。** `CommandName`、`PmsCommandMetadata`、契约 YAML、DSH `tools/command.ts` 的 `COMMANDS` 枚举。今天加 `project.update` 就是四处同改，漏一处就会出现"契约里有、工具枚举里没有"的静默不一致。

## Global Constraints

- PMS 是唯一的业务规则与写入权威；DSH 只能走 `/integration/dsh/v1` 的 preview → confirm → execute，不得新增绕过权限/校验的写入通道。
- 失败关闭：无契约、无上下文、无确认时一律拒绝写入，且不得静默降级为普通对话；错误必须可定位（明确错误码）。
- 契约版本（`contract_version`）与页面数据版本（`context_version`）分开记录，不得互相顶替。
- 写入命令必须使用真实注册的 `CommandName` code；未注册的能力不得写进契约。
- 不修改 `infra/keycloak/realm-pms-dev.json`、`docs/product-specs/pms-dsh-agent-platform.md`、`docs/superpowers/plans/2026-09-18-pms-dsh-agent-platform.md`。
- 本地跑 Spring 集成测试需要 `DOCKER_HOST=unix://$HOME/.colima/<colima-profile>/docker.sock TESTCONTAINERS_RYUK_DISABLED=true`。
- DSH 仓库当前工作目录 `deepseek-harness`；PMS 仓库 `pms-backend`。

## Review Focus

下列五类输入/失败模式最容易伤到真实用户，本计划为每一条指定了承载它的测试（写在对应 Task 里）：

1. **并发修改**：预览后别人改了项目/节点 → 确认或执行必须失败并要求重新预览，绝不能覆盖别人的修改（Task 3、Task 5）。
2. **过期确认**：预览已过期、或确认后放置很久才执行 → 必须明确拒绝，不得执行（Task 1、Task 3）。
3. **上下文缺失/错位**：DSH 重启、跨项目对话、页面与工具读到的项目不一致 → 必须 fail-closed 且给出可定位错误码（Task 8、Task 9）。
4. **重复确认/重复执行**：同一 operationId 重复调用 → 幂等，不产生第二次副作用（Task 1、Task 3）。
5. **契约与元数据漂移**：命令被下线、参数变化、契约声明了未发布的能力 → 加载期即失败或该能力不可用，而不是运行期才崩（Task 11、Task 10）。

## 决策记录（2026-09-21，用户确认）

| # | 决策项 | 取值 | 落到哪里 |
| --- | --- | --- | --- |
| 1 | 执行方式 | Native：本会话内按计划逐任务实现，最后一次整分支复核 | 全篇 |
| 2 | 基线 | 先把现有未提交改动整理成基线提交，再开始本计划 | 执行前准备 |
| 3 | 确认渠道 | 由 DSH 审批 UI 产生：模型调用 `pms_command_confirm` 触发审批，用户点"允许"后才落库；`confirm_source` 记录来源 | Task 1、Task 4 |
| 4 | 执行窗口 | 预览 TTL 保持 10 分钟；确认时把执行窗口重置为 15 分钟（`CONFIRM_TTL_MINUTES` 常量） | Task 1、Task 3 |
| 5 | 发布方式 | PMS 与 DSH 同版本发布，不做旧客户端的灰度兼容层 | 兼容与回滚 |
| 6 | 绑定来源 | 允许 `page` 与 `agent-read` 两种来源，审计区分；预览必须展示目标项目名称与编号 | Task 9 |
| 7 | 免确认档 | 暂不引入，9 个写命令全部 `preview-and-confirm` | Task 11 |

### 设计预留：将来去掉"人点击"

用户计划后续把确认从"人工点按"改为模型自动确认（因为每次点击太繁琐）。保留脚手架，使这次变更只是一次策略改动：

- **状态机不随点击一起删**：即使模型可自动确认，`PREVIEW → CONFIRMED → SUCCEEDED` 仍然承担"禁止同轮预览即执行"、两阶段审计、可恢复执行三个职责。
- **审计必须区分来源**：`pms_ai_operation.confirm_source` 取 `dsh-approval`（人工）或 `agent-auto`（自动）。**它只是自报的提示值，不是安全控制**——PMS 真正的把关是状态机 + 用户身份 + 版本新鲜度，任何安全判断都不得依赖这一列。
- **免点击按命令授权**：将来把契约的 `confirmationPolicies` 增加第二个取值（如 `preview-and-auto-confirm`），由 PMS 校验"该命令是否允许自动确认"。高危命令（`project.delete`、`project.archive`、`node.rollback`、`member.remove`、`node.complete`）建议永久保留人工确认。

## 不在本计划范围（后续独立计划）

- **契约分层 + 本地覆盖 + 健康标记**（借鉴 OpenCLI sitemap）：全局规则 / 节点契约 / 工作流 / pitfalls 分层与 overlay，以及命令健康状态传播。它改动契约文件格式与加载器语义，独立成篇更安全。
- **verify fixture + 静默失败清单 + trace/评测**：每命令一份可重复 fixture、PMS 版"verify 通过但数据是错的"清单、OpenTelemetry GenAI 语义约定与金标集评测。
- **长流程编排（Temporal/Restate/Camunda）**：等多节点、会签、超时升级出现再上；当前用数据库状态机 + 定时补偿足够。

## File Structure

PMS 侧（`pms-backend`）：

| 文件 | 职责 |
| --- | --- |
| `src/main/resources/db/migration/V50__add_ai_operation_confirmation.sql` | 新增 `confirmed_at` / `confirmed_by` 列与索引 |
| `src/main/java/com/brad/pms/ai/command/OperationConfirmRequest.java` | 确认请求 DTO（新增） |
| `src/main/java/com/brad/pms/ai/command/AiOperationService.java` | 三态状态机：`preview` / `confirm` / `execute` |
| `src/main/java/com/brad/pms/controller/DshCommandController.java` | 新增 `POST /operations/{id}/confirm` |
| `src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java` | 新增确认路径的 scope 规则 |
| `src/main/resources/db/migration/V51__add_dsh_session_context.sql` | 会话上下文绑定表（新增） |
| `src/main/java/com/brad/pms/entity/DshSessionContextDO.java`、`mapper/DshSessionContextMapper.java` | 绑定表访问（新增） |
| `src/main/java/com/brad/pms/integration/dsh/service/DshSessionContextService.java` | 绑定/解析/过期（新增） |
| `src/main/java/com/brad/pms/controller/DshContextController.java` | `POST /context/bind`、`GET /context/{dshSessionId}`（新增） |
| `src/main/java/com/brad/pms/ai/contract/PmsAgentContractRegistry.java` | 新增 `findByNodeKey` 与"契约 ↔ 命令元数据"交叉校验 |

DSH 侧（`deepseek-harness`）：

| 文件 | 职责 |
| --- | --- |
| `packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts` | 新增 `confirmOperation`、`bindContext`、`resolveContextBinding` |
| `packages/pms/dsh-pms/src/tools/command.ts` | 新增 `pms_command_confirm` 工具；去掉静态命令枚举 |
| `packages/pms/dsh-pms/src/index.ts` | 上下文回退到服务端绑定；新增错误码与提示文案 |
| `packages/pms/dsh-pms/tests/dsh-pms.spec.ts` | 单元测试 |
| `packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts` | live E2E 改为三步流程 |

执行前准备：本计划假定仓库已有一个干净基线。当前两个仓库都有大量未提交改动（PMS 26 项、DSH 8 项），执行者应先与用户确认：把这些改动整理成基线提交，或明确"继续在脏工作区上叠加"。每个 Task 末尾的 commit 步骤以此为前提前提。

---

## Phase 1：服务端确认状态机

**Phase 1 review gate:** PMS 侧演示"PREVIEW 直接执行被拒 / confirm 后执行成功 / 过期或版本变化被拒"，DSH live E2E 走通三步流程，且 `SELECT status, confirmed_by, confirmed_at FROM pms_ai_operation` 能看到确认人。

### Task 1: 三态状态机与 confirm 服务方法

**Files:**
- Create: `src/main/resources/db/migration/V50__add_ai_operation_confirmation.sql`
- Create: `src/main/java/com/brad/pms/ai/command/OperationConfirmRequest.java`
- Modify: `src/main/java/com/brad/pms/ai/command/AiOperationService.java`
- Modify: `src/main/java/com/brad/pms/entity/AiOperationDO.java`
- Test: `src/test/java/com/brad/pms/ai/command/AiOperationConfirmTest.java`

**Interfaces:**
- Consumes: `AiOperationMapper.selectByIdForUpdate(String)`、`PmsAgentContractRegistry.isCommandAllowed(contractId, version, command)`。
- Produces:
  - `AiOperationDO.getConfirmedAt()/setConfirmedAt(LocalDateTime)`、`getConfirmedBy()/setConfirmedBy(Long)`
  - `AiOperationService.CONFIRMED`（`"CONFIRMED"` 常量）
  - `CommandPreview confirm(Long userId, OperationConfirmRequest request)`
  - `record OperationConfirmRequest(String operationId, String contextId, String contextVersion, String contractId, String contractVersion)`

- [ ] **Step 1: 写失败的测试**

```java
package com.brad.pms.ai.command;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationConfirmTest {

    private static final String CONTRACT = "pms-project-assistant/project-kickoff";

    @Test
    void confirmMovesAPreviewToConfirmedAndRecordsTheActor() {
        Fixture fixture = fixture("PREVIEW", LocalDateTime.now().plusMinutes(5));
        when(fixture.contractRegistry.isCommandAllowed(CONTRACT, "1.0.0", "project.create")).thenReturn(true);

        CommandPreview confirmed = fixture.service.confirm(7L, new OperationConfirmRequest(
                "operation-1", "project-22", "context-v7", CONTRACT, "1.0.0"));

        assertThat(fixture.operation().getStatus()).isEqualTo("CONFIRMED");
        assertThat(fixture.operation().getConfirmedBy()).isEqualTo(7L);
        assertThat(fixture.operation().getConfirmedAt()).isNotNull();
        assertThat(fixture.operation().getConfirmSource()).isEqualTo("dsh-approval");
        // 决策 4：确认把执行窗口重置为 15 分钟。
        assertThat(fixture.operation().getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(14));
        assertThat(confirmed.operationId()).isEqualTo("operation-1");
        verify(fixture.mapper()).updateById(fixture.operation());
    }

    @Test
    void confirmIsIdempotentForAnAlreadyConfirmedOperation() {
        Fixture fixture = fixture("CONFIRMED", LocalDateTime.now().plusMinutes(5));

        fixture.service().confirm(7L, new OperationConfirmRequest(
                "operation-1", "project-22", "context-v7", CONTRACT, "1.0.0"));

        assertThat(fixture.operation().getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmMarksAndRejectsAnExpiredPreview() {
        Fixture fixture = fixture("PREVIEW", LocalDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> fixture.service().confirm(7L, new OperationConfirmRequest(
                "operation-1", "project-22", "context-v7", CONTRACT, "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作预览已过期");
        assertThat(fixture.operation().getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void confirmRejectsAnotherUser() {
        Fixture fixture = fixture("PREVIEW", LocalDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> fixture.service().confirm(9L, new OperationConfirmRequest(
                "operation-1", "project-22", "context-v7", CONTRACT, "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权确认此操作");
    }

    @Test
    void confirmRejectsAMismatchedContextBinding() {
        Fixture fixture = fixture("PREVIEW", LocalDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> fixture.service().confirm(7L, new OperationConfirmRequest(
                "operation-1", "project-24", "context-v8", CONTRACT, "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目上下文不一致");
    }

    private static Fixture fixture(String status, LocalDateTime expiresAt) {
        AiOperationMapper mapper = mock(AiOperationMapper.class);
        PmsAgentContractRegistry contractRegistry = mock(PmsAgentContractRegistry.class);
        AiOperationDO operation = new AiOperationDO();
        operation.setId("operation-1");
        operation.setUserId(7L);
        operation.setCommandName(CommandName.PROJECT_CREATE.code());
        operation.setStatus(status);
        operation.setExpiresAt(expiresAt);
        operation.setContextId("project-22");
        operation.setContextVersion("context-v7");
        operation.setContractId(CONTRACT);
        operation.setContractVersion("1.0.0");
        operation.setPreviewJson("{\"operationId\":\"operation-1\",\"command\":\"project.create\","
                + "\"contextVersion\":\"context-v7\",\"warnings\":[],\"changes\":[]}");
        when(mapper.selectByIdForUpdate("operation-1")).thenReturn(operation);
        AiOperationService service = new AiOperationService(
                mapper, mock(PmsCommandRegistry.class),
                new ObjectMapper().findAndRegisterModules(), contractRegistry);
        return new Fixture(mapper, contractRegistry, operation, service);
    }

    private record Fixture(
            AiOperationMapper mapper,
            PmsAgentContractRegistry contractRegistry,
            AiOperationDO operation,
            AiOperationService service) {
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest=AiOperationConfirmTest test`
Expected: 编译失败，提示找不到 `OperationConfirmRequest` 与 `confirm` 方法（`cannot find symbol`）。

- [ ] **Step 3: 加迁移与实体字段**

`V50__add_ai_operation_confirmation.sql`：

```sql
ALTER TABLE pms_ai_operation
    ADD COLUMN confirmed_at DATETIME NULL AFTER executed_at,
    ADD COLUMN confirmed_by BIGINT NULL AFTER confirmed_at,
    ADD COLUMN confirm_source VARCHAR(32) NULL AFTER confirmed_by;

CREATE INDEX idx_pms_ai_operation_confirmed
    ON pms_ai_operation (status, confirmed_at);
```

`AiOperationDO` 增加：

```java
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
    private String confirmSource;
```

- [ ] **Step 4: 新增 DTO**

`src/main/java/com/brad/pms/ai/command/OperationConfirmRequest.java`（与 `OperationExecuteRequest` 同款校验，去掉幂等键）：

```java
package com.brad.pms.ai.command;

/** A user confirmation for one previously previewed operation. */
public record OperationConfirmRequest(
        String operationId,
        String contextId,
        String contextVersion,
        String contractId,
        String contractVersion,
        String confirmSource) {

    public OperationConfirmRequest(String operationId, String contextId, String contextVersion,
                                   String contractId, String contractVersion) {
        this(operationId, contextId, contextVersion, contractId, contractVersion, null);
    }

    public OperationConfirmRequest {
        operationId = requireText(operationId, "operationId");
        contextId = requireText(contextId, "contextId");
        contextVersion = requireText(contextVersion, "contextVersion");
        contractId = requireText(contractId, "contractId");
        contractVersion = requireText(contractVersion, "contractVersion");
        confirmSource = confirmSource == null || confirmSource.isBlank() ? "dsh-approval" : confirmSource.trim();
        if (!java.util.Set.of("dsh-approval", "agent-auto").contains(confirmSource)) {
            throw new IllegalArgumentException("unsupported confirmSource: " + confirmSource);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
```

- [ ] **Step 5: 实现 confirm 与共享绑定校验**

在 `AiOperationService` 增加常量与 `previewOf` 反序列化，并把原有的 `validateExecutionBinding(operation, request)` 泛化为 `validateBinding(operation, contextId, contextVersion, contractId, contractVersion, action)`，`execute` 调用时传 `"执行"`：

```java
    private static final String CONFIRMED = "CONFIRMED";
    private static final int CONFIRM_TTL_MINUTES = 15;

    @Transactional
    public CommandPreview confirm(Long userId, OperationConfirmRequest request) {
        AiOperationDO operation = operationMapper.selectByIdForUpdate(request.operationId());
        if (operation == null) throw BusinessException.notFound("操作预览不存在");
        if (!operation.getUserId().equals(userId)) throw BusinessException.forbidden("无权确认此操作");
        if (CONFIRMED.equals(operation.getStatus())) return previewOf(operation);
        if (!PREVIEW.equals(operation.getStatus())) {
            throw BusinessException.conflict("操作当前状态不可确认");
        }
        if (operation.getExpiresAt() == null || operation.getExpiresAt().isBefore(LocalDateTime.now())) {
            operation.setStatus(EXPIRED);
            operationMapper.updateById(operation);
            throw BusinessException.conflict("操作预览已过期，请重新生成预览");
        }
        validateBinding(operation, request.contextId(), request.contextVersion(),
                request.contractId(), request.contractVersion(), "确认");
        validateContractBinding(operation.getContractId(), operation.getContractVersion(), operation.getCommandName());
        operation.setStatus(CONFIRMED);
        operation.setConfirmedBy(userId);
        operation.setConfirmedAt(LocalDateTime.now());
        operation.setConfirmSource(request.confirmSource());
        // 决策 4：确认时重置执行窗口（预览 TTL 不变，仍是 10 分钟）。
        operation.setExpiresAt(LocalDateTime.now().plusMinutes(CONFIRM_TTL_MINUTES));
        operationMapper.updateById(operation);
        return previewOf(operation);
    }

    private CommandPreview previewOf(AiOperationDO operation) {
        CommandPreview stored = read(operation.getPreviewJson(), CommandPreview.class);
        return new CommandPreview(operation.getId(), stored.command(), operation.getExpiresAt() == null
                ? stored.expiresAt()
                : operation.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant(),
                stored.contextVersion(), stored.warnings(), stored.changes(), stored.refreshScopes());
    }

    private void validateBinding(AiOperationDO operation, String contextId, String contextVersion,
                                 String contractId, String contractVersion, String action) {
        if (operation.getContractId() != null
                && (!Objects.equals(operation.getContractId(), contractId)
                || !Objects.equals(operation.getContractVersion(), contractVersion))) {
            throw BusinessException.conflict(action + "请求与原契约不一致，请重新生成预览");
        }
        if (operation.getContextId() != null
                && (!Objects.equals(operation.getContextId(), contextId)
                || !Objects.equals(operation.getContextVersion(), contextVersion))) {
            throw BusinessException.conflict(action + "请求与原项目上下文不一致，请重新生成预览");
        }
        if (operation.getContractId() == null && contractId != null) {
            throw BusinessException.conflict("原操作预览未绑定契约，请重新生成预览");
        }
    }
```

`execute` 中原来的两行改为一处调用：

```java
        validateBinding(operation, request.contextId(), request.contextVersion(),
                request.contractId(), request.contractVersion(), "执行");
```

（`read(String, Class<T>)` 若不存在，按现有 `read(String, TypeReference)` 的写法补一个同样处理 `JsonProcessingException` 的私有重载。）

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest=AiOperationConfirmTest test`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

- [ ] **Step 7: 提交**

```bash
git add src/main/resources/db/migration/V50__add_ai_operation_confirmation.sql \
        src/main/java/com/brad/pms/ai/command/OperationConfirmRequest.java \
        src/main/java/com/brad/pms/ai/command/AiOperationService.java \
        src/main/java/com/brad/pms/entity/AiOperationDO.java \
        src/test/java/com/brad/pms/ai/command/AiOperationConfirmTest.java
git commit -m "feat: confirm AI operations server-side before execute"
```

### Task 2: 确认接口与路由权限

**Files:**
- Modify: `src/main/java/com/brad/pms/controller/DshCommandController.java`
- Modify: `src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java`
- Test: `src/test/java/com/brad/pms/controller/DshCommandControllerTest.java`
- Test: `src/test/java/com/brad/pms/security/AiDelegationRoutePolicyTest.java`

**Interfaces:**
- Consumes: Task 1 的 `AiOperationService.confirm(Long, OperationConfirmRequest)`、`OperationConfirmRequest`。
- Produces: `POST /integration/dsh/v1/operations/{operationId}/confirm`，请求体 `ConfirmBody(String contextId, String contextVersion, String contractId, String contractVersion, String source)`（`source` 缺省按 `dsh-approval` 处理），返回 `ResponseResult<CommandPreview>`；`DshCommandController` 构造函数新增 `AiOperationService` 参数（顺序放在 `executionService` 之后）。

- [ ] **Step 1: 写失败的控制器测试**

在 `DshCommandControllerTest` 增加（沿用该测试类现有的 mock 风格）：

```java
    @Test
    void confirmDelegatesToTheOperationServiceWithTheSessionBinding() {
        AiOperationDO confirmed = new AiOperationDO();
        confirmed.setId("op-1");
        confirmed.setStatus("CONFIRMED");
        when(operationService.confirm(eq(7L), eq(new OperationConfirmRequest(
                "op-1", "project-22", "context-v7",
                "pms-project-assistant/project-kickoff", "1.0.0"))))
                .thenReturn(new CommandPreview("op-1", CommandName.TASK_CREATE,
                        java.time.Instant.now().plusSeconds(60), "context-v7",
                        java.util.List.of(), java.util.List.of(), java.util.List.of("task-board")));
        UserContext.set(new LoginUser(7L, "dsh", "DSH"));
        try {
            ResponseResult<CommandPreview> response = controller.confirm("op-1",
                    new DshCommandController.ConfirmBody("project-22", "context-v7",
                            "pms-project-assistant/project-kickoff", "1.0.0", "dsh-approval"));
            assertThat(response.getData().operationId()).isEqualTo("op-1");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void confirmRejectsARequestWithoutSessionBinding() {
        assertThatThrownBy(() -> controller.confirm("op-1",
                new DshCommandController.ConfirmBody(null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DSH 确认请求缺少项目上下文或节点契约绑定");
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest=DshCommandControllerTest test`
Expected: 编译失败，提示 `confirm` / `ConfirmBody` / `operationService` 不存在。

- [ ] **Step 3: 实现接口**

`DshCommandController`：新增依赖与方法。

```java
    private final AiOperationService operationService;

    @PostMapping("/operations/{operationId}/confirm")
    public ResponseResult<CommandPreview> confirm(
            @PathVariable String operationId,
            @RequestBody ConfirmBody body) {
        if (body == null || body.contractId() == null || body.contractVersion() == null
                || body.contextId() == null || body.contextVersion() == null) {
            throw BusinessException.conflict("DSH 确认请求缺少项目上下文或节点契约绑定");
        }
        return ResponseResult.success(operationService.confirm(UserContext.userId(),
                new OperationConfirmRequest(operationId, body.contextId(), body.contextVersion(),
                        body.contractId(), body.contractVersion(), body.source())));
    }

    public record ConfirmBody(
            String contextId,
            String contextVersion,
            String contractId,
            String contractVersion,
            String source) {
    }
```

（`source` 由 DSH 传入：人工审批产生时传 `dsh-approval`，将来自动确认传 `agent-auto`；缺省按 `dsh-approval` 处理。再次强调它只是审计提示值，不作为安全判断依据。）

- [ ] **Step 4: 加路由规则**

`AiDelegationRoutePolicy`，紧挨 `execute` 规则之前：

```java
        if ("POST".equalsIgnoreCase(method)
                && path.matches("/integration/dsh/v1/operations/[^/]+/confirm")) {
            return tokenProvider.hasAiDelegationScope(token, "pms:command:execute");
        }
```

在 `AiDelegationRoutePolicyTest` 增加：持有 `pms:command:execute` 的 token 可确认，仅持有 `pms:command:preview` 的 token 被拒。

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest='DshCommandControllerTest,AiDelegationRoutePolicyTest' test`
Expected: 全绿；原有 execute 用例仍然通过（`DshCommandControllerTest` 构造控制器的地方补上新增的 `operationService` mock）。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/brad/pms/controller/DshCommandController.java \
        src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java \
        src/test/java/com/brad/pms/controller/DshCommandControllerTest.java \
        src/test/java/com/brad/pms/security/AiDelegationRoutePolicyTest.java
git commit -m "feat: expose dsh operation confirm endpoint"
```

### Task 3: 执行必须先确认

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/command/AiOperationService.java`
- Test: `src/test/java/com/brad/pms/ai/command/AiOperationConfirmTest.java`
- Test: `src/test/java/com/brad/pms/ai/command/AiOperationContractAuditTest.java`

**Interfaces:**
- Consumes: Task 1 的 `CONFIRMED` 常量与 `AiOperationDO.getStatus()`。
- Produces: `execute` 对 `PREVIEW` 状态返回冲突 `操作尚未确认，请先确认预览`；对 `CONFIRMED` 且未过期才继续。

- [ ] **Step 1: 写失败的测试**

`AiOperationConfirmTest` 增加：

```java
    @Test
    void executeRejectsAnUnconfirmedPreview() {
        Fixture fixture = fixture("PREVIEW", LocalDateTime.now().plusMinutes(5));

        assertThatThrownBy(() -> fixture.service().execute(7L, new OperationExecuteRequest(
                "operation-1", "idem-1", "project-22", "context-v7", CONTRACT, "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作尚未确认");
    }

    @Test
    void executeRejectsAnExpiredConfirmedOperation() {
        Fixture fixture = fixture("CONFIRMED", LocalDateTime.now().minusSeconds(1));

        assertThatThrownBy(() -> fixture.service().execute(7L, new OperationExecuteRequest(
                "operation-1", "idem-1", "project-22", "context-v7", CONTRACT, "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作预览已过期");
        assertThat(fixture.operation().getStatus()).isEqualTo("EXPIRED");
    }
```

并把 `AiOperationContractAuditTest` 里两个"执行前校验"用例的 `operation.setStatus("PREVIEW")` 改为 `operation.setStatus("CONFIRMED")`（它们要测的是契约/上下文绑定，不是确认状态）。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest='AiOperationConfirmTest,AiOperationContractAuditTest' test`
Expected: `executeRejectsAnUnconfirmedPreview` 失败（当前实现会继续往下走，抛的要么是契约错误要么是"操作当前状态不可执行"）。

- [ ] **Step 3: 实现**

`execute` 中把原来的 `if (!PREVIEW.equals(operation.getStatus()))` 分支替换为：

```java
        if (!CONFIRMED.equals(operation.getStatus())) {
            throw BusinessException.conflict(PREVIEW.equals(operation.getStatus())
                    ? "操作尚未确认，请先确认预览"
                    : "操作当前状态不可执行");
        }
```

过期检查保持在确认状态之后（确认后仍可能超时）。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest='AiOperationConfirmTest,AiOperationContractAuditTest,PmsCommandExecutionTest,AiCommandContractTest' test`
Expected: 全绿。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/brad/pms/ai/command/AiOperationService.java \
        src/test/java/com/brad/pms/ai/command/AiOperationConfirmTest.java \
        src/test/java/com/brad/pms/ai/command/AiOperationContractAuditTest.java
git commit -m "feat: require server-side confirmation before executing operations"
```

### Task 4: DSH 确认工具与提示词

**Files:**
- Modify: `packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Modify: `packages/pms/dsh-pms/src/tools/command.ts`
- Modify: `packages/pms/dsh-pms/src/index.ts`（`PMS_PROMPT` 文案）
- Test: `packages/pms/dsh-pms/tests/dsh-pms.spec.ts`

**Interfaces:**
- Consumes: Task 2 的确认端点。
- Produces:
  - `PmsIntegrationClient.confirmOperation(operationId: string, signal?: AbortSignal, sessionId?: string, binding?: PmsOperationBinding): Promise<PmsCommandPreview>`
  - 工具 `pms_command_confirm`（参数 `operationId`），内部走 `executePmsConfirmation(client, contextStore, args, exec)`
  - 导出 `confirmPmsOperation(client, contextStore, args, exec): Promise<PmsCommandPreview>` 供 live E2E 直接调用

- [ ] **Step 1: 写失败的单元测试**

在 `dsh-pms.spec.ts` 增加：

```ts
  it('confirms the exact preview before execution and forwards the contract binding', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response({
        version: 'v1', tools: ['pms_command_preview', 'pms_command_execute'],
        scopes: ['pms:command:preview', 'pms:command:execute'], pageTypes: ['project-detail'],
        queries: [], commands: [{
          name: 'task.create', description: '创建任务', access: 'write', risk: 'medium',
          requiresConfirmation: true, scopes: ['pms:command:preview', 'pms:command:execute'],
          parameters: { projectId: { type: 'integer', required: true }, nodeId: { type: 'integer', required: true } },
          supportsPreview: true, supportsExecute: true, refreshScopes: [],
        }],
      }))
      .mockResolvedValueOnce(response({
        operationId: 'op-confirm-1', command: 'task.create', warnings: [], changes: [],
      }))
    const ctx = new Context()
    activeContexts.push(ctx)
    await ctx.plugin(SystemPrompt)
    await ctx.plugin(ToolRuntime)
    await ctx.plugin(DshPms, {
      baseUrl: 'http://pms.test', apiPrefix: '/api', accessToken: 'short-token', requestTimeoutMs: 1000,
    })
    const store = ctx.get('pmsContextStore') as PmsContextStore
    store.set({ pageType: 'project-detail', projectId: 22, nodeId: 7, currentNodeKey: 'kickoff', contextVersion: 'pms-v1' })
    store.setContract(undefined, { status: 'ready', contract: kickoffContract() })

    const result = await ctx.tools.execute({
      signal: new AbortController().signal,
      callId: ToolCallId('confirm-call'),
      name: 'pms_command_confirm',
      arguments: { operationId: 'op-confirm-1' },
    })

    expect(result.isError).toBe(false)
    const [url, init] = fetchMock.mock.calls[1]!
    expect(requestUrl(url)).toBe('http://pms.test/api/integration/dsh/v1/operations/op-confirm-1/confirm')
    expect(JSON.parse(requestBody(init?.body) ?? '')).toEqual({
      contextId: 'pms:project-detail:22:7',
      contextVersion: 'pms-v1',
      contractId: 'pms-project-assistant/project-kickoff',
      contractVersion: '1.0.0',
      source: 'dsh-approval',
    })
  })
```

（`kickoffContract()` 已存在于该测试文件。）

- [ ] **Step 2: 运行测试确认失败**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms/tests/dsh-pms.spec.ts -t 'confirms the exact preview'`
Expected: FAIL，报工具 `pms_command_confirm` 不存在 / `isError: true`。

- [ ] **Step 3: 客户端方法**

`PmsIntegrationClient` 增加（放在 `executeOperation` 之前）：

```ts
  confirmOperation(
    operationId: string,
    signal?: AbortSignal,
    sessionId?: string,
    binding?: PmsOperationBinding,
    source: 'dsh-approval' | 'agent-auto' = 'dsh-approval',
  ): Promise<PmsCommandPreview> {
    if (operationId.trim() === '') throw new Error('dsh-pms: operationId is required')
    return this.withCapability('pms_command_execute', signal, sessionId)
      .then(() => this.post<PmsCommandPreview>(
        `/integration/dsh/v1/operations/${encodeURIComponent(operationId)}/confirm`,
        { ...(binding ?? {}), source },
        signal,
        sessionId,
      ))
  }
```

- [ ] **Step 4: 确认工具（人工点击就在这一步）**

`tools/command.ts` 的 `registerPmsCommandTools` 中，把 `tools/pre-execute` 的 ask 门禁同时覆盖 confirm：`if (exec.name !== 'pms_command_confirm' && exec.name !== 'pms_command_execute') return next()`，并注册新工具：

决策 3 的含义：模型调用 `pms_command_confirm` 时会命中这条 ask 门禁，DSH 弹出审批，**用户点"允许"之后**请求才发到 PMS；`source` 固定传 `dsh-approval`。将来自动确认时，只需把 `source` 改为 `agent-auto` 并放开策略，不改状态机。

```ts
  if (enabled.execute) ctx.tools.register(defineTool({
    name: 'pms_command_confirm',
    description: 'Record the user\'s explicit confirmation for one PMS preview before executing it. Call this only after the user confirmed that exact preview in a later message; then call pms_command_execute with the same operationId.',
    parameters: {
      operationId: { type: 'string', required: true, description: 'The operationId returned by the latest PMS command preview.' },
    },
    output: {
      schema: { type: 'json' },
      render: (_args, value) => [{ type: 'text', text: JSON.stringify(value, null, 2) }],
    },
    execute: (args, exec) => confirmPmsOperation(client, contextStore, args, exec),
    isConcurrencySafe: () => false,
  }))

export async function confirmPmsOperation(
  client: PmsIntegrationClient,
  contextStore: PmsContextStore,
  args: { operationId: string },
  exec: { signal: AbortSignal; agent?: { id?: string } },
): Promise<PmsCommandPreview> {
  const binding = requireOperationBinding(contextStore.get(exec.agent?.id), contextStore.getContract(exec.agent?.id))
  return client.confirmOperation(args.operationId, exec.signal, exec.agent?.id, binding)
}
```

- [ ] **Step 5: 提示词**

`index.ts` 的 `PMS_PROMPT` 里，把预览/执行口径改为：

```ts
  'For a write request, first call pms_command_preview. Explain every proposed change and warning, then wait for an explicit confirmation in a later user message. Once confirmed, call pms_command_confirm with that operationId and then pms_command_execute with the same operationId.',
  'Never call pms_command_confirm or pms_command_execute in the same turn as preview, never invent an operationId, and never treat a vague request as confirmation.',
```

- [ ] **Step 6: 运行单元测试与类型检查**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms && ./node_modules/.bin/tsc -b packages/pms/dsh-pms/tsconfig.json --pretty false`
Expected: 全绿；`pms_command_confirm` 出现在 `ctx.tools.schemas()` 的工具清单里（如现有断言白名单，需同步更新为该 7 个工具）。

- [ ] **Step 7: 提交**

```bash
git add packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts \
        packages/pms/dsh-pms/src/tools/command.ts \
        packages/pms/dsh-pms/src/index.ts \
        packages/pms/dsh-pms/tests/dsh-pms.spec.ts
git commit -m "feat(dsh-pms): confirm operations before executing them"
```

### Task 5: live E2E 三步流程与文档

**Files:**
- Modify: `packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts`
- Modify: `docs/agent-contracts/pms/project-kickoff.md`
- Modify: `docs/agent-contracts/pms/project-kickoff-e2e-record.md`

**Interfaces:**
- Consumes: Task 4 的 `confirmPmsOperation`、`executePmsOperation`。
- Produces: E2E 证据里出现 `write.confirmed` 步骤；记录文档说明 confirm 为服务端状态。

- [ ] **Step 1: 改 live E2E**

在"previews, executes and verifies an idempotent write"用例里，把原来的"审批门禁 + 直接执行"改成三步：

```ts
    const blockedConfirm = await callToolError('pms_command_confirm', { operationId: preview.operationId })
    record('write.confirm-gate-without-approval', { blocked: true, message: blockedConfirm })

    const confirmed = await confirmPmsOperation(client, contextStore, { operationId: preview.operationId }, {
      signal: new AbortController().signal,
    })
    expect(confirmed.operationId).toBe(preview.operationId)
    record('write.confirmed', { operationId: preview.operationId, contractVersion: confirmed.command })

    const executed = await executePmsOperation(client, contextStore, { operationId: preview.operationId }, {
      signal: new AbortController().signal,
    })
```

并新增一个断言：未确认的操作不能执行——

```ts
  it('refuses to execute a preview that was never confirmed', async () => {
    const detail = await callTool<{ data: { currentNode?: PmsJsonObject } }>('pms_project_get', { projectId: createdProjectId })
    const currentNodeId = Number(detail.data.currentNode?.id)
    const preview = await callTool<PmsCommandPreview>('pms_command_preview', {
      command: 'task.create',
      arguments: { projectId: createdProjectId, nodeId: currentNodeId, title: '未确认不应执行' },
    })
    let failure: Record<string, unknown> = { blocked: false }
    try {
      await executePmsOperation(client, contextStore, { operationId: preview.operationId }, {
        signal: new AbortController().signal,
      })
    } catch (error) {
      const e = error as { message?: string; code?: string | number }
      failure = { blocked: true, message: e.message, code: e.code }
    }
    expect(failure.blocked).toBe(true)
    record('write.execute-without-confirm', failure)
  })
```

- [ ] **Step 2: 运行 live E2E**

前置：PMS 后端已用新构建启动（`mvn -q -o -DskipTests package` 后重启）。

Run:
```bash
cd deepseek-harness
set -a && source .env.mysql.local && set +a
PMS_E2E_BASE_URL=http://127.0.0.1:8080 PMS_E2E_SERVICE_KEY="$PMS_DSH_SERVICE_KEY" \
PMS_E2E_USER_EMAIL=e2e.kickoff@pms.com PMS_E2E_USER_PASSWORD='KickoffE2e!2026' \
PMS_E2E_SESSION_ID=pms-kickoff-e2e-live PMS_E2E_RECORD_PATH=/tmp/pms-kickoff-e2e/evidence.json \
./node_modules/.bin/vitest run packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts
```
Expected: 全绿；`evidence.json` 含 `write.confirmed` 与 `write.execute-without-confirm`。

- [ ] **Step 3: 文档**

`docs/agent-contracts/pms/project-kickoff.md` 的执行规则第 4、5 条之间插入：

```markdown
4. 用户在后续消息中明确确认同一份预览后，写入前必须先 `pms_command_confirm` 把确认落到 PMS（服务端状态 `PREVIEW → CONFIRMED`），再 `pms_command_execute`；未经确认的操作在 PMS 侧无法执行。
```

`project-kickoff-e2e-record.md` 的场景表新增一行"确认落库"：

```markdown
| 确认落库（本计划 Phase 1） | `pms_command_confirm` 前直接执行被拒（`操作尚未确认，请先确认预览`）；确认后 `pms_ai_operation.status=CONFIRMED`、`confirmed_by` 有值，再执行成功 | 通过 |
```

- [ ] **Step 4: 提交**

```bash
cd deepseek-harness && git add packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts && git commit -m "test(dsh-pms): cover the confirm-then-execute live flow"
cd pms-backend && git add docs/agent-contracts/pms/project-kickoff.md docs/agent-contracts/pms/project-kickoff-e2e-record.md && git commit -m "docs: document server-side operation confirmation"
```

---

## Phase 2：会话上下文服务端化

**Phase 2 review gate:** 杀掉并重启 PMS 后端与 DSH 进程后，同一个 DSH 会话仍能加载契约并完成一次写入；无绑定且无页面上下文时给出 `PMS_SESSION_CONTEXT_MISSING` 而不是 `节点：undefined`。

**为什么选"服务端绑定"而不是让客户端重连时补拉：** 现有客户端已经有 `pms.context.request`，但那只在 PMS 工作区挂载/iframe load 时才会被应答；用户关掉工作区、换一个客户端、或宿主重启后，没有任何东西保证页面上线。把绑定落到 PMS 后，DSH 只需一次 HTTP 就能恢复"上次在看的项目/节点"，且绑定本身受用户与权限约束、有 TTL、可审计。

### Task 6: 绑定表与绑定服务

**Files:**
- Create: `src/main/resources/db/migration/V51__add_dsh_session_context.sql`
- Create: `src/main/java/com/brad/pms/entity/DshSessionContextDO.java`
- Create: `src/main/java/com/brad/pms/mapper/DshSessionContextMapper.java`
- Create: `src/main/java/com/brad/pms/integration/dsh/service/DshSessionContextService.java`
- Modify: `src/main/java/com/brad/pms/ai/contract/PmsAgentContractRegistry.java`（新增 `findByNodeKey`）
- Test: `src/test/java/com/brad/pms/integration/dsh/service/DshSessionContextServiceTest.java`

**Interfaces:**
- Consumes: `ProjectPermissionService.requireProjectReadable(Long)`、`NodeService.list(Long)`（取当前节点）、`PmsAgentContractRegistry.findByNodeKey(String)`。
- Produces:
  - `Optional<PmsAgentContract> PmsAgentContractRegistry.findByNodeKey(String nodeKey)`
  - `DshSessionContextDTO DshSessionContextService.bind(Long userId, String dshSessionId, Long projectId, Long nodeId, String source)`
  - `Optional<DshSessionContextDTO> DshSessionContextService.resolve(Long userId, String dshSessionId)`
  - `record DshSessionContextDTO(String dshSessionId, Long projectId, Long nodeId, String nodeKey, String contractKey, String contractVersion, String contextVersion, String bindingSource, LocalDateTime expiresAt)`

- [ ] **Step 1: 写失败的测试**

```java
package com.brad.pms.integration.dsh.service;

import com.brad.pms.ai.contract.PmsAgentContract;
import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.DshSessionContextDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.DshSessionContextMapper;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DshSessionContextServiceTest {

    @Mock ProjectPermissionService permissionService;
    @Mock NodeService nodeService;
    @Mock PmsAgentContractRegistry contractRegistry;
    @Mock DshSessionContextMapper mapper;

    @Test
    void bindResolvesTheCurrentNodeAndItsContract() {
        ProjectNodeDO currentNode = new ProjectNodeDO();
        currentNode.setId(388L);
        currentNode.setNodeKey("kickoff");
        currentNode.setVersion(3);
        when(nodeService.list(44L)).thenReturn(List.of(nodeDto(388L, "kickoff", true), nodeDto(389L, "requirement", false)));
        when(contractRegistry.findByNodeKey("kickoff")).thenReturn(Optional.of(contract()));
        when(mapper.selectById("pms-session-1")).thenReturn(null);

        DshSessionContextDTO binding = service().bind(7L, "pms-session-1", 44L, null, "agent-read");

        assertThat(binding.projectId()).isEqualTo(44L);
        assertThat(binding.nodeId()).isEqualTo(388L);
        assertThat(binding.nodeKey()).isEqualTo("kickoff");
        assertThat(binding.contractKey()).isEqualTo("project-kickoff");
        assertThat(binding.bindingSource()).isEqualTo("agent-read");
    }

    @Test
    void bindRejectsANodeWithoutAContract() {
        when(nodeService.list(44L)).thenReturn(List.of(nodeDto(389L, "requirement", true)));
        when(contractRegistry.findByNodeKey("requirement")).thenReturn(Optional.empty());
        when(mapper.selectById("pms-session-1")).thenReturn(null);

        assertThatThrownBy(() -> service().bind(7L, "pms-session-1", 44L, 389L, "page"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该节点没有已注册契约");
    }

    @Test
    void resolveReturnsNothingForAnotherUser() {
        DshSessionContextDO row = new DshSessionContextDO();
        row.setDshSessionId("pms-session-1");
        row.setUserId(9L);
        row.setProjectId(44L);
        row.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        when(mapper.selectById("pms-session-1")).thenReturn(row);

        assertThat(service().resolve(7L, "pms-session-1")).isEmpty();
    }

    private DshSessionContextService service() {
        return new DshSessionContextService(mapper, permissionService, nodeService, contractRegistry);
    }

    private static com.brad.pms.dto.response.ProjectNodeDTO nodeDto(long id, String nodeKey, boolean current) {
        com.brad.pms.dto.response.ProjectNodeDTO dto = new com.brad.pms.dto.response.ProjectNodeDTO();
        dto.setId(id);
        dto.setNodeKey(nodeKey);
        dto.setStatus(current ? 1 : 0);
        return dto;
    }

    private static PmsAgentContract contract() {
        return new PmsAgentContract(
                "pms-project-assistant/project-kickoff", "project_assistant", "project-kickoff",
                List.of("kickoff"), "1.0.0", true, "zh-CN", "PMS 项目助手", List.of(),
                List.of("项目上下文"), java.util.Map.of("project-context", "pms_project_get"),
                List.of("project.create"), java.util.Map.of("project.create", "preview-and-confirm"),
                List.of(), List.of(), List.of(), List.of(), List.of("连续三轮无法完成"));
    }
}
```

（`PmsAgentContract` 是 20 个组件的 record，**没有** `contentSha256`——摘要由 `PmsAgentContractFingerprint` 单独计算；`ProjectNodeDTO` 的 `nodeKey`/`status` 字段名以 `src/main/java/com/brad/pms/dto/response/ProjectNodeDTO.java` 为准。）

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest=DshSessionContextServiceTest test`
Expected: 编译失败（`DshSessionContextService` 等不存在）。

- [ ] **Step 3: 迁移与实体**

`V51__add_dsh_session_context.sql`：

```sql
CREATE TABLE pms_dsh_session_context (
    dsh_session_id  VARCHAR(128) NOT NULL,
    user_id         BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    node_id         BIGINT       NULL,
    node_key        VARCHAR(64)  NULL,
    contract_key    VARCHAR(64)  NULL,
    contract_version VARCHAR(80) NULL,
    context_version VARCHAR(64)  NULL,
    binding_source  VARCHAR(32)  NOT NULL,
    created_at      DATETIME     NOT NULL,
    updated_at      DATETIME     NOT NULL,
    expires_at      DATETIME     NULL,
    PRIMARY KEY (dsh_session_id)
);

CREATE INDEX idx_pms_dsh_session_context_user ON pms_dsh_session_context (user_id, updated_at);
```

`DshSessionContextDO`（`@TableName("pms_dsh_session_context")`，字段与列一一对应，`@TableId("dsh_session_id")`）。

- [ ] **Step 4: 契约按节点查找**

`PmsAgentContractRegistry` 增加：

```java
    public java.util.Optional<PmsAgentContract> findByNodeKey(String nodeKey) {
        if (loadError != null || nodeKey == null || nodeKey.isBlank()) return java.util.Optional.empty();
        return contracts.values().stream()
                .filter(contract -> contract.workflowNodeKeys().contains(nodeKey))
                .findFirst();
    }
```

- [ ] **Step 5: 实现服务**

```java
@Service
@RequiredArgsConstructor
public class DshSessionContextService {

    private static final Duration BINDING_TTL = Duration.ofHours(12);
    private static final Set<String> SOURCES = Set.of("page", "agent-read");

    private final DshSessionContextMapper mapper;
    private final ProjectPermissionService permissionService;
    private final NodeService nodeService;
    private final PmsAgentContractRegistry contractRegistry;

    @Transactional
    public DshSessionContextDTO bind(Long userId, String dshSessionId, Long projectId, Long nodeId, String source) {
        if (userId == null) throw BusinessException.unauthorized("未登录");
        if (dshSessionId == null || dshSessionId.isBlank() || dshSessionId.length() > 128) {
            throw BusinessException.error("DSH 会话标识无效");
        }
        String bindingSource = SOURCES.contains(source) ? source : "page";
        permissionService.requireProjectReadable(projectId);
        List<ProjectNodeDTO> nodes = nodeService.list(projectId);
        ProjectNodeDTO node = nodes.stream()
                .filter(candidate -> nodeId == null ? isCurrent(candidate) : candidate.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> BusinessException.error("项目当前节点不存在或不可用"));
        PmsAgentContract contract = contractRegistry.findByNodeKey(node.getNodeKey())
                .orElseThrow(() -> BusinessException.conflict("该节点没有已注册契约：" + node.getNodeKey()));
        LocalDateTime now = LocalDateTime.now();
        DshSessionContextDO row = mapper.selectById(dshSessionId);
        boolean insert = row == null;
        if (row == null) { row = new DshSessionContextDO(); row.setDshSessionId(dshSessionId); row.setCreatedAt(now); }
        row.setUserId(userId);
        row.setProjectId(projectId);
        row.setNodeId(node.getId());
        row.setNodeKey(node.getNodeKey());
        row.setContractKey(contract.contractKey());
        row.setContractVersion(contract.contractVersion());
        row.setContextVersion("project-" + projectId + ":node-" + node.getId());
        row.setBindingSource(bindingSource);
        row.setUpdatedAt(now);
        row.setExpiresAt(now.plus(BINDING_TTL));
        if (insert) mapper.insert(row); else mapper.updateById(row);
        return toDto(row);
    }

    @Transactional(readOnly = true)
    public Optional<DshSessionContextDTO> resolve(Long userId, String dshSessionId) {
        if (dshSessionId == null || dshSessionId.isBlank()) return Optional.empty();
        DshSessionContextDO row = mapper.selectById(dshSessionId);
        if (row == null || !row.getUserId().equals(userId)) return Optional.empty();
        if (row.getExpiresAt() != null && row.getExpiresAt().isBefore(LocalDateTime.now())) return Optional.empty();
        return Optional.of(toDto(row));
    }

    private static boolean isCurrent(ProjectNodeDTO node) {
        return node.getStatus() != null && node.getStatus() == 1;
    }

    private static DshSessionContextDTO toDto(DshSessionContextDO row) {
        return new DshSessionContextDTO(
                row.getDshSessionId(),
                row.getProjectId(),
                row.getNodeId(),
                row.getNodeKey(),
                row.getContractKey(),
                row.getContractVersion(),
                row.getContextVersion(),
                row.getBindingSource(),
                row.getExpiresAt());
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest=DshSessionContextServiceTest test`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 7: 提交**

```bash
git add src/main/resources/db/migration/V51__add_dsh_session_context.sql \
        src/main/java/com/brad/pms/entity/DshSessionContextDO.java \
        src/main/java/com/brad/pms/mapper/DshSessionContextMapper.java \
        src/main/java/com/brad/pms/integration/dsh/service/DshSessionContextService.java \
        src/main/java/com/brad/pms/ai/contract/PmsAgentContractRegistry.java \
        src/test/java/com/brad/pms/integration/dsh/service/DshSessionContextServiceTest.java
git commit -m "feat: persist dsh session project and node binding"
```

### Task 7: 绑定接口与路由权限

**Files:**
- Create: `src/main/java/com/brad/pms/controller/DshContextController.java`
- Modify: `src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java`
- Test: `src/test/java/com/brad/pms/controller/DshContextControllerTest.java`

**Interfaces:**
- Consumes: Task 6 的 `DshSessionContextService.bind/resolve`。
- Produces:
  - `POST /integration/dsh/v1/context/bind`，body `{ "dshSessionId": "...", "projectId": 44, "nodeId": 388 }` → `ResponseResult<DshSessionContextDTO>`
  - `GET /integration/dsh/v1/context/{dshSessionId}` → `ResponseResult<DshSessionContextDTO>`，无绑定返回业务 404

- [ ] **Step 1: 写失败的控制器测试**

```java
    @Test
    void bindReturnsTheResolvedNodeAndContract() {
        when(contextService.bind(eq(7L), eq("pms-session-1"), eq(44L), isNull(), eq("agent-read")))
                .thenReturn(new DshSessionContextDTO("pms-session-1", 44L, 388L, "kickoff",
                        "project-kickoff", "1.0.0", "project-44:node-388", "agent-read", null));
        UserContext.set(new LoginUser(7L, "dsh", "DSH"));
        try {
            ResponseResult<DshSessionContextDTO> response = controller.bind(
                    new DshContextController.BindBody("pms-session-1", 44L, null, "agent-read"));
            assertThat(response.getData().nodeKey()).isEqualTo("kickoff");
        } finally {
            UserContext.clear();
        }
    }

    @Test
    void resolveReturnsNotFoundWhenNoBindingExists() {
        when(contextService.resolve(7L, "pms-session-1")).thenReturn(java.util.Optional.empty());
        UserContext.set(new LoginUser(7L, "dsh", "DSH"));
        try {
            assertThatThrownBy(() -> controller.resolve("pms-session-1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("PMS_SESSION_CONTEXT_MISSING");
        } finally {
            UserContext.clear();
        }
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest=DshContextControllerTest test`
Expected: 编译失败（控制器不存在）。

- [ ] **Step 3: 实现控制器**

```java
@RestController
@RequestMapping("/integration/dsh/v1/context")
@RequiredArgsConstructor
public class DshContextController {

    private final DshSessionContextService contextService;

    @PostMapping("/bind")
    public ResponseResult<DshSessionContextDTO> bind(@RequestBody BindBody body) {
        if (body == null || body.dshSessionId() == null || body.projectId() == null) {
            throw BusinessException.error("DSH 上下文绑定请求无效");
        }
        return ResponseResult.success(contextService.bind(
                UserContext.userId(), body.dshSessionId(), body.projectId(), body.nodeId(), body.source()));
    }

    @GetMapping("/{dshSessionId}")
    public ResponseResult<DshSessionContextDTO> resolve(@PathVariable String dshSessionId) {
        return ResponseResult.success(contextService.resolve(UserContext.userId(), dshSessionId)
                .orElseThrow(() -> BusinessException.notFound("PMS_SESSION_CONTEXT_MISSING: 当前会话没有 PMS 项目上下文")));
    }

    public record BindBody(String dshSessionId, Long projectId, Long nodeId, String source) {
    }
}
```

- [ ] **Step 4: 加路由规则**

```java
        if ("POST".equalsIgnoreCase(method) && "/integration/dsh/v1/context/bind".equals(path)) {
            return tokenProvider.hasAiDelegationScope(token, "pms:project:read");
        }
        if ("GET".equalsIgnoreCase(method) && path.matches("/integration/dsh/v1/context/[^/]+")) {
            return tokenProvider.hasAiDelegationScope(token, "pms:project:read");
        }
```

（绑定只是会话状态，不是项目写入；真正的写入仍在 confirm/execute 时按项目权限校验，因此这里用读 scope。）

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest='DshContextControllerTest,AiDelegationRoutePolicyTest' test`
Expected: 全绿。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/brad/pms/controller/DshContextController.java \
        src/main/java/com/brad/pms/security/AiDelegationRoutePolicy.java \
        src/test/java/com/brad/pms/controller/DshContextControllerTest.java
git commit -m "feat: expose dsh session context binding endpoints"
```

### Task 8: DSH 在上下文缺失时回退到服务端绑定

**Files:**
- Modify: `packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Modify: `packages/pms/dsh-pms/src/index.ts`
- Modify: `packages/pms/dsh-pms/src/types.ts`（新增 `PmsContextBinding`）
- Test: `packages/pms/dsh-pms/tests/dsh-pms.spec.ts`

**Interfaces:**
- Consumes: Task 7 的两个端点。
- Produces:
  - `PmsContextBinding` 类型 `{ projectId: number; nodeId?: number; nodeKey?: string; contractKey?: string; contractVersion?: string; contextVersion?: string; bindingSource: string }`
  - `PmsIntegrationClient.resolveContextBinding(sessionId?: string, signal?: AbortSignal): Promise<PmsContextBinding | undefined>`
  - 错误码 `PMS_SESSION_CONTEXT_MISSING`（契约状态 `unavailable`）

- [ ] **Step 1: 写失败的测试**

```ts
  it('recovers the PMS page context from the server binding after a restart', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response({
        version: 'v1', tools: ['pms_command_preview'], scopes: ['pms:query:read'], pageTypes: [],
        queries: [], commands: [], agentContracts: [{
          agentId: 'project_assistant', contractKey: 'project-kickoff', workflowNodeKeys: ['kickoff'],
          contractVersion: '1.0.0',
          endpoint: '/integration/dsh/v1/agent-contracts/{agentId}/{contractKey}',
          scope: 'pms:query:read', required: true,
        }],
      }))
      .mockResolvedValueOnce(response({
        dshSessionId: 'session-restart', projectId: 44, nodeId: 388, nodeKey: 'kickoff',
        contractKey: 'project-kickoff', contractVersion: '1.0.0',
        contextVersion: 'project-44:node-388', bindingSource: 'agent-read',
      }))
      .mockResolvedValueOnce(response({
        contractId: 'pms-project-assistant/project-kickoff', agentId: 'project_assistant',
        contractKey: 'project-kickoff', workflowNodeKeys: ['kickoff'], contractVersion: '1.0.0',
        required: true, locale: 'zh-CN', principalRole: 'PMS 项目经理', specializedAgents: [],
        readCapabilities: ['项目上下文'], readToolBindings: { 'project-context': 'pms_project_get' },
        writeCommands: ['project.create'], confirmationPolicies: { 'project.create': 'preview-and-confirm' },
        entryConditions: ['首个节点'], inputs: ['项目基本信息'], missingInputRules: ['不得猜测'],
        executionSteps: ['读取状态'], completionCriteria: ['必填字段完整'],
        failureStrategies: ['鉴权失败即停止'], terminationConditions: ['用户未确认'],
        contentSha256: 'a'.repeat(64),
      }))
    const ctx = new Context()
    activeContexts.push(ctx)
    await ctx.plugin(SystemPrompt)
    await ctx.plugin(ToolRuntime)
    await ctx.plugin(DshPms, {
      baseUrl: 'http://pms.test', apiPrefix: '/api', accessToken: 'short-token',
      dshSessionId: 'session-restart', requestTimeoutMs: 1000,
    })

    const assembly = await ctx.systemPrompt.assemble()

    const section = assembly.sections.find(item => item.name === 'pms:agent-contract')
    expect(section?.text).toContain('当前节点：kickoff')
    expect(requestUrl(fetchMock.mock.calls[1]![0])).toBe('http://pms.test/api/integration/dsh/v1/context/session-restart')
  })

  it('reports a locatable error when no page context and no server binding exist', async () => {
    vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response({
        version: 'v1', tools: ['pms_command_preview'], scopes: ['pms:query:read'], pageTypes: [],
        queries: [], commands: [], agentContracts: [],
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        code: 404, msg: 'PMS_SESSION_CONTEXT_MISSING: 当前会话没有 PMS 项目上下文', requestId: 'req-1',
      }), { status: 404, headers: { 'content-type': 'application/json' } }))

    const ctx = new Context()
    activeContexts.push(ctx)
    await ctx.plugin(SystemPrompt)
    await ctx.plugin(ToolRuntime)
    await ctx.plugin(DshPms, {
      baseUrl: 'http://pms.test', apiPrefix: '/api', accessToken: 'short-token',
      dshSessionId: 'session-empty', requestTimeoutMs: 1000,
    })
    const store = ctx.get('pmsContextStore') as PmsContextStore

    const assembly = await ctx.systemPrompt.assemble()

    expect(store.getContract('session-empty')).toMatchObject({ status: 'unavailable', errorCode: 'PMS_SESSION_CONTEXT_MISSING' })
    expect(assembly.sections.find(item => item.name === 'pms:agent-contract')?.text)
      .toContain('请在 DSH 中打开对应项目页面')
  })
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms/tests/dsh-pms.spec.ts -t 'recovers the PMS page context'`
Expected: FAIL（会走 `PMS_CURRENT_NODE_UNAVAILABLE` 分支，且没有 `/context/` 请求）。

- [ ] **Step 3: 类型与客户端方法**

`types.ts` 增加：

```ts
export interface PmsContextBinding {
  dshSessionId: string
  projectId: number
  nodeId?: number
  nodeKey?: string
  contractKey?: string
  contractVersion?: string
  contextVersion?: string
  bindingSource: string
  expiresAt?: string
}
```

`PmsIntegrationClient` 增加：

```ts
  async resolveContextBinding(
    sessionId?: string,
    signal?: AbortSignal,
  ): Promise<PmsContextBinding | undefined> {
    const resolvedSessionId = sessionId?.trim() || this.dshSessionId
    if (resolvedSessionId === '') return undefined
    try {
      return await this.get<PmsContextBinding>(
        `/integration/dsh/v1/context/${encodeURIComponent(resolvedSessionId)}`,
        undefined,
        signal,
        resolvedSessionId,
      )
    } catch (error) {
      if (error instanceof PmsIntegrationError && error.status === 404) return undefined
      throw error
    }
  }
```

- [ ] **Step 4: 回退逻辑与错误码**

`index.ts` 的 `resolveCurrentNodeLocator` 在"locator 缺失或没有 currentNodeKey"时插入回退：

```ts
  if (locator === undefined || locator.currentNodeKey === undefined) {
    const binding = await client.resolveContextBinding(sessionId, signal).catch(() => undefined)
    if (binding !== undefined && binding.nodeKey !== undefined) {
      const recovered: PmsContextLocator = {
        pageType: 'project-detail',
        route: `/projects/${binding.projectId}`,
        projectId: binding.projectId,
        ...(binding.nodeId === undefined ? {} : { nodeId: binding.nodeId }),
        currentNodeKey: binding.nodeKey,
        ...(binding.contextVersion === undefined ? {} : { contextVersion: binding.contextVersion }),
      }
      if (sessionId === undefined) contextStore.set(recovered)
      else contextStore.set(sessionId, recovered)
      return recovered
    }
  }
```

并把"节点没有契约"分支的错误码固定为 `PMS_SESSION_CONTEXT_MISSING`，文案：

```ts
      const message = '当前会话还没有 PMS 项目上下文：请在 DSH 中打开对应项目页面，或先让我读取一个项目（例如「看一下 PRJ-000044」）。写入已暂停。'
      contextStore.setContract(sessionId, { status: 'unavailable', errorCode: 'PMS_SESSION_CONTEXT_MISSING', errorMessage: message })
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms && ./node_modules/.bin/tsc -b packages/pms/dsh-pms/tsconfig.json --pretty false`
Expected: 全绿。

- [ ] **Step 6: 提交**

```bash
git add packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts packages/pms/dsh-pms/src/index.ts \
        packages/pms/dsh-pms/tests/dsh-pms.spec.ts
git commit -m "feat(dsh-pms): recover PMS context from the server binding"
```

### Task 9: 显式读项目即绑定上下文

**Files:**
- Modify: `packages/pms/dsh-pms/src/tools/project-detail.ts`
- Modify: `packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts`
- Test: `packages/pms/dsh-pms/tests/dsh-pms.spec.ts`
- Modify: `packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts`

**Interfaces:**
- Consumes: Task 7 的 `POST /context/bind`。
- Produces:
  - `PmsIntegrationClient.bindContext(input: { projectId: number, nodeId?: number, source: 'page' | 'agent-read' }, sessionId?: string, signal?: AbortSignal): Promise<PmsContextBinding>`
  - `pms_project_get` 在 `args.projectId` 显式提供且服务端可解析时回写绑定（失败不影响读取结果）

- [ ] **Step 1: 写失败的测试**

```ts
  it('binds the server-side context after an explicit project read', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response({
        version: 'v1', tools: ['pms_project_get'], scopes: ['pms:project:read'], pageTypes: [],
        queries: [], commands: [], agentContracts: [],
      }))
      .mockResolvedValueOnce(response({ contextId: 'project-detail:44:388', pageType: 'project-detail', projectId: 44, data: {} }))
      .mockResolvedValueOnce(response({
        dshSessionId: 'session-read', projectId: 44, nodeId: 388, nodeKey: 'kickoff',
        contractKey: 'project-kickoff', contractVersion: '1.0.0', bindingSource: 'agent-read',
      }))
    const ctx = new Context()
    activeContexts.push(ctx)
    await ctx.plugin(SystemPrompt)
    await ctx.plugin(ToolRuntime)
    await ctx.plugin(DshPms, {
      baseUrl: 'http://pms.test', apiPrefix: '/api', accessToken: 'short-token',
      dshSessionId: 'session-read', requestTimeoutMs: 1000,
    })

    const result = await ctx.tools.execute({
      signal: new AbortController().signal,
      callId: ToolCallId('read-bind'),
      name: 'pms_project_get',
      arguments: { projectId: 44 },
    })

    expect(result.isError).toBe(false)
    const [bindUrl, bindInit] = fetchMock.mock.calls[2]!
    expect(requestUrl(bindUrl)).toBe('http://pms.test/api/integration/dsh/v1/context/bind')
    expect(JSON.parse(requestBody(bindInit?.body) ?? '')).toEqual({
      dshSessionId: 'session-read', projectId: 44, nodeId: undefined, source: 'agent-read',
    })
  })
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms/tests/dsh-pms.spec.ts -t 'binds the server-side context after an explicit project read'`
Expected: FAIL（只会发两次请求）。

- [ ] **Step 3: 实现客户端方法与工具回写**

```ts
  bindContext(
    input: { projectId: number; nodeId?: number; source: 'page' | 'agent-read' },
    sessionId?: string,
    signal?: AbortSignal,
  ): Promise<PmsContextBinding> {
    const resolvedSessionId = sessionId?.trim() || this.dshSessionId
    if (resolvedSessionId === '') throw new Error('dsh-pms: dshSessionId is required to bind the PMS context')
    return this.post<PmsContextBinding>(
      '/integration/dsh/v1/context/bind',
      { dshSessionId: resolvedSessionId, projectId: input.projectId, nodeId: input.nodeId, source: input.source },
      signal,
      resolvedSessionId,
    )
  }
```

`project-detail.ts` 的 `execute` 改为：

```ts
    execute: async (args, exec) => {
      const sessionId = exec.agent?.id
      const locator = contextStore.get(sessionId)
      const projectId = args.projectId ?? locator?.projectId
      if (projectId === undefined) throw new Error('pms_project_get requires projectId or a current PMS project context')
      const snapshot = await client.project(projectId, args.nodeId ?? locator?.nodeId, exec.signal, sessionId)
      if (args.projectId !== undefined) {
        await client.bindContext({ projectId, nodeId: args.nodeId, source: 'agent-read' }, sessionId, exec.signal)
          .catch(() => undefined)
      }
      return snapshot
    },
```

- [ ] **Step 4: live E2E 增加"重启可恢复"**

在 live spec 里新增：

```ts
  it('keeps writes available after the local context is dropped', async () => {
    contextStore.clear()
    await refreshAuthCode()
    await client.bindContext({ projectId: createdProjectId, source: 'agent-read' }, sessionId).catch(() => undefined)

    const assembly = await ctx.systemPrompt.assemble({ signal: new AbortController().signal })

    expect(assembly.sections.find(section => section.name === 'pms:agent-contract')?.text).toContain('当前节点：kickoff')
    record('context.recovered-from-server', { sessionId, projectId: createdProjectId })
  })
```

- [ ] **Step 5: 运行测试与 live E2E**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms`；再按 Task 5 Step 2 的命令跑 live spec。
Expected: 单元测试全绿；live spec 全绿且证据含 `context.recovered-from-server`。

- [ ] **Step 6: 提交**

```bash
git add packages/pms/dsh-pms/src/tools/project-detail.ts packages/pms/dsh-pms/src/client/PmsIntegrationClient.ts \
        packages/pms/dsh-pms/tests/dsh-pms.spec.ts packages/pms/dsh-pms/tests/pms-kickoff-contract.live.spec.ts
git commit -m "feat(dsh-pms): bind the PMS session context on explicit reads"
```

---

## Phase 3：命令元数据单一来源

**Phase 3 review gate:** 在 PMS 里临时停用一个命令后，DSH 侧对该命令的预览给出明确"未发布"错误而不是参数校验错误；把契约里改成未注册命令时，PMS 启动即失败并给出可定位错误。

### Task 10: DSH 按能力目录校验命令

**Files:**
- Modify: `packages/pms/dsh-pms/src/tools/command.ts`
- Test: `packages/pms/dsh-pms/tests/dsh-pms.spec.ts`

**Interfaces:**
- Consumes: `PmsIntegrationClient.capabilities()`（已在预览路径调用）。
- Produces: 预览工具的参数 schema 不再带静态 `enum`；未发布命令返回错误 `PMS 当前未发布该命令：<code>；可用命令：<a, b, c>`。

- [ ] **Step 1: 写失败的测试**

```ts
  it('rejects a command the capability catalog does not publish', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(response({
        version: 'v1', tools: ['pms_command_preview'], scopes: ['pms:command:preview'], pageTypes: [],
        queries: [], commands: [{
          name: 'task.create', description: '创建任务', access: 'write', risk: 'medium',
          requiresConfirmation: true, scopes: ['pms:command:preview'],
          parameters: { projectId: { type: 'integer', required: true } },
          supportsPreview: true, supportsExecute: false, refreshScopes: [],
        }],
      }))
    const ctx = new Context()
    activeContexts.push(ctx)
    await ctx.plugin(SystemPrompt)
    await ctx.plugin(ToolRuntime)
    await ctx.plugin(DshPms, {
      baseUrl: 'http://pms.test', apiPrefix: '/api', accessToken: 'short-token', requestTimeoutMs: 1000,
    })
    const store = ctx.get('pmsContextStore') as PmsContextStore
    store.set({ pageType: 'project-detail', projectId: 22, nodeId: 7, currentNodeKey: 'kickoff', contextVersion: 'v1' })
    store.setContract(undefined, { status: 'ready', contract: kickoffContract() })

    const result = await ctx.tools.execute({
      signal: new AbortController().signal,
      callId: ToolCallId('unpublished-command'),
      name: 'pms_command_preview',
      arguments: { command: 'project.delete', arguments: { projectId: 22 } },
    })

    expect(result.isError).toBe(true)
    expect(result.content[0]).toMatchObject({ type: 'text' })
    expect(JSON.stringify(result.content)).toContain('PMS 当前未发布该命令：project.delete')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms/tests/dsh-pms.spec.ts -t 'rejects a command the capability catalog does not publish'`
Expected: FAIL（当前会继续发预览请求，或被静态枚举先拦下并给出 schema 错误信息）。

- [ ] **Step 3: 实现**

`tools/command.ts`：删除 `const COMMANDS = [...] as const` 与 schema 里的 `enum: [...COMMANDS]`（保留 `type: 'string'` 与说明"命令必须是 PMS capability 目录里发布的 code"），并在 `previewCommand` 前校验：

```ts
    execute: async (args, exec) => {
      const locator = contextStore.get(exec.agent?.id)
      const binding = requireOperationBinding(locator, contextStore.getContract(exec.agent?.id))
      const capabilities = await client.capabilities(exec.signal, exec.agent?.id)
      const published = (capabilities.commands ?? []).map(command => command.name)
      if (!published.includes(args.command)) {
        throw new Error(`PMS 当前未发布该命令：${args.command}；可用命令：${published.join(', ')}`)
      }
      const request = buildPreviewRequest(args, binding, acceptedArguments(capabilities, args.command), locator)
      return client.previewCommand(request, exec.signal, exec.agent?.id)
    },
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./node_modules/.bin/vitest run packages/pms/dsh-pms && ./node_modules/.bin/tsc -b packages/pms/dsh-pms/tsconfig.json --pretty false`
Expected: 全绿（含既有 `honors the PMS parameter contract...` 用例；既有测试里凡是 mock 了 capabilities 的都要确认 `commands` 里包含被测命令）。

- [ ] **Step 5: 提交**

```bash
git add packages/pms/dsh-pms/src/tools/command.ts packages/pms/dsh-pms/tests/dsh-pms.spec.ts
git commit -m "refactor(dsh-pms): validate preview commands against the live capability catalog"
```

### Task 11: 契约加载期交叉校验命令元数据

**Files:**
- Modify: `src/main/java/com/brad/pms/ai/contract/PmsAgentContractRegistry.java`
- Test: `src/test/java/com/brad/pms/ai/contract/PmsAgentContractRegistryTest.java`

**Interfaces:**
- Consumes: `PmsCommandRegistry.list()`、`PmsCommandMetadata.descriptor(CommandName)`。
- Produces: 启动/加载时校验——契约声明的每个写命令必须满足 `requiresConfirmation == true` 且 `scopes` 同时包含 `pms:command:preview` 与 `pms:command:execute`；否则 registry 标记 unhealthy 并输出"契约命令元数据不一致"（沿用现有 unhealthy 语义：契约能力隐藏，不影响 PMS 核心启动）。

- [ ] **Step 1: 写失败的测试**

在 `PmsAgentContractRegistryTest` 增加：

```java
    @Test
    void rejectsAContractCommandThatIsNotPreparedForConfirmation() {
        PmsCommandRegistry commands = mock(PmsCommandRegistry.class);
        when(commands.list()).thenReturn(java.util.Set.of(CommandName.PROJECT_CREATE));
        when(commands.require(CommandName.PROJECT_CREATE)).thenReturn(mock(PmsCommand.class));
        // 描述符故意缺少 pms:command:execute
        try (var mocked = org.mockito.Mockito.mockStatic(PmsCommandMetadata.class)) {
            mocked.when(() -> PmsCommandMetadata.descriptor(CommandName.PROJECT_CREATE)).thenReturn(
                    new PmsCommandDescriptor(CommandName.PROJECT_CREATE, "创建项目", "write", "high", true,
                            java.util.List.of("pms:project:write", "pms:command:preview"),
                            java.util.Map.of("name", java.util.Map.of("type", "string")), true, true,
                            java.util.List.of("project-list")));
            PmsAgentContractRegistry registry = new PmsAgentContractRegistry(commands);
            assertThat(registry.healthy()).isFalse();
            assertThat(registry.list()).isEmpty();
        }
    }
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest=PmsAgentContractRegistryTest test`
Expected: FAIL（当前实现只校验命令是否存在，registry 仍是 healthy）。

- [ ] **Step 3: 实现校验**

`PmsAgentContractRegistry` 在加载成功后、置 healthy 之前增加：

```java
    private void verifyCommandMetadata(PmsAgentContract contract) {
        for (String code : contract.writeCommands()) {
            CommandName name = CommandName.fromCode(code);
            if (!commands.list().contains(name)) {
                throw new IllegalStateException("契约声明了未注册的写入命令: " + code);
            }
            PmsCommandDescriptor descriptor = PmsCommandMetadata.descriptor(name);
            if (!descriptor.requiresConfirmation()) {
                throw new IllegalStateException("契约命令未声明确认策略: " + code);
            }
            if (!descriptor.scopes().containsAll(List.of("pms:command:preview", "pms:command:execute"))) {
                throw new IllegalStateException("契约命令缺少预览/执行权限声明: " + code);
            }
        }
    }
```

并把该方法加进现有加载循环的 try/catch 中，异常按现有方式写入 `loadError`（registry unhealthy、契约隐藏），`loadError` 文案形如 `契约命令元数据不一致: <detail>`。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -o -DfailIfNoTests=false -Dtest='PmsAgentContractRegistryTest,DshAgentContractControllerTest,DshCapabilityControllerTest' test`
Expected: 全绿；`healthy()` 在真实注册表下仍为 true（`project.update` 元数据已含 preview/execute 与 requiresConfirmation）。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/brad/pms/ai/contract/PmsAgentContractRegistry.java \
        src/test/java/com/brad/pms/ai/contract/PmsAgentContractRegistryTest.java
git commit -m "feat: cross-check contract commands with PMS command metadata"
```

---

## 兼容与回滚

- **兼容**：`confirm` 是新增端点与新增列；旧客户端（只调 preview + execute）会在 Task 3 之后被拒，这是有意的行为变更。按决策 5，PMS 与 DSH **同版本发布**，Task 3 与 Task 4 必须一起上线，因此不做旧客户端灰度兼容层（若将来改成独立发版，需要补一段"接受旧 PREVIEW 执行但记录告警"的过渡期）。
- **回滚**：Phase 1 回滚 = 恢复 `execute` 接受 `PREVIEW`（一行分支），并保留 `confirmed_*` 列（nullable，无副作用）；Phase 2 回滚 = DSH 侧 `resolveContextBinding` 返回 undefined 即可退回"仅页面上下文"；Phase 3 回滚 = 恢复 DSH 静态枚举与 registry 校验方法。
- **数据**：`pms_dsh_session_context` 与 `pms_ai_operation` 新增列均为可空/新增表，不影响既有查询；`V50`/`V51` 只做 ADD，不重写历史数据。

## 实施顺序与 Review Gate

1. Phase 1（Task 1–5）：先做，因为它把"确认"从客户端约定变成服务端事实，且后续所有写入测试都依赖它。
2. Phase 2（Task 6–9）：再做，解决重启/跨会话丢上下文。
3. Phase 3（Task 10–11）：最后做，属于可维护性收敛，不改变外部行为（除错误信息更准确）。

每个 Phase 结束时执行对应 review gate，跑：

```bash
# PMS
DOCKER_HOST="unix://$HOME/.colima/<colima-profile>/docker.sock" TESTCONTAINERS_RYUK_DISABLED=true mvn -o test
# DSH
cd deepseek-harness
./node_modules/.bin/vitest run packages/pms/dsh-pms
./node_modules/.bin/tsc -b packages/pms/dsh-pms/tsconfig.json --pretty false
./node_modules/.bin/oxlint packages/pms/dsh-pms/src packages/pms/dsh-pms/tests
```

已知既有失败（非本计划引入，不要试图"顺手修"）：`CommandPreviewServiceScopeTest.rejectsWorkflowPreviewWhenDelegationTokenLacksWorkflowWriteScope`；DSH 全仓 `tsc -b tsconfig.host.json` 在其他包（`llm-pi-ai` / `agent-loop` / `agent-presets` / `client/connection`）有既有类型错误。
