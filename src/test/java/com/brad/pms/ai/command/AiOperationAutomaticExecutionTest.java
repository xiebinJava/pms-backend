package com.brad.pms.ai.command;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.mapper.AiOperationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationAutomaticExecutionTest {

    @Test
    void reservesTheIdempotencyKeyAndReturnsTheStoredResultOnRetry() throws Exception {
        AiOperationMapper mapper = mock(AiOperationMapper.class);
        PmsCommandRegistry registry = mock(PmsCommandRegistry.class);
        PmsAgentContractRegistry contractRegistry = mock(PmsAgentContractRegistry.class);
        PmsCommand command = mock(PmsCommand.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        CommandResult expected = new CommandResult(
                "operation-1", "SUCCEEDED", "项目已创建", Map.of("projectId", 7L), List.of("project-list"));
        CommandPreview proposal = new CommandPreview(
                null, CommandName.PROJECT_CREATE, Instant.now().plusSeconds(600),
                "v1", List.of(), List.of(Map.of("entity", "project")), List.of("project-list"));
        when(mapper.selectByUserIdAndIdempotencyKeyForUpdate(7L, "idem-1"))
                .thenReturn(null, succeededOperation(objectMapper, expected));
        when(registry.require(CommandName.PROJECT_CREATE)).thenReturn(command);
        when(command.preview(any())).thenReturn(proposal);
        when(command.execute(any())).thenReturn(expected);
        AtomicReference<String> insertedStatus = new AtomicReference<>();
        doAnswer(invocation -> {
            insertedStatus.set(invocation.getArgument(0, AiOperationDO.class).getStatus());
            return 1;
        }).when(mapper).insert(any(AiOperationDO.class));

        AiOperationService service = new AiOperationService(mapper, registry, objectMapper, contractRegistry);
        CommandPreviewRequest request = new CommandPreviewRequest(
                CommandName.PROJECT_CREATE, Map.of("name", "测试项目"), "global:pms", "v1");

        CommandResult first = service.executeAutomatically(7L, request, "idem-1", "mcp", "request-1");
        CommandResult retry = service.executeAutomatically(7L, request, "idem-1", "mcp", "request-2");

        assertThat(first).isEqualTo(expected);
        assertThat(retry.operationId()).isEqualTo(expected.operationId());
        assertThat(retry.status()).isEqualTo(expected.status());
        assertThat(retry.message()).isEqualTo(expected.message());
        assertThat(retry.refreshScopes()).containsExactlyElementsOf(expected.refreshScopes());
        assertThat(retry.data()).containsEntry("projectId", 7);
        verify(command, times(1)).execute(any(AiOperationDO.class));
        var captor = org.mockito.ArgumentCaptor.forClass(AiOperationDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(insertedStatus).hasValue("AUTOMATIC_RUNNING");
        assertThat(captor.getValue().getExecutionMode()).isEqualTo("AUTOMATIC");
        assertThat(captor.getValue().getSourceClient()).isEqualTo("mcp");
        assertThat(captor.getValue().getRequestId()).isEqualTo("request-1");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-1");
    }

    private AiOperationDO succeededOperation(ObjectMapper objectMapper, CommandResult result) throws Exception {
        AiOperationDO operation = new AiOperationDO();
        operation.setStatus("SUCCEEDED");
        operation.setResultJson(objectMapper.writeValueAsString(result));
        return operation;
    }
}
