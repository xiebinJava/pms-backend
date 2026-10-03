package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Validates and merges the structured state of a story node workbench variant. */
public final class StoryNodeWorkbenchPolicy {
    private record FieldSpec(int maxLength, boolean requiresUrl, boolean requiresDate) {
        static FieldSpec text(int maxLength) { return new FieldSpec(maxLength, false, false); }
        static FieldSpec url(int maxLength) { return new FieldSpec(maxLength, true, false); }
        static FieldSpec date() { return new FieldSpec(10, false, true); }
    }

    private static final Map<String, Map<String, FieldSpec>> VARIANTS = Map.of(
            "writing", Map.of(
                    "acceptanceCriteria", FieldSpec.text(2000),
                    "background", FieldSpec.text(2000)),
            "iteration", Map.of(
                    "iterationName", FieldSpec.text(200),
                    "meetingNote", FieldSpec.text(2000),
                    "dependencies", FieldSpec.text(2000)),
            "development", Map.of(
                    "implementationNote", FieldSpec.text(2000),
                    "selfTestResult", FieldSpec.text(2000),
                    "codeLink", FieldSpec.url(1000)),
            "acceptance", Map.of(
                    "acceptanceConclusion", FieldSpec.text(2000),
                    "acceptanceNote", FieldSpec.text(2000)),
            "release", Map.of(
                    "releaseVersion", FieldSpec.text(200),
                    "releaseWindow", FieldSpec.text(200),
                    "releaseNote", FieldSpec.text(2000)),
            "launch", Map.of(
                    "launchDate", FieldSpec.date(),
                    "launchVerification", FieldSpec.text(2000),
                    "retrospective", FieldSpec.text(2000)));

    private StoryNodeWorkbenchPolicy() { }

    public static boolean supports(String variant) {
        return variant != null && VARIANTS.containsKey(variant);
    }

    public static Set<String> variants() {
        return VARIANTS.keySet();
    }

    /** Only whitelisted fields are writable; unknown incoming fields are dropped and server history is preserved. */
    public static ObjectNode merge(JsonNode existing, JsonNode incoming, String variant) {
        validate(incoming, variant);
        ObjectNode merged = existing != null && existing.isObject()
                ? (ObjectNode) existing.deepCopy() : JsonNodeFactory.instance.objectNode();
        for (String key : VARIANTS.get(variant).keySet()) {
            if (incoming.has(key)) merged.set(key, incoming.get(key).deepCopy());
        }
        return merged;
    }

    public static void validate(JsonNode state, String variant) {
        Map<String, FieldSpec> specs = VARIANTS.get(variant);
        if (specs == null) throw new IllegalArgumentException("故事节点工作台类型无效");
        if (state == null || !state.isObject()) throw new IllegalArgumentException("故事节点工作台内容无效");
        for (Map.Entry<String, FieldSpec> entry : specs.entrySet()) {
            text(state.get(entry.getKey()), entry.getValue());
        }
    }

    private static void text(JsonNode value, FieldSpec spec) {
        if (value == null || value.isNull()) return;
        if (!value.isTextual()) throw new IllegalArgumentException("故事节点工作台字段格式不正确");
        String text = value.asText();
        if (text.length() > spec.maxLength()) throw new IllegalArgumentException("故事节点工作台字段超过长度限制");
        if (spec.requiresUrl() && !text.isBlank() && !validHttpUrl(text)) {
            throw new IllegalArgumentException("请填写有效的 http 或 https 链接");
        }
        if (spec.requiresDate() && !text.isBlank() && !validDate(text.trim())) {
            throw new IllegalArgumentException("请填写有效的日期");
        }
    }

    private static boolean validHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean validDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
