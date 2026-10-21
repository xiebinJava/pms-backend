package com.brad.pms.ai.orchestrator.service;

import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.connector.AiConnectorPort;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.persistence.AiPlanDO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiResourceDetailDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;

@Service
@RequiredArgsConstructor
public class AiOrchestratorVerificationService {

    private static final String STORY_NODE_WORKBENCH = "story-node-workbench";

    private final AiConnectorPort connectorPort;
    private final ObjectMapper objectMapper;

    public AiOrchestratorResponse.VerificationView verify(AiPlanDO plan,
                                                           AgentDraft draft,
                                                           AiOperationResultDTO operation) {
        try {
            AiWorkflowContextDTO before = objectMapper.readValue(
                    plan.getContextSnapshotJson(), AiWorkflowContextDTO.class);
            AiResourceDetailDTO resource = connectorPort.readResource(
                    plan.getResourceType(), plan.getResourceId());
            AiWorkflowContextDTO after = connectorPort.readContext(
                    plan.getResourceType(), plan.getResourceId(),
                    new ContextReadOptions(ContextReadOptions.VIEW_FOCUSED, plan.getNodeId(),
                            focusedComponentKey(plan)));

            boolean resourceReadback = resource != null
                    && Objects.equals(resource.resourceType(), plan.getResourceType())
                    && Objects.equals(resource.resourceId(), plan.getResourceId());
            boolean contextReadback = after != null
                    && Objects.equals(after.resourceType(), plan.getResourceType())
                    && Objects.equals(after.resourceId(), plan.getResourceId())
                    && resourceContextMatches(plan, after);
            Set<String> allowedMutations = allowedMutations(operation);
            boolean protectedFieldsUnchanged = contextReadback
                    && storyContextMatches(plan, after)
                    && unchangedWorkflowState(plan, before, after, allowedMutations)
                    && storyProtectedFieldsUnchanged(plan, before, after, allowedMutations)
                    && resourceProtectedFieldsUnchanged(plan, before, after, allowedMutations)
                    && versionDidNotMoveBack(plan, after);
            Map<String, Object> resourceDetails = resource == null ? null : resource.details();
            boolean patchApplied = resourceReadback && contextReadback
                    && "SUCCEEDED".equals(operation == null ? null : operation.status())
                    && patchInReadback(plan, after, resourceDetails, draft.draft().patch());

            if (!resourceReadback || !contextReadback) {
                return new AiOrchestratorResponse.VerificationView(resourceReadback, contextReadback,
                        protectedFieldsUnchanged, patchApplied, "WRITE_RESULT_UNKNOWN",
                        "业务写入结果无法通过回读确认");
            }
            if (!protectedFieldsUnchanged || !patchApplied) {
                return new AiOrchestratorResponse.VerificationView(resourceReadback, contextReadback,
                        protectedFieldsUnchanged, patchApplied, "WRITE_RESULT_UNKNOWN",
                        "回读结果无法证明分析已按预期写入");
            }
            return new AiOrchestratorResponse.VerificationView(true, true, true, true,
                    "SUCCEEDED", "写入并回读验证成功");
        } catch (Exception exception) {
            return new AiOrchestratorResponse.VerificationView(false, false, false, false,
                    "WRITE_RESULT_UNKNOWN", "业务写入结果无法通过回读确认");
        }
    }

    private String focusedComponentKey(AiPlanDO plan) {
        if ("story".equalsIgnoreCase(plan.getResourceType())) return STORY_NODE_WORKBENCH;
        if (isResourceNodePlan(plan)) return plan.getComponentKey();
        return null;
    }

    private boolean versionDidNotMoveBack(AiPlanDO plan, AiWorkflowContextDTO after) {
        if ("story".equalsIgnoreCase(plan.getResourceType())) {
            AiWorkflowContextDTO.Node node = after == null || after.workflow() == null ? null
                    : after.workflow().nodes().stream()
                    .filter(candidate -> candidate != null && Objects.equals(candidate.id(), plan.getNodeId()))
                    .findFirst().orElse(null);
            return after != null && after.version() != null && node != null && node.version() != null
                    && Objects.equals(after.version(), node.version())
                    && plan.getContextVersion() != null && after.version() >= plan.getContextVersion();
        }
        if (after == null || after.version() == null || plan.getContextVersion() == null
                || after.version() < plan.getContextVersion()) return false;
        if (isResourceNodePlan(plan)) {
            AiWorkflowContextDTO.Node node = findNode(after, plan.getNodeId());
            return node != null && node.version() != null && plan.getNodeVersion() != null
                    && node.version() >= plan.getNodeVersion();
        }
        return true;
    }

    private boolean unchangedWorkflowState(AiPlanDO plan, AiWorkflowContextDTO before,
                                           AiWorkflowContextDTO after, Set<String> allowedMutations) {
        if (before == null || after == null) return false;
        if (isResourceNodePlan(plan)) {
            AiWorkflowContextDTO.Node beforeNode = findNode(before, plan.getNodeId());
            AiWorkflowContextDTO.Node afterNode = findNode(after, plan.getNodeId());
            return beforeNode != null && afterNode != null
                    && protectedDetailsEqual(before.resourceDetails(), after.resourceDetails(), allowedMutations)
                    && nodeProtectedFieldsEqual(beforeNode, afterNode, allowedMutations);
        }
        if (!Objects.equals(nodeId(before), nodeId(after))
                || !Objects.equals(nodeKey(before), nodeKey(after))) {
            return false;
        }
        if (!protectedDetailsEqual(before.resourceDetails(), after.resourceDetails(), allowedMutations)) return false;
        AiWorkflowContextDTO.Node beforeNode = selectedNode(before);
        AiWorkflowContextDTO.Node afterNode = selectedNode(after);
        return beforeNode != null && afterNode != null
                && nodeProtectedFieldsEqual(beforeNode, afterNode, allowedMutations);
    }

    private Long nodeId(AiWorkflowContextDTO context) {
        return context.currentNode() == null ? null : context.currentNode().id();
    }

    private String nodeKey(AiWorkflowContextDTO context) {
        return context.currentNode() == null ? null : context.currentNode().key();
    }

    private AiWorkflowContextDTO.Node selectedNode(AiWorkflowContextDTO context) {
        if (context == null || context.workflow() == null || context.currentNode() == null) return null;
        return context.workflow().nodes().stream()
                .filter(node -> node != null && Objects.equals(node.id(), context.currentNode().id()))
                .findFirst().orElse(null);
    }

    private boolean protectedDetailsEqual(Map<String, Object> before, Map<String, Object> after,
                                          Set<String> allowedMutations) {
        if (before == null || after == null) return false;
        for (String key : new String[]{"systemId", "targetType", "targetId", "title", "baseTitle",
                "topicId", "baseTopicId", "ownerId", "status", "developmentStatus", "startDate",
                "endDate", "dueDate"}) {
            if (allowedMutations.contains(key)) continue;
            // A focused project/topic projection can materialize fields that
            // were absent (or null) in the plan snapshot after the first
            // workbench write. Only compare values that were observable
            // before execution.
            if (!before.containsKey(key) || before.get(key) == null) continue;
            if (!valuesEquivalent(before.get(key), after.get(key))) return false;
        }
        return true;
    }

    private boolean nodeProtectedFieldsEqual(AiWorkflowContextDTO.Node before,
                                             AiWorkflowContextDTO.Node after,
                                             Set<String> allowedMutations) {
        if (before == null || after == null) return false;
        if (!allowedMutations.contains("status") && !Objects.equals(before.status(), after.status())) return false;
        if (!allowedMutations.contains("ownerId") && !Objects.equals(before.ownerId(), after.ownerId())) return false;
        if (!allowedMutations.contains("startDate") && !Objects.equals(before.startDate(), after.startDate())) return false;
        if (!allowedMutations.contains("endDate") && !Objects.equals(before.endDate(), after.endDate())) return false;
        return true;
    }

    private boolean storyContextMatches(AiPlanDO plan, AiWorkflowContextDTO context) {
        if (!"story".equalsIgnoreCase(plan.getResourceType())) return true;
        if (context.currentNode() == null || !Objects.equals(context.currentNode().id(), plan.getNodeId())) {
            return false;
        }
        if (context.workflow() == null || context.workflow().nodes() == null) return false;
        AiWorkflowContextDTO.Node node = context.workflow().nodes().stream()
                .filter(candidate -> candidate != null && Objects.equals(candidate.id(), plan.getNodeId()))
                .findFirst().orElse(null);
        if (node == null || (plan.getContextVersion() != null
                && (node.version() == null || node.version() < plan.getContextVersion()))) return false;
        AiWorkflowContextDTO.Component component = node.components().stream()
                .filter(value -> value != null && STORY_NODE_WORKBENCH.equals(value.key()))
                .findFirst().orElse(null);
        if (component == null) return false;
        if (component.versions() == null || !Objects.equals(component.versions().get("node"), node.version())) {
            return false;
        }
        Object contractVersion = component.saveContract() == null
                ? null : component.saveContract().get("version");
        Long parsedContractVersion = numericVersion(contractVersion);
        return parsedContractVersion != null && node.version() != null
                && parsedContractVersion == node.version().longValue();
    }

    private Long numericVersion(Object version) {
        if (version instanceof Number number) return number.longValue();
        if (version instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean storyProtectedFieldsUnchanged(AiPlanDO plan,
                                                  AiWorkflowContextDTO before,
                                                  AiWorkflowContextDTO after,
                                                  Set<String> allowedMutations) {
        if (!"story".equalsIgnoreCase(plan.getResourceType())) return true;
        AiWorkflowContextDTO.Component beforeComponent = storyComponent(before, plan.getNodeId());
        AiWorkflowContextDTO.Component afterComponent = storyComponent(after, plan.getNodeId());
        if (beforeComponent == null || afterComponent == null) return false;
        Object rawProtected = beforeComponent.saveContract() == null
                ? null : beforeComponent.saveContract().get("protectedFields");
        if (!(rawProtected instanceof List<?> protectedFields)) return false;
        for (Object field : protectedFields) {
            if (!(field instanceof String key) || key.isBlank()) continue;
            if (allowedMutations.contains(key)) continue;
            JsonNode beforeValue = value(beforeComponent.content(), key);
            JsonNode afterValue = value(afterComponent.content(), key);
            if (!valuesEquivalent(beforeValue, afterValue)) return false;
        }
        return true;
    }

    private boolean valuesEquivalent(Object before, Object after) {
        if (before instanceof Number left && after instanceof Number right) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString())) == 0;
        }
        if ((before instanceof CharSequence || before instanceof TemporalAccessor)
                && (after instanceof CharSequence || after instanceof TemporalAccessor)) {
            return String.valueOf(before).contentEquals(String.valueOf(after));
        }
        return Objects.equals(before, after);
    }

    private boolean resourceProtectedFieldsUnchanged(AiPlanDO plan,
                                                     AiWorkflowContextDTO before,
                                                     AiWorkflowContextDTO after,
                                                     Set<String> allowedMutations) {
        if (!isResourceNodePlan(plan)) return true;
        AiWorkflowContextDTO.Component beforeComponent = resourceComponent(before, plan);
        AiWorkflowContextDTO.Component afterComponent = resourceComponent(after, plan);
        if (beforeComponent == null || afterComponent == null) return false;
        // Only compare protected values that were observable in the plan's
        // before snapshot. Project/topic focused projections may materialize
        // identifiers (projectId/nodeId/workbench) after the first write;
        // treating those after-only values as a mutation would turn a valid
        // write into WRITE_RESULT_UNKNOWN.
        List<String> protectedFields = resourceProtectedFields(beforeComponent, beforeComponent);
        for (String field : protectedFields) {
            if (allowedMutations.contains(field)) continue;
            Object beforeValue = resourceField(before, beforeComponent, plan.getNodeId(), field);
            Object afterValue = resourceField(after, afterComponent, plan.getNodeId(), field);
            if (beforeValue == null) continue;
            if (!Objects.equals(beforeValue, afterValue)) return false;
        }
        return true;
    }

    private List<String> resourceProtectedFields(AiWorkflowContextDTO.Component before,
                                                  AiWorkflowContextDTO.Component after) {
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        for (AiWorkflowContextDTO.Component component : List.of(before, after)) {
            if (component.saveContract() == null) continue;
            Object raw = component.saveContract().get("protectedFields");
            if (raw instanceof List<?> list) {
                list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(fields::add);
            }
        }
        return List.copyOf(fields);
    }

    private boolean patchInReadback(AiPlanDO plan,
                                    AiWorkflowContextDTO after,
                                    Map<String, Object> resourceDetails,
                                    Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) return false;
        if ("story".equalsIgnoreCase(plan.getResourceType())) {
            AiWorkflowContextDTO.Component component = storyComponent(after, plan.getNodeId());
            return component != null && containsPatch(component.content(), objectMapper.valueToTree(patch));
        }
        if (isResourceNodePlan(plan)) {
            AiWorkflowContextDTO.Component component = resourceComponent(after, plan);
            return component != null && containsPatch(componentContent(after, component, plan.getNodeId()), patch);
        }
        return containsPatch(resourceDetails, patch) || containsPatchInWorkflowComponents(after, patch);
    }

    private boolean resourceContextMatches(AiPlanDO plan, AiWorkflowContextDTO context) {
        if (!isResourceNodePlan(plan)) return true;
        AiWorkflowContextDTO.Node node = findNode(context, plan.getNodeId());
        if (node == null || node.version() == null || plan.getNodeVersion() == null
                || node.version() < plan.getNodeVersion()) return false;
        if (!Objects.equals(plan.getIntent(), "RESOURCE_NODE_COMPLETE")
                && (context.currentNode() == null || !Objects.equals(context.currentNode().id(), plan.getNodeId()))) {
            return false;
        }
        return resourceComponent(context, plan) != null;
    }

    private boolean isResourceNodePlan(AiPlanDO plan) {
        return plan != null && ("project".equalsIgnoreCase(plan.getResourceType())
                || "topic".equalsIgnoreCase(plan.getResourceType()));
    }

    private AiWorkflowContextDTO.Node findNode(AiWorkflowContextDTO context, Long nodeId) {
        if (context == null || context.workflow() == null || nodeId == null) return null;
        return context.workflow().nodes().stream()
                .filter(node -> node != null && Objects.equals(node.id(), nodeId))
                .findFirst().orElse(null);
    }

    private AiWorkflowContextDTO.Component resourceComponent(AiWorkflowContextDTO context, AiPlanDO plan) {
        AiWorkflowContextDTO.Node node = findNode(context, plan.getNodeId());
        if (node == null || plan.getComponentKey() == null) return null;
        return node.components().stream()
                .filter(component -> component != null && Objects.equals(component.key(), plan.getComponentKey()))
                .findFirst().orElse(null);
    }

    private Map<String, Object> componentContent(AiWorkflowContextDTO context,
                                                 AiWorkflowContextDTO.Component component,
                                                 Long nodeId) {
        if (component.content() != null && component.content().isObject()) {
            return objectMapper.convertValue(component.content(), Map.class);
        }
        AiWorkflowContextDTO.Node node = findNode(context, nodeId);
        Object path = component.saveContract() == null ? null : component.saveContract().get("fieldValuesPath");
        if (node != null && path instanceof String text && node.fieldValues() != null) {
            Object current = node.fieldValues();
            for (String segment : text.split("\\.")) {
                if (!(current instanceof Map<?, ?> values)) return Map.of();
                current = values.get(segment);
            }
            if (current instanceof Map<?, ?> values) return objectMapper.convertValue(values, Map.class);
        }
        return Map.of();
    }

    private Object resourceField(AiWorkflowContextDTO context,
                                 AiWorkflowContextDTO.Component component,
                                 Long nodeId,
                                 String field) {
        if (context.resourceDetails() != null && context.resourceDetails().containsKey(field)) {
            return context.resourceDetails().get(field);
        }
        return componentContent(context, component, nodeId).get(field);
    }

    private Set<String> allowedMutations(AiOperationResultDTO operation) {
        if (operation == null || operation.data() == null) return Set.of();
        Object raw = operation.data().get("allowedMutations");
        if (raw instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).collect(java.util.stream.Collectors.toSet());
        }
        if (raw instanceof Map<?, ?> map) {
            return map.keySet().stream().filter(String.class::isInstance).map(String.class::cast)
                    .collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    private boolean containsPatchInWorkflowComponents(AiWorkflowContextDTO context,
                                                       Map<String, Object> patch) {
        if (context == null || context.workflow() == null || patch == null || patch.isEmpty()) return false;
        JsonNode patchNode = objectMapper.valueToTree(patch);
        return context.workflow().nodes().stream()
                .filter(node -> node != null)
                .flatMap(node -> node.components().stream())
                .filter(component -> component != null)
                .anyMatch(component -> containsPatch(component.content(), patchNode));
    }

    private AiWorkflowContextDTO.Component storyComponent(AiWorkflowContextDTO context, Long nodeId) {
        if (context == null || context.workflow() == null || nodeId == null) return null;
        return context.workflow().nodes().stream()
                .filter(node -> node != null && Objects.equals(node.id(), nodeId))
                .flatMap(node -> node.components().stream())
                .filter(component -> component != null && STORY_NODE_WORKBENCH.equals(component.key()))
                .findFirst().orElse(null);
    }

    private JsonNode value(JsonNode content, String key) {
        return content == null ? null : content.get(key);
    }

    private boolean containsPatch(Map<String, Object> container, Map<String, Object> patch) {
        if (container == null || patch == null || patch.isEmpty()) return false;
        JsonNode containerNode = objectMapper.valueToTree(container);
        JsonNode patchNode = objectMapper.valueToTree(patch);
        return containsPatch(containerNode, patchNode);
    }

    private boolean containsPatch(JsonNode container, JsonNode patch) {
        if (container == null || patch == null) return false;
        if (containsExpectedShape(container, patch)) return true;
        if (container.isContainerNode()) {
            for (JsonNode child : container) {
                if (containsPatch(child, patch)) return true;
            }
        }
        return false;
    }

    /**
     * Compare the agent's expected patch with the persisted projection while
     * allowing PMS to add server-owned metadata to structured values (for
     * example ids on scope items). Object fields are a subset match; arrays
     * retain their length and order so a partial or reordered write cannot be
     * reported as verified.
     */
    private boolean containsExpectedShape(JsonNode actual, JsonNode expected) {
        if (actual == null || expected == null) return false;
        if (expected.isObject()) {
            if (!actual.isObject()) return false;
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode actualValue = actual.get(field.getKey());
                if (actualValue == null || !containsExpectedShape(actualValue, field.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            if (!actual.isArray() || actual.size() != expected.size()) return false;
            for (int index = 0; index < expected.size(); index++) {
                if (!containsExpectedShape(actual.get(index), expected.get(index))) return false;
            }
            return true;
        }
        return actual.equals(expected);
    }
}
