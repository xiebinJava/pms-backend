package com.brad.pms.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTypeSaveCmdTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAProjectTypeNameWithoutAClientProvidedCode() {
        ProjectTypeSaveCmd command = new ProjectTypeSaveCmd();
        command.setName("故事流程");
        command.setDescription("用于配置故事流程");

        assertTrue(validator.validate(command).isEmpty());
    }

    @Test
    void stillRequiresAProjectTypeName() {
        ProjectTypeSaveCmd command = new ProjectTypeSaveCmd();
        assertTrue(validator.validate(command).stream()
                .anyMatch(error -> "name".equals(error.getPropertyPath().toString())));
    }
}
