package com.brad.pms.ai.command;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiOperationContractAuditTest {

    @Test
    void persistsContractIdentityAndVersionAlongsideThePreviewAudit() {
        AiOperationMapper mapper = mock(AiOperationMapper.class);
        PmsAgentContractRegistry contractRegistry = mock(PmsAgentContractRegistry.class);
        when(mapper.insert(any(AiOperationDO.class))).thenReturn(1);
        when(contractRegistry.isCommandAllowed(
                "pms-project-assistant/project-kickoff", "1.0.0", "project.create")).thenReturn(true);
        AiOperationService service = new AiOperationService(
                mapper, mock(PmsCommandRegistry.class), new ObjectMapper().findAndRegisterModules(), contractRegistry);

        CommandPreviewRequest request = new CommandPreviewRequest(
                CommandName.PROJECT_CREATE,
                Map.of("name", "测试项目"),
                "project-22",
                "context-v7",
                "pms-project-assistant/project-kickoff",
                "1.0.0");
        CommandPreview proposal = new CommandPreview(
                "ignored", CommandName.PROJECT_CREATE, Instant.now().plusSeconds(60),
                "context-v7", List.of(), List.of(), List.of("project-list"));

        service.persistPreview(7L, request, proposal);

        var captor = org.mockito.ArgumentCaptor.forClass(AiOperationDO.class);
        org.mockito.Mockito.verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getContractId())
                .isEqualTo("pms-project-assistant/project-kickoff");
        assertThat(captor.getValue().getContractVersion()).isEqualTo("1.0.0");
    }

    @Test
    void refusesToExecuteAPreviewAfterItsContractVersionIsNoLongerCurrent() {
        AiOperationMapper mapper = mock(AiOperationMapper.class);
        PmsAgentContractRegistry contractRegistry = mock(PmsAgentContractRegistry.class);
        AiOperationDO operation = new AiOperationDO();
        operation.setId("operation-1");
        operation.setUserId(7L);
        operation.setCommandName(CommandName.PROJECT_CREATE.code());
        operation.setStatus("PREVIEW");
        operation.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
        operation.setContextId("project-22");
        operation.setContextVersion("context-v7");
        operation.setContractId("pms-project-assistant/project-kickoff");
        operation.setContractVersion("1.0.0");
        when(mapper.selectByIdForUpdate("operation-1")).thenReturn(operation);
        when(contractRegistry.isCommandAllowed(
                "pms-project-assistant/project-kickoff", "1.0.0", "project.create")).thenReturn(false);
        AiOperationService service = new AiOperationService(
                mapper, mock(PmsCommandRegistry.class), new ObjectMapper().findAndRegisterModules(), contractRegistry);

        assertThatThrownBy(() -> service.execute(7L, new OperationExecuteRequest(
                "operation-1", "idem-1", "project-22", "context-v7",
                "pms-project-assistant/project-kickoff", "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("契约版本或操作命令未获授权");
    }

    @Test
    void refusesToExecuteWhenTheCurrentContextDoesNotMatchThePreview() {
        AiOperationMapper mapper = mock(AiOperationMapper.class);
        PmsAgentContractRegistry contractRegistry = mock(PmsAgentContractRegistry.class);
        AiOperationDO operation = new AiOperationDO();
        operation.setId("operation-2");
        operation.setUserId(7L);
        operation.setCommandName(CommandName.PROJECT_CREATE.code());
        operation.setStatus("PREVIEW");
        operation.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
        operation.setContextId("project-22");
        operation.setContextVersion("context-v7");
        operation.setContractId("pms-project-assistant/project-kickoff");
        operation.setContractVersion("1.0.0");
        when(mapper.selectByIdForUpdate("operation-2")).thenReturn(operation);
        when(contractRegistry.isCommandAllowed(
                "pms-project-assistant/project-kickoff", "1.0.0", "project.create")).thenReturn(true);
        AiOperationService service = new AiOperationService(
                mapper, mock(PmsCommandRegistry.class), new ObjectMapper().findAndRegisterModules(), contractRegistry);

        assertThatThrownBy(() -> service.execute(7L, new OperationExecuteRequest(
                "operation-2", "idem-2", "project-24", "context-v8",
                "pms-project-assistant/project-kickoff", "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目上下文不一致");
    }
}
