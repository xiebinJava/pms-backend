package com.brad.pms.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectTypeSaveCmdTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsGeneratedNumericProjectTypeCodesAndExistingAlphaCodes() {
        assertValidCode("001");
        assertValidCode("0001");
        assertValidCode("general");
        assertValidCode("PRODUCT-TYPE_2");
    }

    @Test
    void rejectsCodesThatAreNeitherNumericNorAlphaPrefixedIdentifiers() {
        assertTrue(hasCodeViolation("-TYPE"));
        assertTrue(hasCodeViolation("TYPE CODE"));
        assertTrue(hasCodeViolation("类型"));
    }

    private void assertValidCode(String code) {
        assertFalse(hasCodeViolation(code), () -> "Expected project type code to be valid: " + code);
    }

    private boolean hasCodeViolation(String code) {
        ProjectTypeSaveCmd command = new ProjectTypeSaveCmd();
        command.setCode(code);
        command.setName("测试类型");
        return validator.validate(command).stream()
                .anyMatch(error -> "code".equals(error.getPropertyPath().toString()));
    }
}
