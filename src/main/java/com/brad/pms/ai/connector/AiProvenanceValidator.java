package com.brad.pms.ai.connector;

import com.brad.pms.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the persisted draft provenance envelope at the PMS boundary. */
public final class AiProvenanceValidator {

    public static final String CONTRACT_VERSION = "draft-provenance/v1";
    public static final int MAX_FIELDS = 200;
    public static final int MAX_REFS_PER_FIELD = 20;
    public static final int MAX_SERIALIZED_BYTES = 64 * 1024;

    private static final Pattern DIGEST = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final Pattern RESOURCE_REF = Pattern.compile(
            "^(project|requirement|topic|story|task|subtask|iteration_plan|workflow_template|user):[1-9]\\d*$");
    private static final Pattern NODE_REF = Pattern.compile(
            "^(project|requirement|topic|story)-node:[1-9]\\d*(#component:[A-Za-z0-9][A-Za-z0-9._-]{0,159})?$");
    private static final Set<String> PROVENANCE_KEYS = Set.of("contractVersion", "draftDigest", "fieldSources");
    private static final Set<String> FIELD_SOURCE_KEYS = Set.of("sourceType", "sourceRefs", "confidence");
    private static final Set<String> SOURCE_TYPES = Set.of("pms_context", "conversation", "inference");
    private static final Set<String> CONFIDENCE = Set.of("high", "medium", "low");

    private AiProvenanceValidator() {
    }

    public static Validated validate(Map<String, Object> provenance, ObjectMapper objectMapper) {
        if (provenance == null) return null;
        JsonNode root = objectMapper.valueToTree(provenance);
        if (!root.isObject()) invalid("provenance 必须是对象");
        requireKeys(root, PROVENANCE_KEYS, "provenance");
        if (!CONTRACT_VERSION.equals(text(root, "contractVersion"))) {
            invalid("不支持的 provenance contractVersion");
        }
        String digest = text(root, "draftDigest");
        if (!DIGEST.matcher(digest).matches()) invalid("draftDigest 格式无效");

        JsonNode fieldSources = root.get("fieldSources");
        if (fieldSources == null || !fieldSources.isObject()) invalid("fieldSources 必须是对象");
        if (fieldSources.size() > MAX_FIELDS) {
            tooLarge("fieldSources 最多支持 200 个字段");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = fieldSources.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getKey().isBlank() || field.getKey().length() > 160) {
                invalid("fieldSources 字段名长度无效");
            }
            JsonNode source = field.getValue();
            if (source == null || !source.isObject()) invalid("字段来源必须是对象");
            requireKeys(source, FIELD_SOURCE_KEYS, "fieldSources." + field.getKey());
            if (!SOURCE_TYPES.contains(text(source, "sourceType"))) invalid("sourceType 无效");
            if (!CONFIDENCE.contains(text(source, "confidence"))) invalid("confidence 无效");
            JsonNode refs = source.get("sourceRefs");
            if (refs == null || !refs.isArray() || refs.isEmpty()) invalid("sourceRefs 不能为空");
            if (refs.size() > MAX_REFS_PER_FIELD) tooLarge("每个字段最多支持 20 个 sourceRefs");
            for (JsonNode ref : refs) {
                String value = ref == null || !ref.isTextual() ? "" : ref.asText().trim();
                if (!RESOURCE_REF.matcher(value).matches() && !NODE_REF.matcher(value).matches()) {
                    invalid("sourceRef 必须是可读取的 PMS 资源、节点或组件引用");
                }
            }
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            invalid("provenance 无法序列化");
            return null;
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_SERIALIZED_BYTES) {
            tooLarge("provenance 序列化后不得超过 64 KiB");
        }
        return new Validated(json, digest);
    }

    private static void requireKeys(JsonNode object, Set<String> allowed, String path) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) invalid(path + " 包含未知字段: " + name);
        }
        for (String required : allowed) {
            if (!object.has(required)) invalid(path + " 缺少字段: " + required);
        }
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) invalid(field + " 不能为空");
        return value.asText().trim();
    }

    private static void invalid(String message) {
        throw BusinessException.error("PROVENANCE_INVALID: " + message);
    }

    private static void tooLarge(String message) {
        throw BusinessException.error("PROVENANCE_TOO_LARGE: " + message);
    }

    public record Validated(String json, String draftDigest) {
    }
}
