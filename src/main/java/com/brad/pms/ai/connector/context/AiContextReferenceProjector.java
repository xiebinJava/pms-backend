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
    /** Project workbench keys + research components; unknown keys inside legacy JSON are dropped. */
    private static final List<String> KNOWN_COMPONENTS = List.of(
            "requirement-receiving-analysis", "requirement-node-workbench", "topic-research",
            "topic-design-review", "story-node-workbench", "story-testing",
            "project-basic-info", "requirement-scope", "solution-design", "plan-resource-risk",
            "development-control", "business-acceptance", "release-handover", "value-review",
            "knowledge-standard");

    private final AiContextReadPolicy policy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> projectResourceDetails(String resourceType, Long resourceId,
                                                      Map<String, Object> details) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Map<String, Boolean> groupOk = parentGroupReadability(resourceType, details);
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "executionTarget" -> {
                    // DTO and Map payloads are both legitimate; unreadable targets are dropped whole.
                    Long targetId = executionTargetId(value);
                    String targetType = executionTargetType(value);
                    if (targetId == null || targetType == null
                            || !policy.canReadReference(targetType, targetId)) {
                        continue;
                    }
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
                default -> {
                    // Parent references carried by topic/story details form an id+label key group
                    // (projectId/projectCode/projectName...); whole group shares one verdict.
                    String group = parentGroupIdKey(resourceType, key);
                    if (group == null) group = parentLabelKey(resourceType, key);
                    if (group == null) {
                        out.put(key, value);
                        continue;
                    }
                    if (!Boolean.TRUE.equals(groupOk.get(group))) continue;
                    out.put(key, value);
                }
            }
        }
        return out;
    }

    /**
     * Evaluates each parent-reference group once per call by locating its id key
     * (projectId/topicId/iterationPlanId) in the details and checking module READ + existence.
     */
    private Map<String, Boolean> parentGroupReadability(String resourceType, Map<String, Object> details) {
        Map<String, Boolean> groupOk = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = entry.getKey();
            String group = parentGroupIdKey(resourceType, key);
            if (group == null || groupOk.containsKey(group)) continue;
            Long id = idValue(entry.getValue());
            groupOk.put(group, id != null && policy.canReadReference(group, id));
        }
        return groupOk;
    }


    /** Id keys group by themselves; label keys follow their group's verdict. */
    private String parentLabelKey(String resourceType, String key) {
        if (!"topic".equals(resourceType) && !"story".equals(resourceType)) return null;
        return switch (key) {
            case "projectCode", "projectName" -> "project";
            case "topicTitle" -> "topic";
            case "iterationPlanName" -> "iteration_plan";
            default -> null;
        };
    }

    private String parentGroupIdKey(String resourceType, String key) {
        if (!"topic".equals(resourceType) && !"story".equals(resourceType)) return null;
        return switch (key) {
            case "projectId" -> "project";
            case "topicId" -> "topic";
            case "iterationPlanId" -> "iteration_plan";
            default -> null;
        };
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
        // Actual AI payloads carry `__components` as a Jackson ObjectNode (Map<String,JsonNode>
        // in the DTO), so both real representations are filtered here.
        if (componentsValue instanceof com.fasterxml.jackson.databind.JsonNode node) {
            return projectComponentsNode(node);
        }
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
            stripComponentReferences(componentKey, copy);
            out.put(componentKey, copy);
        }
        return out;
    }

    private com.fasterxml.jackson.databind.JsonNode projectComponentsNode(
            com.fasterxml.jackson.databind.JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            ObjectNode out = objectMapper.createObjectNode();
            var fieldIterator = objectNode.fields();
            while (fieldIterator.hasNext()) {
                var field = fieldIterator.next();
                String componentKey = field.getKey();
                if (!KNOWN_COMPONENTS.contains(componentKey)) continue;
                if (field.getValue() instanceof ObjectNode state) {
                    ObjectNode copy = state.deepCopy();
                    stripComponentReferencesAsNodes(componentKey, copy);
                    out.set(componentKey, copy);
                } else {
                    out.set(componentKey, field.getValue().deepCopy());
                }
            }
            return out;
        }
        return node.deepCopy();
    }

    private void stripComponentReferences(String componentKey, Map<String, Object> copy) {
        for (String referenceKey : COMPONENT_REFERENCE_KEYS) {
            Long referenceId = idValue(copy.get(referenceKey));
            if (referenceId == null) continue;
            if (!policy.canReadReference(componentReferenceType(componentKey, referenceKey), referenceId)) {
                copy.remove(referenceKey);
            }
        }
    }

    private void stripComponentReferencesAsNodes(String componentKey, ObjectNode state) {
        for (String referenceKey : COMPONENT_REFERENCE_KEYS) {
            Long referenceId = idValue(state.get(referenceKey));
            if (referenceId == null) continue;
            if (!policy.canReadReference(componentReferenceType(componentKey, referenceKey), referenceId)) {
                state.remove(referenceKey);
            }
        }
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


    private Long executionTargetId(Object value) {
        if (value instanceof com.brad.pms.dto.response.RequirementExecutionTargetDTO target) {
            return target.getTargetId();
        }
        Map<String, Object> map = asMap(value);
        return map == null ? null : idValue(map.get("targetId"));
    }

    private String executionTargetType(Object value) {
        if (value instanceof com.brad.pms.dto.response.RequirementExecutionTargetDTO target) {
            return target.getTargetType() == null
                    ? null : target.getTargetType().name().toLowerCase(Locale.ROOT).replace('-', '_');
        }
        Map<String, Object> map = asMap(value);
        if (map == null || map.get("targetType") == null) return null;
        return map.get("targetType").toString().trim().toLowerCase(Locale.ROOT).replace('-', '_');
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
