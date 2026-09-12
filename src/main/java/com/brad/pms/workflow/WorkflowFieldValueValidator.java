package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowFieldValueValidator {
    private WorkflowFieldValueValidator() { }

    public static Map<String, JsonNode> validate(List<WorkflowFieldDefinition> definitions,
                                                  Map<String, JsonNode> values) {
        Map<String, WorkflowFieldDefinition> byKey = new HashMap<>();
        for (WorkflowFieldDefinition definition : definitions) byKey.put(definition.key(), definition);
        Map<String, JsonNode> normalized = new LinkedHashMap<>();
        if (values == null) return normalized;
        for (Map.Entry<String, JsonNode> entry : values.entrySet()) {
            WorkflowFieldDefinition definition = byKey.get(entry.getKey());
            if (definition == null) throw new IllegalArgumentException("字段未配置: " + entry.getKey());
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                normalized.put(entry.getKey(), value);
                continue;
            }
            validateOne(definition, value);
            normalized.put(entry.getKey(), value);
        }
        return normalized;
    }

    public static List<String> missingRequiredFields(List<WorkflowFieldDefinition> definitions,
                                                      Map<String, JsonNode> values) {
        List<String> missing = new ArrayList<>();
        for (WorkflowFieldDefinition definition : definitions) {
            if (!definition.required()) continue;
            JsonNode value = values == null ? null : values.get(definition.key());
            if (isEmpty(value)) missing.add(definition.label());
        }
        return missing;
    }

    private static void validateOne(WorkflowFieldDefinition field, JsonNode value) {
        boolean valid = switch (field.type()) {
            case TEXT -> value.isTextual() && value.asText().length() <= 500;
            case TEXTAREA -> value.isTextual() && value.asText().length() <= 10000;
            case NUMBER -> value.isNumber();
            case DATE -> isDate(value);
            case SINGLE_SELECT -> value.isTextual() && field.options().contains(value.asText());
            case MULTI_SELECT -> isMultiSelect(field, value);
            case PERSON -> value.isIntegralNumber() && value.asLong() > 0;
            case ATTACHMENT -> isAttachmentList(value);
        };
        if (!valid) {
            String reason = field.type() == WorkflowFieldType.SINGLE_SELECT || field.type() == WorkflowFieldType.MULTI_SELECT
                    ? "选项无效" : field.type() == WorkflowFieldType.DATE ? "日期格式无效" : "字段值类型无效";
            throw new IllegalArgumentException(field.label() + reason);
        }
    }

    private static boolean isDate(JsonNode value) {
        if (!value.isTextual()) return false;
        try {
            LocalDate.parse(value.asText());
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isMultiSelect(WorkflowFieldDefinition field, JsonNode value) {
        if (!value.isArray()) return false;
        Set<String> selected = new HashSet<>();
        for (JsonNode option : value) {
            if (!option.isTextual() || !field.options().contains(option.asText()) || !selected.add(option.asText())) return false;
        }
        return true;
    }

    private static boolean isAttachmentList(JsonNode value) {
        if (!value.isArray()) return false;
        Set<Long> ids = new HashSet<>();
        for (JsonNode attachmentId : value) {
            if (!attachmentId.isIntegralNumber() || attachmentId.asLong() <= 0 || !ids.add(attachmentId.asLong())) return false;
        }
        return true;
    }

    private static boolean isEmpty(JsonNode value) {
        return value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())
                || ((value.isArray() || value.isObject()) && value.isEmpty());
    }
}
