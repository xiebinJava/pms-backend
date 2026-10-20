package com.brad.pms.ai.orchestrator.provenance;

import com.brad.pms.ai.connector.AiProvenanceValidator;
import com.brad.pms.ai.orchestrator.adapter.ResourceNodeBinding;
import com.brad.pms.ai.orchestrator.contract.AgentDraftItem;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computes the frozen draft-provenance/v1 digest and wire provenance for
 * resource-node orchestration plans. The canonical JSON mirrors the connector
 * implementation byte-for-byte: object keys are sorted recursively,
 * sourceRefs arrays are sorted lexicographically, other arrays keep their
 * declaration order, unknown version axes stay explicit nulls, and the digest
 * is a lowercase sha256 with the sha256: prefix.
 */
@Component
public class DraftProvenanceDigestService {

    public static final String CONTRACT_VERSION = "draft-provenance/v1";

    private final ObjectMapper objectMapper;

    public DraftProvenanceDigestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record DigestInput(
            String contractVersion,
            String resourceType,
            Long resourceId,
            Long nodeId,
            String componentKey,
            String intent,
            Boolean requestedCompletion,
            Map<String, Object> versionSnapshot,
            List<Map<String, Object>> facts,
            List<Map<String, Object>> inferences,
            List<Map<String, Object>> assumptions,
            List<Map<String, Object>> unresolvedDecisions,
            Map<String, Object> patch,
            Map<String, Object> fieldSources) {
    }

    /** Fixed five-axis version snapshot; unknown axes stay explicit nulls. */
    public static Map<String, Object> versionSnapshot(ResourceNodeBinding binding) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nodeVersion", binding.nodeVersion());
        snapshot.put("entityVersion", binding.entityVersion());
        snapshot.put("workflowInstanceVersion", binding.workflowInstanceVersion());
        snapshot.put("componentVersion", binding.componentVersion());
        snapshot.put("contextVersion", binding.contextVersion());
        return snapshot;
    }

    /** AnalysisDraft item projection; absent confidence stays absent. */
    public static List<Map<String, Object>> items(List<AgentDraftItem> items) {
        if (items == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(items.size());
        for (AgentDraftItem item : items) {
            Map<String, Object> projected = new LinkedHashMap<>();
            projected.put("text", item.text());
            projected.put("sourceRefs", item.sourceRefs() == null ? List.of() : item.sourceRefs());
            if (item.confidence() != null) projected.put("confidence", item.confidence());
            result.add(projected);
        }
        return result;
    }

    /** Builds the wire provenance object sent as CommandPreviewRequest.provenance. */
    public Map<String, Object> buildProvenance(DigestInput input) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("contractVersion", CONTRACT_VERSION);
        provenance.put("draftDigest", compute(input));
        provenance.put("fieldSources", input.fieldSources());
        AiProvenanceValidator.validate(provenance, objectMapper);
        return provenance;
    }

    public String compute(DigestInput input) {
        if (input == null || input.fieldSources() == null) {
            throw BusinessException.error("PROVENANCE_INVALID: 来源和草稿内容不能为空");
        }
        String canonical = canonicalJson(input);
        return "sha256:" + sha256(canonical);
    }

    /**
     * Verifies that a PMS preview is bound to the plan provenance before the
     * plan may enter PREVIEWED/WAITING_CONFIRMATION.
     */
    public void verifyPreviewBinding(String previewOperationId, AiOperationPreviewDTO preview,
                                     Map<String, Object> provenance) {
        if (preview == null || previewOperationId == null || previewOperationId.isBlank()) {
            throw BusinessException.error("PROVENANCE_INVALID: preview 缺失，无法绑定 provenance");
        }
        if (preview.expectedVersions() == null || preview.expectedVersions().isEmpty()) {
            throw BusinessException.error("PROVENANCE_INVALID: PMS 未返回 expectedVersions");
        }
        String expectedRef = "operation:" + previewOperationId;
        if (!Objects.equals(expectedRef, preview.provenanceRef())) {
            throw BusinessException.error("PROVENANCE_INVALID: provenanceRef 与 preview 不绑定: "
                    + preview.provenanceRef());
        }
        if (provenance == null || !Objects.equals(provenance.get("draftDigest"), preview.draftDigest())) {
            throw BusinessException.error("PROVENANCE_INVALID: PMS 回显的 draftDigest 与计划不一致");
        }
    }

    private String canonicalJson(DigestInput input) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("contractVersion", input.contractVersion());
        root.put("resourceType", input.resourceType());
        root.put("resourceId", input.resourceId());
        root.put("nodeId", input.nodeId());
        root.put("componentKey", input.componentKey());
        root.put("intent", input.intent());
        root.put("requestedCompletion", input.requestedCompletion());
        root.put("versionSnapshot", input.versionSnapshot());
        root.put("facts", input.facts());
        root.put("inferences", input.inferences());
        root.put("assumptions", input.assumptions());
        root.put("unresolvedDecisions", input.unresolvedDecisions());
        root.put("patch", input.patch());
        root.put("fieldSources", input.fieldSources());
        return objectMapper.valueToTree(canonicalize(root, null)).toString();
    }

    /**
     * Mirrors the connector canonicalizer: object keys are sorted recursively,
     * sourceRefs arrays are sorted lexicographically and every other array
     * keeps its declaration order; explicit nulls are preserved.
     */
    private JsonNode canonicalize(Object value, String key) {
        if (value == null) return NullNode.getInstance();
        if (value instanceof Map<?, ?> map) {
            ObjectNode node = objectMapper.createObjectNode();
            map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .forEach(entry -> node.set(String.valueOf(entry.getKey()),
                            canonicalize(entry.getValue(), String.valueOf(entry.getKey()))));
            return node;
        }
        if (value instanceof List<?> list) {
            List<JsonNode> items = new ArrayList<>(list.size());
            for (Object item : list) {
                items.add(canonicalize(item, null));
            }
            if ("sourceRefs".equals(key)) {
                items.sort(Comparator.comparing(JsonNode::asText));
            }
            ArrayNode array = objectMapper.createArrayNode();
            items.forEach(array::add);
            return array;
        }
        return objectMapper.valueToTree(value);
    }

    private String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
