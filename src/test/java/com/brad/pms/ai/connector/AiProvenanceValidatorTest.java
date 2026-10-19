package com.brad.pms.ai.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProvenanceValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsResourceNodeAndComponentReferencesAndPreservesTheDigest() {
        Map<String, Object> provenance = Map.of(
                "contractVersion", "draft-provenance/v1",
                "draftDigest", "sha256:" + "a".repeat(64),
                "fieldSources", Map.of(
                        "resource", source("project:42"),
                        "node", source("requirement-node:3"),
                        "component", source("story-node:7#component:story-node-workbench")));

        AiProvenanceValidator.Validated validated = AiProvenanceValidator.validate(provenance, objectMapper);

        assertThat(validated.draftDigest()).isEqualTo("sha256:" + "a".repeat(64));
        assertThat(validated.json()).contains("story-node:7#component:story-node-workbench");
    }

    @Test
    void rejectsConversationAndPmsNamespaces() {
        assertThatThrownBy(() -> AiProvenanceValidator.validate(
                provenanceWithRef("conversation:latest"), objectMapper))
                .hasMessageContaining("PROVENANCE_INVALID");
        assertThatThrownBy(() -> AiProvenanceValidator.validate(
                provenanceWithRef("pms:context:requirement:12"), objectMapper))
                .hasMessageContaining("PROVENANCE_INVALID");
    }

    @Test
    void rejectsTooManyFieldsAndRefs() {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int index = 0; index < 201; index++) fields.put("field-" + index, source("project:42"));
        assertThatThrownBy(() -> AiProvenanceValidator.validate(Map.of(
                "contractVersion", "draft-provenance/v1",
                "draftDigest", "sha256:" + "b".repeat(64),
                "fieldSources", fields), objectMapper))
                .hasMessageContaining("PROVENANCE_TOO_LARGE");

        List<String> refs = new ArrayList<>();
        for (int index = 1; index <= 21; index++) refs.add("project:" + index);
        assertThatThrownBy(() -> AiProvenanceValidator.validate(Map.of(
                "contractVersion", "draft-provenance/v1",
                "draftDigest", "sha256:" + "c".repeat(64),
                "fieldSources", Map.of("field", source(refs))), objectMapper))
                .hasMessageContaining("PROVENANCE_TOO_LARGE");
    }

    private static Map<String, Object> source(String ref) {
        return Map.of("sourceType", "pms_context", "sourceRefs", List.of(ref), "confidence", "high");
    }

    private static Map<String, Object> source(List<String> refs) {
        return Map.of("sourceType", "pms_context", "sourceRefs", refs, "confidence", "high");
    }

    private static Map<String, Object> provenanceWithRef(String ref) {
        return Map.of(
                "contractVersion", "draft-provenance/v1",
                "draftDigest", "sha256:" + "d".repeat(64),
                "fieldSources", Map.of("field", source(ref)));
    }
}
