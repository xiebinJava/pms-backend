package com.brad.pms.ai.connector.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Projects every system-generated structured reference in AI read payloads through the
 * context READ policy. Inputs are never mutated; unreadable references are omitted whole
 * (no leftover ids/titles/counts), user-written body text is never rewritten.
 */
@Component
@RequiredArgsConstructor
public class AiContextReferenceProjector {

    /** Component state keys that represent system-resolved object references. */
    private static final List<String> COMPONENT_REFERENCE_KEYS =
            List.of("topicId", "baseTopicId", "projectId", "targetId", "navigationId", "iterationPlanId");
    private static final List<String> KNOWN_COMPONENTS = List.of(
            "requirement-receiving-analysis", "requirement-node-workbench", "topic-research",
            "topic-design-review", "story-node-workbench", "story-testing");

    private final AiContextReadPolicy policy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> projectResourceDetails(String resourceType, Long resourceId,
                                                      Map<String, Object> details) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "executionTarget" -> {
                    Map<String, Object> target = asMap(value);
                    if (target == null || !executionTargetReadable(target)) continue;
                    out.put(key, value);
                }
                case "sourceRequirements" -> out.put(key, filterRequirementList(value));
                case "sourceRequirement" -> {
                    Map<String, Object> source = asMap(value);
                    if (source == null || !policy.canReadReference("requirement", idValue(source.get("id")))) {
                        continue;
                    }
                    out.put(key, value);
                }
                default -> out.put(key, value);
            }
        }
        return out;
    }

    public Map<String, Object> projectFieldValues(String resourceType, Long resourceId,
                                                  Map<String, Object> fieldValues) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
            Object value = entry.getValue();
            if ("__components".equals(entry.getKey())) {
                out.put(entry.getKey(), projectComponents(value));
            } else {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    public JsonNode projectComponentContent(String resourceType, Long resourceId, String componentKey,
                                            JsonNode content) {
        if (componentKey == null || !KNOWN_COMPONENTS.contains(componentKey)) return null;
        if (content == null) return null;
        ObjectNode clone = (ObjectNode) objectMapper.valueToTree(content);
        List<String> removed = new ArrayList<>();
        for (String referenceKey : COMPONENT_REFERENCE_KEYS) {
            JsonNode reference = clone.get(referenceKey);
            if (reference == null || reference.isNull()) continue;
            Long referenceId = idValue(reference);
            if (referenceId == null) continue;
            if (!policy.canReadReference(componentReferenceType(componentKey, referenceKey), referenceId)) {
                clone.remove(referenceKey);
                removed.add(referenceKey);
            }
        }
        return clone;
    }

    private Object projectComponents(Object componentsValue) {
        Map<String, Object> components = asMap(componentsValue);
        if (components == null) return componentsValue;
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : components.entrySet()) {
            String componentKey = entry.getKey();
            if (!KNOWN_COMPONENTS.contains(componentKey)) continue;
            Map<String, Object> state = asMap(entry.getValue());
            if (state == null) {
                out.put(componentKey, entry.getValue());
                continue;
            }
            Map<String, Object> copy = new java.util.LinkedHashMap<>(state);
            for (String referenceKey : COMPONENT_REFERENCE_KEYS) {
                Long referenceId = idValue(copy.get(referenceKey));
                if (referenceId == null) continue;
                if (!policy.canReadReference(componentReferenceType(componentKey, referenceKey), referenceId)) {
                    copy.remove(referenceKey);
                }
            }
            out.put(componentKey, copy);
        }
        return out;
    }

    /** Component schemas decide reference types; unrelated ids in user text are never guessed. */
    private String componentReferenceType(String componentKey, String referenceKey) {
        return switch (referenceKey) {
            case "topicId", "baseTopicId" -> "topic";
            case "projectId" -> "project";
            case "targetId", "navigationId" -> "project";
            case "iterationPlanId" -> "iteration_plan";
            default -> null;
        };
    }

    private Object filterRequirementList(Object value) {
        if (!(value instanceof List<?> items)) return value;
        List<Object> out = new ArrayList<>();
        for (Object item : items) {
            Map<String, Object> map = asMap(item);
            if (map != null && !policy.canReadReference("requirement", idValue(map.get("id")))) continue;
            out.add(item);
        }
        return out;
    }

    private boolean executionTargetReadable(Map<String, Object> target) {
        Object targetType = target.get("targetType");
        Object targetId = target.get("targetId");
        if (targetType == null || targetId == null) return false;
        String normalized = targetType.toString().trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return policy.canReadReference(normalized, idValue(targetId));
    }

    private Long idValue(Object value) {
        if (value == null) return null;
        if (value instanceof com.fasterxml.jackson.databind.JsonNode node) {
            if (node.isNumber()) return node.longValue();
            if (node.isTextual()) return parseId(node.textValue());
            return null;
        }
        if (value instanceof Long longValue) return longValue;
        if (value instanceof Integer integer) return integer.longValue();
        if (value instanceof Number number) return number.longValue();
        return parseId(value.toString());
    }

    private Long parseId(String value) {
        String text = value == null ? null : value.trim();
        if (text == null || !text.matches("\\d+")) return null;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException outsideRange) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return null;
    }
}
