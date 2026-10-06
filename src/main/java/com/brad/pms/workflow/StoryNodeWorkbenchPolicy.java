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
                    "background", FieldSpec.text(2000),
                    "descriptionAndAcceptance", FieldSpec.text(5000),
                    "priority", FieldSpec.text(6)),
            "iteration", Map.of(
                    "iterationName", FieldSpec.text(200),
                    "meetingNote", FieldSpec.text(2000),
                    "dependencies", FieldSpec.text(2000),
                    "iterationPlanId", FieldSpec.text(20)),
            "development", Map.of(
                    "implementationNote", FieldSpec.text(2000),
                    "selfTestResult", FieldSpec.text(2000),
                    "codeLink", FieldSpec.url(2000),
                    "mergeStatus", FieldSpec.text(20),
                    "deployEnv", FieldSpec.text(100)),
            "acceptance", Map.of(
                    "acceptanceConclusion", FieldSpec.text(2000),
                    "acceptanceNote", FieldSpec.text(2000)),
            "release", Map.of(
                    "iterationPlanId", FieldSpec.text(20)),
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
        Set<String> writable = new java.util.LinkedHashSet<>(VARIANTS.get(variant).keySet());
        if ("iteration".equals(variant)) {
            writable.add("developerIds");
            writable.add("testerIds");
        }
        for (String key : writable) {
            if (incoming.has(key)) merged.set(key, incoming.get(key).deepCopy());
        }
        if ("development".equals(variant) && incoming.has("testCases")) {
            merged.set("testCases", mergeTestCases(merged.get("testCases"), incoming.get("testCases")));
        }
        return merged;
    }

    /** Keeps server-side history per stable id while only accepting the writable fields. */
    private static com.fasterxml.jackson.databind.node.ArrayNode mergeTestCases(JsonNode existing, JsonNode incoming) {
        var history = new java.util.HashMap<String, JsonNode>();
        if (existing != null && existing.isArray()) {
            existing.forEach(entry -> { if (entry.isObject()) history.put(entry.path("id").asText(), entry); });
        }
        var next = JsonNodeFactory.instance.arrayNode();
        for (JsonNode entry : incoming) {
            JsonNode old = history.get(entry.path("id").asText());
            ObjectNode mergedEntry = old != null && old.isObject() ? (ObjectNode) old.deepCopy()
                    : JsonNodeFactory.instance.objectNode();
            mergedEntry.put("id", entry.path("id").asText());
            mergedEntry.put("name", entry.path("name").asText(""));
            mergedEntry.put("priority", entry.path("priority").asText(""));
            mergedEntry.put("expectedResult", entry.path("expectedResult").asText(""));
            next.add(mergedEntry);
        }
        return next;
    }

    public static void validate(JsonNode state, String variant) {
        Map<String, FieldSpec> specs = VARIANTS.get(variant);
        if (specs == null) throw new IllegalArgumentException("故事节点工作台类型无效");
        if (state == null || !state.isObject()) throw new IllegalArgumentException("故事节点工作台内容无效");
        for (Map.Entry<String, FieldSpec> entry : specs.entrySet()) {
            text(state.get(entry.getKey()), entry.getValue());
        }
        if ("writing".equals(variant)) {
            for (String key : java.util.List.of("title", "baseTitle")) text(state.get(key), FieldSpec.text(300));
            if (state.has("title") && (state.get("title").isNull() || state.path("title").asText().isBlank())) {
                throw new IllegalArgumentException("故事名称不能为空");
            }
            for (String key : java.util.List.of("topicId", "baseTopicId")) {
                text(state.get(key), FieldSpec.text(20));
                if (state.hasNonNull(key) && !state.path(key).asText().isEmpty()) {
                    try {
                        if (Long.parseLong(state.path(key).asText()) <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException exception) { throw new IllegalArgumentException("关联专题无效"); }
                }
            }
            if (state.has("priority") && !Set.of("LOW", "NORMAL", "HIGH", "URGENT").contains(state.path("priority").asText())) {
                throw new IllegalArgumentException("故事优先级无效");
            }
        }
        if ("acceptance".equals(variant)) {
            if (state.hasNonNull("acceptanceConclusion") && !state.path("acceptanceConclusion").asText().isEmpty()
                    && !Set.of("PASS", "CONDITIONAL", "FAIL").contains(state.path("acceptanceConclusion").asText())) {
                throw new IllegalArgumentException("验收结论无效");
            }
        }
        if ("development".equals(variant)) {
            String mergeStatus = state.path("mergeStatus").asText("");
            if (state.has("mergeStatus") && !Set.of("NOT_MERGED", "MERGED").contains(mergeStatus)) {
                throw new IllegalArgumentException("代码合并状态无效");
            }
            JsonNode cases = state.get("testCases");
            if (cases != null) {
                if (!cases.isArray() || cases.size() > 100) throw new IllegalArgumentException("测试用例最多记录100条");
                Set<String> ids = new java.util.HashSet<>();
                for (JsonNode entry : cases) {
                    if (!entry.isObject() || !entry.path("id").isTextual()
                            || entry.path("id").asText().isBlank() || entry.path("id").asText().length() > 100
                            || !validUuid(entry.path("id").asText())
                            || !ids.add(entry.path("id").asText())) {
                        throw new IllegalArgumentException("测试用例条目标识无效或重复");
                    }
                    text(entry.get("name"), FieldSpec.text(200));
                    text(entry.get("priority"), FieldSpec.text(10));
                    text(entry.get("expectedResult"), FieldSpec.text(2000));
                    if (!Set.of("LOW", "NORMAL", "HIGH", "URGENT").contains(entry.path("priority").asText()))
                        throw new IllegalArgumentException("测试用例优先级无效");
                }
            }
        }
        if ("iteration".equals(variant) || "release".equals(variant)) {
            String planId = state.path("iterationPlanId").asText("");
            if (!planId.isEmpty()) {
                try {
                    if (Long.parseLong(planId) <= 0) throw new NumberFormatException();
                } catch (NumberFormatException exception) { throw new IllegalArgumentException("关联迭代计划无效"); }
            }
            for (String key : java.util.List.of("developerIds", "testerIds")) {
                JsonNode ids = state.get(key);
                if (ids == null || ids.isNull()) continue;
                if (!ids.isArray()) throw new IllegalArgumentException("人员确认无效");
                for (JsonNode id : ids) {
                    if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() <= 0) {
                        throw new IllegalArgumentException("人员确认无效");
                    }
                }
            }
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

    private static boolean validUuid(String value) {
        try { return java.util.UUID.fromString(value).toString().equals(value); }
        catch (IllegalArgumentException exception) { return false; }
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
