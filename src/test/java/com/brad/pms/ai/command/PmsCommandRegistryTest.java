package com.brad.pms.ai.command;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PmsCommandRegistryTest {

    @Test
    void registryListsOnlyRegisteredWriteCommandsAndResolvesByStableName() {
        PmsCommand create = command(CommandName.TASK_CREATE);
        PmsCommand assign = command(CommandName.TASK_ASSIGN);
        PmsCommandRegistry registry = new PmsCommandRegistry(List.of(create, assign));

        assertThat(registry.list()).containsExactlyInAnyOrder(CommandName.TASK_CREATE, CommandName.TASK_ASSIGN);
        assertThat(registry.require(CommandName.TASK_CREATE)).isSameAs(create);
        assertThatThrownBy(() -> registry.require(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");
    }

    @Test
    void duplicateCommandNamesAreRejectedAtConstruction() {
        assertThatThrownBy(() -> new PmsCommandRegistry(List.of(
                command(CommandName.TASK_CREATE), command(CommandName.TASK_CREATE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    private static PmsCommand command(CommandName name) {
        return new PmsCommand() {
            @Override public CommandName name() { return name; }
            @Override public CommandPreview preview(CommandPreviewRequest request) {
                return new CommandPreview(null, name, Instant.now(), request.contextVersion(),
                        List.of(), List.of(Map.of()), List.of("project-detail"));
            }
            @Override public CommandResult execute(com.brad.pms.entity.AiOperationDO operation) {
                return new CommandResult(operation.getId(), "SUCCEEDED", "ok", Map.of(), List.of());
            }
        };
    }
}
