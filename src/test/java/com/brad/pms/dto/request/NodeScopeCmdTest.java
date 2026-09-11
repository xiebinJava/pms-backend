package com.brad.pms.dto.request;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NodeScopeCmdTest {

    @Test
    void taskCommandCarriesTheSelectedNodeId() {
        TaskCreateCmd task = new TaskCreateCmd();
        task.setNodeId(12L);

        assertEquals(12L, task.getNodeId());
    }

    @Test
    void projectIdIsSuppliedByTheTaskPath() {
        TaskCreateCmd task = new TaskCreateCmd();
        task.setTitle("任务标题");

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertFalse(validator.validate(task).stream()
                .anyMatch(error -> "projectId".equals(error.getPropertyPath().toString())));
    }
}
