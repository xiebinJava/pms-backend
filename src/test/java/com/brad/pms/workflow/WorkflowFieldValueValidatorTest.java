package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowFieldValueValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reportsOnlyMissingRequiredFieldsAndTreatsBlankAndEmptyAsMissing() throws Exception {
        var fields = List.of(
                field("title", WorkflowFieldType.TEXT, true, List.of()),
                field("owners", WorkflowFieldType.MULTI_SELECT, true, List.of("A", "B")),
                field("optional", WorkflowFieldType.TEXT, false, List.of()));
        Map<String, JsonNode> values = Map.of(
                "title", mapper.readTree("\"  \""),
                "owners", mapper.readTree("[]"),
                "optional", mapper.readTree("null"));

        assertThat(WorkflowFieldValueValidator.missingRequiredFields(fields, values))
                .containsExactly("标题", "负责人");
    }

    @Test
    void validatesFieldTypesSelectOptionsDatesAndAttachmentReferences() throws Exception {
        var fields = List.of(
                field("count", WorkflowFieldType.NUMBER, false, List.of()),
                field("due", WorkflowFieldType.DATE, false, List.of()),
                field("kind", WorkflowFieldType.SINGLE_SELECT, false, List.of("A", "B")),
                field("labels", WorkflowFieldType.MULTI_SELECT, false, List.of("A", "B")),
                field("owner", WorkflowFieldType.PERSON, false, List.of()),
                field("files", WorkflowFieldType.ATTACHMENT, false, List.of()));

        assertThat(WorkflowFieldValueValidator.validate(fields, Map.of(
                "count", mapper.readTree("12.5"),
                "due", mapper.readTree("\"2026-09-12\""),
                "kind", mapper.readTree("\"A\""),
                "labels", mapper.readTree("[\"A\",\"B\"]"),
                "owner", mapper.readTree("42"),
                "files", mapper.readTree("[7,8]")))).hasSize(6);

        assertThatThrownBy(() -> WorkflowFieldValueValidator.validate(fields,
                Map.of("kind", mapper.readTree("\"C\""))))
                .hasMessageContaining("选项无效");
        assertThatThrownBy(() -> WorkflowFieldValueValidator.validate(fields,
                Map.of("due", mapper.readTree("\"tomorrow\""))))
                .hasMessageContaining("日期格式无效");
    }

    @Test
    void rejectsUnknownFieldsAndWrongJsonTypes() throws Exception {
        var fields = List.of(field("title", WorkflowFieldType.TEXT, false, List.of()));

        assertThatThrownBy(() -> WorkflowFieldValueValidator.validate(fields,
                Map.of("other", mapper.readTree("\"x\""))))
                .hasMessageContaining("字段未配置");
        assertThatThrownBy(() -> WorkflowFieldValueValidator.validate(fields,
                Map.of("title", mapper.readTree("42"))))
                .hasMessageContaining("字段值类型无效");
    }

    private WorkflowFieldDefinition field(String key, WorkflowFieldType type, boolean required, List<String> options) {
        String label = switch (key) {
            case "title" -> "标题";
            case "owners" -> "负责人";
            default -> key;
        };
        return new WorkflowFieldDefinition(key, label, type, required, options);
    }
}
