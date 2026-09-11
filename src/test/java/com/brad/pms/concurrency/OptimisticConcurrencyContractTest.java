package com.brad.pms.concurrency;

import com.brad.pms.dto.request.NodeOwnerUpdateCmd;
import com.brad.pms.dto.request.NodeScheduleUpdateCmd;
import com.brad.pms.dto.request.ProjectUpdateCmd;
import com.brad.pms.dto.request.TaskMoveCmd;
import com.brad.pms.dto.request.TaskUpdateCmd;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.dto.response.ProjectTaskDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticConcurrencyContractTest {

    @Test
    void editorCommandsAndResponsesCarryVersions() {
        assertThat(fieldNames(ProjectUpdateCmd.class)).contains("version");
        assertThat(fieldNames(TaskUpdateCmd.class)).contains("version");
        assertThat(fieldNames(TaskMoveCmd.class)).contains("version");
        assertThat(fieldNames(NodeOwnerUpdateCmd.class)).contains("version");
        assertThat(fieldNames(NodeScheduleUpdateCmd.class)).contains("version");
        assertThat(fieldNames(ProjectDTO.class)).contains("version");
        assertThat(fieldNames(ProjectTaskDTO.class)).contains("version");
        assertThat(fieldNames(ProjectNodeDTO.class)).contains("version");
    }

    private static Stream<String> fieldNames(Class<?> type) {
        return Stream.of(type.getDeclaredFields()).map(Field::getName);
    }
}
