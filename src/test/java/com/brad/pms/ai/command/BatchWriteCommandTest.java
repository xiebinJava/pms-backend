package com.brad.pms.ai.command;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AiOperationDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchWriteCommandTest {

    @Mock PmsCommandRegistry registry;
    @Mock PmsAgentContractRegistry contractRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> executed = new ArrayList<>();
    private BatchWriteCommand command;

    @BeforeEach
    void setUp() {
        command = new BatchWriteCommand(registry, contractRegistry, objectMapper);
        lenient().when(registry.require(any(CommandName.class)))
                .thenAnswer(invocation -> child(invocation.getArgument(0)));
        lenient().when(contractRegistry.isCommandAllowed(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void previewsEveryChildChangeInOneOperation() {
        Map<String, Object> arguments = operations(
                item("task.create", Map.of("title", "任务一")),
                item("task.create", Map.of("title", "任务二")));

        CommandPreview preview = command.preview(
                new CommandPreviewRequest(CommandName.BATCH_WRITE, arguments, "pms:project-detail:22:7", "v1"));

        assertThat(preview.command()).isEqualTo(CommandName.BATCH_WRITE);
        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("entity", "batch");
            assertThat(change).containsEntry("itemCount", 2);
            assertThat((List<?>) change.get("items")).hasSize(2);
        });
        assertThat(preview.warnings()).anyMatch(warning -> warning.contains("整批回滚"));
    }

    @Test
    void executesEveryChildInOrderAndReportsEachResult() {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-batch-1");
        operation.setArgumentsJson(write(operations(
                item("task.create", Map.of("title", "任务一")),
                item("task.assign", Map.of("taskId", 1)))));
        operation.setExpectedVersionsJson(write(List.of(batchExpectations(
                expectation("task.create", Map.of("projectVersion", 3)),
                expectation("task.assign", Map.of("taskVersion", 9))))));

        CommandResult result = command.execute(operation);

        assertThat(executed).containsExactly("task.create", "task.assign");
        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.message()).contains("2");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) result.data().get("results");
        assertThat(results).hasSize(2);
        assertThat(results.get(0)).containsEntry("command", "task.create");
    }

    @Test
    void reportsWhichItemFailedAndThatTheBatchRolledBack() {
        when(registry.require(CommandName.TASK_CREATE)).thenReturn(new PmsCommand() {
            @Override
            public CommandName name() {
                return CommandName.TASK_CREATE;
            }

            @Override
            public CommandPreview preview(CommandPreviewRequest request) {
                return new CommandPreview(null, CommandName.TASK_CREATE, null, "v1", List.of(),
                        List.of(Map.of("entity", "child")), List.of("task-board"));
            }

            @Override
            public CommandResult execute(AiOperationDO operation) {
                throw BusinessException.conflict("任务已被其他人修改，请刷新后重试");
            }
        });
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-batch-2");
        operation.setArgumentsJson(write(operations(
                item("task.create", Map.of("title", "任务一")),
                item("task.assign", Map.of("taskId", 1)))));
        operation.setExpectedVersionsJson(write(List.of(batchExpectations(
                expectation("task.create", Map.of("projectVersion", 3)),
                expectation("task.assign", Map.of("taskVersion", 9))))));

        assertThatThrownBy(() -> command.execute(operation))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第 1 条（task.create）失败")
                .hasMessageContaining("任务已被其他人修改")
                .hasMessageContaining("整批已回滚");
        assertThat(executed).isEmpty();
    }

    @Test
    void rejectsAnUnsupportedChildCommand() {
        Map<String, Object> arguments = operations(item("totally.unknown", Map.of()));

        assertThatThrownBy(() -> command.preview(request(arguments)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的命令");
    }

    @Test
    void rejectsANestedBatch() {
        Map<String, Object> arguments = operations(item("batch.write", Map.of()));

        assertThatThrownBy(() -> command.preview(request(arguments)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能嵌套");
    }

    @Test
    void rejectsAnEmptyBatch() {
        assertThatThrownBy(() -> command.preview(request(Map.of("operations", List.of()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少一条");
    }

    @Test
    void rejectsAnOversizedBatch() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            items.add(item("task.create", Map.of("title", "任务" + index)));
        }

        assertThatThrownBy(() -> command.preview(request(Map.of("operations", items))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多 20 条");
    }

    @Test
    void rejectsAChildCommandTheNodeContractDoesNotAllow() {
        when(contractRegistry.isCommandAllowed(anyString(), anyString(), anyString())).thenReturn(false);
        Map<String, Object> arguments = operations(item("project.delete", Map.of("projectId", 22)));

        assertThatThrownBy(() -> command.preview(new CommandPreviewRequest(
                CommandName.BATCH_WRITE, arguments, "pms:project-detail:22:7", "v1",
                "pms-project-assistant/requirement", "1.0.0")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("契约不允许");
    }

    private CommandPreviewRequest request(Map<String, Object> arguments) {
        return new CommandPreviewRequest(CommandName.BATCH_WRITE, arguments, "pms:project-detail:22:7", "v1");
    }

    @SafeVarargs
    private static Map<String, Object> operations(Map<String, Object>... items) {
        return Map.of("operations", List.of(items));
    }

    private static Map<String, Object> item(String command, Map<String, Object> arguments) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("command", command);
        item.put("arguments", arguments);
        return item;
    }

    private static Map<String, Object> expectation(String command, Map<String, Object> change) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("command", command);
        entry.put("changes", List.of(change));
        return entry;
    }

    @SafeVarargs
    private static Map<String, Object> batchExpectations(Map<String, Object>... items) {
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "batch");
        change.put("items", List.of(items));
        return change;
    }

    private PmsCommand child(CommandName name) {
        return new PmsCommand() {
            @Override
            public CommandName name() {
                return name;
            }

            @Override
            public CommandPreview preview(CommandPreviewRequest request) {
                return new CommandPreview(null, name, null, "v1", List.of("子命令 " + name.code() + " 预览"),
                        List.of(Map.of("entity", "child", "command", name.code())), List.of("task-board"));
            }

            @Override
            public CommandResult execute(AiOperationDO operation) {
                executed.add(name.code());
                return new CommandResult(operation.getId(), "SUCCEEDED", name.code() + " 已执行",
                        Map.of("command", name.code()), List.of("task-board"));
            }
        };
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
