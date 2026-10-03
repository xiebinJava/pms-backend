package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
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
                next.put("description", issue.path("description").asText(""));
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
        String reportUrl = state.path("reportUrl").asText("").trim();
        if (!reportUrl.isEmpty()) {
            try {
                URI uri = URI.create(reportUrl);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("请填写有效的 http 或 https 测试报告链接");
            }
        }
        JsonNode issues = state.get("residualIssues");
        if (issues == null || issues.isNull()) return;
        if (!issues.isArray() || issues.size() > 100) throw new IllegalArgumentException("遗留问题最多记录100条");
        Set<String> ids = new java.util.HashSet<>();
        for (JsonNode issue : issues) {
            if (!issue.isObject() || !issue.path("id").isTextual() || issue.path("id").asText().isBlank()
                    || issue.path("id").asText().length() > 100 || !ids.add(issue.path("id").asText())) {
                throw new IllegalArgumentException("遗留问题条目标识无效或重复");
            }
            text(issue.get("description"), 2000, "遗留问题说明");
        }
    }

    private static void text(JsonNode value, int max, String label) {
        if (value != null && !value.isNull() && (!value.isTextual() || value.asText().length() > max)) {
            throw new IllegalArgumentException(label + "格式不正确或超过长度限制");
        }
    }
}
