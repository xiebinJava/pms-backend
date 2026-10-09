package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/** Validates optional testing records without introducing a new node-completion gate. */
public final class TopicDevelopmentTestingPolicy {
    private TopicDevelopmentTestingPolicy() { }

    /** Only supported fields are writable; historical metadata comes from the server, not the request. */
    public static ObjectNode merge(JsonNode existing, JsonNode incoming) {
        validate(incoming);
        ObjectNode merged = existing != null && existing.isObject()
                ? (ObjectNode) existing.deepCopy() : JsonNodeFactory.instance.objectNode();
        for (String key : java.util.List.of("buildVersion", "testStatus", "reportUrl")) {
            if (incoming.has(key)) merged.set(key, incoming.get(key).deepCopy());
        }
        if (incoming.has("residualIssues")) {
            var history = new java.util.HashMap<String, JsonNode>();
            JsonNode oldIssues = merged.get("residualIssues");
            if (oldIssues != null && oldIssues.isArray()) {
                oldIssues.forEach(issue -> { if (issue.isObject()) history.put(issue.path("id").asText(), issue); });
            }
            var nextIssues = JsonNodeFactory.instance.arrayNode();
            JsonNode issues = incoming.get("residualIssues");
            if (issues != null && issues.isArray()) for (JsonNode issue : issues) {
                JsonNode old = history.get(issue.path("id").asText());
                ObjectNode next = old != null ? (ObjectNode) old.deepCopy() : JsonNodeFactory.instance.objectNode();
                next.put("id", issue.path("id").asText());
                for (String key : java.util.List.of("name", "type", "level", "description", "expectedResult", "owner")) {
                    if (issue.has(key)) next.set(key, issue.get(key).deepCopy());
                }
                nextIssues.add(next);
            }
            merged.set("residualIssues", nextIssues);
        }
        return merged;
    }

    public static void validate(JsonNode state) {
        text(state.get("buildVersion"), 200, "构建版本");
        text(state.get("reportUrl"), 4000, "测试报告链接");
        JsonNode status = state.get("testStatus");
        if (status != null && !status.isNull() && (!status.isTextual()
                || !Set.of("NOT_STARTED", "IN_PROGRESS", "PASSED", "FAILED").contains(status.asText()))) {
            throw new IllegalArgumentException("请选择有效的测试状态");
        }
        JsonNode issues = state.get("residualIssues");
        if (issues == null || issues.isNull()) return;
        if (!issues.isArray() || issues.size() > 100) throw new IllegalArgumentException("遗留问题最多记录100条");
        Set<String> ids = new java.util.HashSet<>();
        Set<String> defectTypes = Set.of("RND", "UI", "PRODUCT");
        Set<String> defectLevels = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
        for (JsonNode issue : issues) {
            if (!issue.isObject() || !issue.path("id").isTextual() || issue.path("id").asText().isBlank()
                    || issue.path("id").asText().length() > 100 || !ids.add(issue.path("id").asText())) {
                throw new IllegalArgumentException("缺陷条目标识无效或重复");
            }
            text(issue.get("name"), 200, "缺陷名称");
            text(issue.get("description"), 2000, "缺陷描述");
            text(issue.get("expectedResult"), 2000, "缺陷预期结果");
            enumText(issue.get("type"), defectTypes, "缺陷类型");
            enumText(issue.get("level"), defectLevels, "缺陷等级");
            owner(issue.get("owner"));
        }
    }

    private static void enumText(JsonNode value, Set<String> allowed, String label) {
        if (value == null || value.isNull()) return;
        if (!value.isTextual() || (!value.asText().isEmpty() && !allowed.contains(value.asText()))) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
    }

    private static void owner(JsonNode value) {
        if (value == null || value.isNull()) return;
        if (value.isIntegralNumber() && value.canConvertToInt() && value.asInt() > 0) return;
        if (value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 100) return;
        throw new IllegalArgumentException("缺陷对接人格式不正确");
    }

    private static void text(JsonNode value, int max, String label) {
        if (value != null && !value.isNull() && (!value.isTextual() || value.asText().length() > max)) {
            throw new IllegalArgumentException(label + "格式不正确或超过长度限制");
        }
    }
}
