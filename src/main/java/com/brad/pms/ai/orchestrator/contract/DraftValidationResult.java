package com.brad.pms.ai.orchestrator.contract;

import java.util.List;

public record DraftValidationResult(List<ValidationError> errors) {

    public DraftValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }

    public List<String> errorCodes() {
        return errors.stream().map(ValidationError::code).distinct().toList();
    }

    public record ValidationError(String code, String path, String message) {
    }
}
