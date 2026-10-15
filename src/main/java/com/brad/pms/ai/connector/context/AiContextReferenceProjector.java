package com.brad.pms.ai.connector.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Projects every system-generated structured reference in AI read payloads through the
 * context READ policy. Inputs are never mutated; unreadable references are omitted whole
 * (no leftover ids/titles/counts), user-written body text is never rewritten.
 */
@Component
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
    private final ReadAuthorizationSnapshot snapshot;

    @Autowired
    public AiContextReferenceProjector(AiContextReadPolicy policy) {
        this(policy, null);
    }

    private AiContextReferenceProjector(AiContextReadPolicy policy, ReadAuthorizationSnapshot snapshot) {
        this.policy = policy;
        this.snapshot = snapshot;
    }

    public AiContextReferenceProjector scoped(ReadAuthorizationSnapshot authorizationSnapshot) {
        return new AiContextReferenceProjector(policy, authorizationSnapshot);
    }

    private boolean canReadReference(String resourceType, Long resourceId) {
        return snapshot == null
                ? policy.canReadReference(resourceType, resourceId)
                : snapshot.canRead(resourceType, resourceId);
    }

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
                            || !canReadReference(targetType, targetId)) {
                        continue;
                    }
                    out.put(key, value);
                }
                case "sourceRequirements" -> out.put(key, filterRequirementList(value));
                case "sourceRequirement" -> {
                    Map<String, Object> source = asMap(value);
                    if (source == null || !canReadReference("requirement", idValue(source.get("id")))) {
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
            groupOk.put(group, id != null && canReadReference(group, id));
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
        if (!content.isObject()) return content.deepCopy();
        ObjectNode clone = content.deepCopy();
        for (String referenceKey : COMPONENT_REFERENCE_KEYS) {
            JsonNode reference = clone.get(referenceKey);
            if (reference == null || reference.isNull()) continue;
            Long referenceId = idValue(reference);
            if (referenceId == null) continue;
            String referenceType = componentReferenceType(componentKey, referenceKey, clone);
            if (referenceType == null || !canReadReference(referenceType, referenceId)) {
                clone.remove(referenceKey);
                removeTargetTypeWhenTargetIsDropped(componentKey, referenceKey, clone);
            }
        }
        return clone;
    }

    /** Projects the DTO-backed project workbench snapshots, including nested references. */
    public JsonNode projectProjectComponentContent(String componentKey, JsonNode content) {
        JsonNode projected = projectComponentContent("project", null, componentKey, content);
        if (!(projected instanceof ObjectNode objectNode)) return projected;
        switch (componentKey) {
            case "plan-resource-risk" -> filterArrayByReference(objectNode, "iterationPlans", "iteration_plan");
            case "development-control" -> projectDevelopmentControl(objectNode);
            default -> { }
        }
        return objectNode;
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
            JsonNode rawState = objectMapper.valueToTree(entry.getValue());
            if (!rawState.isObject()) {
                out.put(componentKey, entry.getValue());
                continue;
            }
            JsonNode projected = projectProjectComponentContent(componentKey, rawState);
            out.put(componentKey, objectMapper.convertValue(projected, Object.class));
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
                    out.set(componentKey, projectProjectComponentContent(componentKey, copy));
                } else {
                    out.set(componentKey, field.getValue().deepCopy());
                }
            }
            return out;
        }
        return node.deepCopy();
    }

    /** Component schemas decide reference types; unrelated ids in user text are never guessed. */
    private String componentReferenceType(String componentKey, String referenceKey, JsonNode state) {
        return switch (referenceKey) {
            case "topicId", "baseTopicId" -> "topic";
            case "projectId" -> "project";
            case "targetId", "navigationId" -> {
                if (!"requirement-node-workbench".equals(componentKey)) yield "project";
                String typeKey = "navigationId".equals(referenceKey) ? "navigationType" : "targetType";
                String typeValue = state == null ? null : state.path(typeKey).asText(null);
                if ((typeValue == null || typeValue.isBlank()) && "navigationId".equals(referenceKey)) {
                    // Older persisted workbench states only stored targetType while still
                    // carrying navigationId; keep the navigation reference projected by that type.
                    typeValue = state.path("targetType").asText(null);
                }
                yield normalizeReferenceType(typeValue);
            }
            case "iterationPlanId" -> "iteration_plan";
            default -> null;
        };
    }

    private String normalizeReferenceType(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return Set.of("project", "requirement", "topic", "story", "iteration_plan").contains(normalized)
                ? normalized : null;
    }

    private void removeTargetTypeWhenTargetIsDropped(String componentKey, String referenceKey,
                                                     ObjectNode state) {
        if ("requirement-node-workbench".equals(componentKey)
                && ("targetId".equals(referenceKey) || "navigationId".equals(referenceKey))) {
            state.remove("targetType");
            state.remove("navigationType");
        }
    }

    private void filterArrayByReference(ObjectNode parent, String arrayKey, String referenceType) {
        JsonNode value = parent.get(arrayKey);
        if (!(value instanceof ArrayNode array)) return;
        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode item : array) {
            Long id = item == null ? null : idValue(item.get("id"));
            if (id != null && !canReadReference(referenceType, id)) continue;
            filtered.add(item.deepCopy());
        }
        parent.set(arrayKey, filtered);
    }

    private void projectDevelopmentControl(ObjectNode root) {
        JsonNode value = root.get("topics");
        if (!(value instanceof ArrayNode topics)) return;
        ArrayNode filteredTopics = objectMapper.createArrayNode();
        for (JsonNode topic : topics) {
            if (!(topic instanceof ObjectNode topicObject)) {
                filteredTopics.add(topic.deepCopy());
                continue;
            }
            Long topicId = idValue(topicObject.get("id"));
            if (topicId != null && !canReadReference("topic", topicId)) continue;
            JsonNode storyValue = topicObject.get("stories");
            if (storyValue instanceof ArrayNode stories) {
                ArrayNode filteredStories = objectMapper.createArrayNode();
                for (JsonNode story : stories) {
                    if (!(story instanceof ObjectNode storyObject)) {
                        filteredStories.add(story.deepCopy());
                        continue;
                    }
                    Long storyId = idValue(storyObject.get("id"));
                    if (storyId != null && !canReadReference("story", storyId)) continue;
                    projectIterationPlanReference(storyObject);
                    filteredStories.add(storyObject);
                }
                topicObject.set("stories", filteredStories);
            }
            filteredTopics.add(topicObject);
        }
        root.set("topics", filteredTopics);
        recomputeDevelopmentSummary(root, filteredTopics);
    }

    private void projectIterationPlanReference(ObjectNode story) {
        Long iterationPlanId = idValue(story.get("iterationPlanId"));
        if (iterationPlanId != null && !canReadReference("iteration_plan", iterationPlanId)) {
            story.remove("iterationPlanId");
            story.remove("iterationPlanName");
        }
    }

    private void recomputeDevelopmentSummary(ObjectNode root, ArrayNode topics) {
        JsonNode summaryValue = root.get("summary");
        if (!(summaryValue instanceof ObjectNode summary)) return;
        List<JsonNode> stories = new ArrayList<>();
        for (JsonNode topic : topics) {
            JsonNode storyValue = topic.get("stories");
            if (storyValue instanceof ArrayNode storyArray) storyArray.forEach(stories::add);
        }
        summary.put("topicCount", topics.size());
        summary.put("storyCount", stories.size());
        summary.put("completedStoryCount", (int) stories.stream()
                .filter(story -> "DONE".equals(story.path("status").asText())).count());
        summary.put("blockedStoryCount", (int) stories.stream()
                .filter(story -> "BLOCKED".equals(story.path("status").asText())).count());
        if (stories.isEmpty()) {
            summary.put("progress", 0);
        } else {
            double average = stories.stream().mapToInt(this::effectiveProgress).average().orElse(0);
            summary.put("progress", (int) Math.round(average));
        }
    }

    private int effectiveProgress(JsonNode story) {
        String status = story.path("status").asText(null);
        if ("DONE".equals(status)) return 100;
        if ("NOT_STARTED".equals(status)) return 0;
        return Math.max(0, Math.min(100, story.path("progress").asInt(0)));
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
            if (map != null && !canReadReference("requirement", idValue(map.get("id")))) continue;
            out.add(item);
        }
        return out;
    }

    private boolean executionTargetReadable(Map<String, Object> target) {
        Object targetType = target.get("targetType");
        Object targetId = target.get("targetId");
        if (targetType == null || targetId == null) return false;
        String normalized = targetType.toString().trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return canReadReference(normalized, idValue(targetId));
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
