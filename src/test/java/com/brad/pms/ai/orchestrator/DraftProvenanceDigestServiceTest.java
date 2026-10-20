package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.orchestrator.adapter.ResourceNodeBinding;
import com.brad.pms.ai.orchestrator.contract.AgentDraftItem;
import com.brad.pms.ai.orchestrator.provenance.DraftProvenanceDigestService;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftProvenanceDigestServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DraftProvenanceDigestService service = new DraftProvenanceDigestService(objectMapper);

    @Test
    void matchesTheSharedGoldenVectorByteForByte() throws IOException {
        JsonNode fixture = readFixture();
        DraftProvenanceDigestService.DigestInput input = digestInput(fixture);

        assertThat(service.compute(input)).isEqualTo(fixture.get("expectedDraftDigest").asText());
        assertThat(fixture.get("expectedProvenanceRef").asText()).isEqualTo("operation:preview-op-42");

        Map<String, Object> provenance = service.buildProvenance(input);
        assertThat(provenance).containsOnlyKeys("contractVersion", "draftDigest", "fieldSources");
        assertThat(provenance.get("contractVersion")).isEqualTo("draft-provenance/v1");
        assertThat(provenance.get("draftDigest")).isEqualTo(fixture.get("expectedDraftDigest").asText());
    }

    @Test
    void isStableUnderObjectKeyOrderAndSourceRefOrder() {
        DraftProvenanceDigestService.DigestInput first = sampleInput(
                List.of("project-node:301#component:solution-design", "project:42"));
        DraftProvenanceDigestService.DigestInput second = sampleInput(
                List.of("project:42", "project-node:301#component:solution-design"));

        assertThat(service.compute(first)).isEqualTo(service.compute(second));
        assertThat(service.compute(first)).matches("^sha256:[0-9a-f]{64}$");
    }

    @Test
    void preservesDeclarationOrderForNonReferenceArrays() {
        DraftProvenanceDigestService.DigestInput input = new DraftProvenanceDigestService.DigestInput(
                "draft-provenance/v1", "project", 42L, 301L, "solution-design",
                "RESOURCE_NODE_FIELD_UPDATE", false, versionSnapshot(null, 3L, 2, 4, 3L),
                List.of(fact("第一条", List.of("project:42"))),
                List.of(fact("第二条", List.of("project:42"))),
                List.of(), List.of(), Map.of("testResult", "PASS"),
                Map.of("testResult", fieldSource(List.of("project:42"))));

        DraftProvenanceDigestService.DigestInput reordered = new DraftProvenanceDigestService.DigestInput(
                "draft-provenance/v1", "project", 42L, 301L, "solution-design",
                "RESOURCE_NODE_FIELD_UPDATE", false, versionSnapshot(null, 3L, 2, 4, 3L),
                List.of(fact("第二条", List.of("project:42"))),
                List.of(fact("第一条", List.of("project:42"))),
                List.of(), List.of(), Map.of("testResult", "PASS"),
                Map.of("testResult", fieldSource(List.of("project:42"))));

        assertThat(service.compute(input)).isNotEqualTo(service.compute(reordered));
    }

    @Test
    void changesWithAnyVersionAxis() {
        DraftProvenanceDigestService.DigestInput baseline = sampleInput(List.of("project:42"));
        String digest = service.compute(baseline);
        assertThat(service.compute(withSnapshot(sampleInput(List.of("project:42")),
                versionSnapshot(7L, 3L, 2, 4, 3L))))
                .isNotEqualTo(digest);
        assertThat(service.compute(withSnapshot(sampleInput(List.of("project:42")),
                versionSnapshot(null, 4L, 2, 4, 3L))))
                .isNotEqualTo(digest);
        assertThat(service.compute(withSnapshot(sampleInput(List.of("project:42")),
                versionSnapshot(null, 3L, 9, 4, 3L))))
                .isNotEqualTo(digest);
        assertThat(service.compute(withSnapshot(sampleInput(List.of("project:42")),
                versionSnapshot(null, 3L, 2, 5, 3L))))
                .isNotEqualTo(digest);
        assertThat(service.compute(withSnapshot(sampleInput(List.of("project:42")),
                versionSnapshot(null, 3L, 2, 4, 6L))))
                .isNotEqualTo(digest);
    }

    @Test
    void omitsAbsentItemConfidenceButKeepsExplicitVersionNulls() {
        List<Map<String, Object>> items = DraftProvenanceDigestService.items(
                List.of(new AgentDraftItem("事实", List.of("project:42"), null)));
        assertThat(items.get(0)).containsOnlyKeys("text", "sourceRefs");

        List<Map<String, Object>> withConfidence = DraftProvenanceDigestService.items(
                List.of(new AgentDraftItem("事实", List.of("project:42"), "high")));
        assertThat(withConfidence.get(0)).containsOnlyKeys("text", "sourceRefs", "confidence");

        Map<String, Object> snapshot = DraftProvenanceDigestService.versionSnapshot(
                new ResourceNodeBinding("project", 42L, 301L, 6L, "solution-design", Map.of(),
                        List.of("testResult"), List.of(), true, null, null, null, null));
        assertThat(snapshot).containsOnlyKeys(
                "nodeVersion", "entityVersion", "workflowInstanceVersion", "componentVersion", "contextVersion");
        assertThat(snapshot.get("entityVersion")).isNull();
    }

    @Test
    void rejectsUnboundPreviewsBeforePlanExposure() {
        Map<String, Object> provenance = service.buildProvenance(sampleInput(List.of("project:42")));

        assertThatThrownBy(() -> service.verifyPreviewBinding("op-1",
                new AiOperationPreviewDTO("op-1", "node.field.update", Instant.now(),
                        "3", Map.of(), List.of(), List.of(), List.of(), List.of(),
                        "operation:op-1", provenance.get("draftDigest").toString()), provenance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expectedVersions");

        assertThatThrownBy(() -> service.verifyPreviewBinding("op-1",
                preview("op-1", "operation:op-other", provenance), provenance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("provenanceRef");

        assertThatThrownBy(() -> service.verifyPreviewBinding("op-1",
                preview("op-1", "operation:op-1", Map.of("contractVersion", "draft-provenance/v1",
                        "draftDigest", "sha256:0000000000000000000000000000000000000000000000000000000000000000",
                        "fieldSources", Map.of())), provenance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("draftDigest");

        assertThatCode(() -> service.verifyPreviewBinding("op-1",
                preview("op-1", "operation:op-1", provenance), provenance))
                .doesNotThrowAnyException();
    }

    private AiOperationPreviewDTO preview(String operationId, String provenanceRef,
                                          Map<String, Object> provenance) {
        return new AiOperationPreviewDTO(operationId, "node.field.update", Instant.now(), "3",
                Map.of("nodeVersion", 6, "entityVersion", 3), List.of(), List.of(), List.of(), List.of(),
                provenanceRef, provenance.get("draftDigest").toString());
    }

    private DraftProvenanceDigestService.DigestInput sampleInput(List<String> sourceRefs) {
        List<String> refs = new ArrayList<>(sourceRefs);
        return new DraftProvenanceDigestService.DigestInput(
                "draft-provenance/v1", "project", 42L, 301L, "solution-design",
                "RESOURCE_NODE_FIELD_UPDATE", false, versionSnapshot(null, 3L, 2, 4, 3L),
                List.of(fact("测试结果为通过", refs)),
                List.of(), List.of(), List.of(), Map.of("testResult", "PASS"),
                Map.of("testResult", fieldSource(refs)));
    }

    private Map<String, Object> fact(String text, List<String> refs) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("text", text);
        fact.put("sourceRefs", refs);
        fact.put("confidence", "high");
        return fact;
    }

    private DraftProvenanceDigestService.DigestInput withSnapshot(
            DraftProvenanceDigestService.DigestInput input, Map<String, Object> snapshot) {
        return new DraftProvenanceDigestService.DigestInput(
                input.contractVersion(), input.resourceType(), input.resourceId(), input.nodeId(),
                input.componentKey(), input.intent(), input.requestedCompletion(), snapshot,
                input.facts(), input.inferences(), input.assumptions(), input.unresolvedDecisions(),
                input.patch(), input.fieldSources());
    }

    private Map<String, Object> versionSnapshot(Object nodeVersion, Object entityVersion,
                                                Object workflowInstanceVersion,
                                                Object componentVersion, Object contextVersion) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeVersion", nodeVersion);
        snapshot.put("entityVersion", entityVersion);
        snapshot.put("workflowInstanceVersion", workflowInstanceVersion);
        snapshot.put("componentVersion", componentVersion);
        snapshot.put("contextVersion", contextVersion);
        return snapshot;
    }

    private Map<String, Object> fieldSource(List<String> refs) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceType", "pms_context");
        source.put("sourceRefs", refs);
        source.put("confidence", "high");
        return source;
    }

    @SuppressWarnings("unchecked")
    private DraftProvenanceDigestService.DigestInput digestInput(JsonNode fixture) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        JsonNode versions = fixture.get("versionSnapshot");
        snapshot.put("nodeVersion", versions.get("nodeVersion").asInt());
        snapshot.put("entityVersion", versions.get("entityVersion").asInt());
        snapshot.put("workflowInstanceVersion", versions.get("workflowInstanceVersion").asInt());
        snapshot.put("componentVersion", versions.get("componentVersion").asInt());
        snapshot.put("contextVersion", versions.get("contextVersion").asInt());

        List<Map<String, Object>> facts = new ArrayList<>();
        for (JsonNode item : fixture.get("facts")) {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("text", item.get("text").asText());
            fact.put("sourceRefs", objectMapper.convertValue(item.get("sourceRefs"), List.class));
            if (item.has("confidence")) fact.put("confidence", item.get("confidence").asText());
            facts.add(fact);
        }

        return new DraftProvenanceDigestService.DigestInput(
                fixture.get("contractVersion").asText(),
                fixture.get("resourceType").asText(),
                fixture.get("resourceId").asLong(),
                fixture.get("nodeId").asLong(),
                fixture.get("componentKey").asText(),
                fixture.get("intent").asText(),
                fixture.get("requestedCompletion").asBoolean(),
                snapshot,
                facts,
                List.of(), List.of(), List.of(),
                objectMapper.convertValue(fixture.get("patch"), Map.class),
                objectMapper.convertValue(fixture.get("fieldSources"), Map.class));
    }

    private JsonNode readFixture() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
                "/fixtures/provenance/draft-provenance-v1.json")) {
            assertThat(stream).isNotNull();
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readTree(json);
        }
    }
}
