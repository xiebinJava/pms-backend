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
                            "story".equalsIgnoreCase(plan.getResourceType()) ? STORY_NODE_WORKBENCH : null));

            boolean resourceReadback = resource != null
                    && Objects.equals(resource.resourceType(), plan.getResourceType())
                    && Objects.equals(resource.resourceId(), plan.getResourceId());
            boolean contextReadback = after != null
                    && Objects.equals(after.resourceType(), plan.getResourceType())
                    && Objects.equals(after.resourceId(), plan.getResourceId());
            boolean protectedFieldsUnchanged = contextReadback
                    && storyContextMatches(plan, after)
                    && unchangedWorkflowState(before, after)
                    && storyProtectedFieldsUnchanged(plan, before, after)
                    && versionDidNotMoveBack(plan, after);
            Map<String, Object> resourceDetails = resource == null ? null : resource.details();
            boolean patchApplied = "SUCCEEDED".equals(operation == null ? null : operation.status())
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
        return after.version() != null && plan.getContextVersion() != null
                && after.version() >= plan.getContextVersion();
    }

    private boolean unchangedWorkflowState(AiWorkflowContextDTO before, AiWorkflowContextDTO after) {
        if (before == null || after == null) return false;
        if (!Objects.equals(nodeId(before), nodeId(after))
                || !Objects.equals(nodeKey(before), nodeKey(after))) {
            return false;
        }
        if (!protectedDetailsEqual(before.resourceDetails(), after.resourceDetails())) return false;
        AiWorkflowContextDTO.Node beforeNode = selectedNode(before);
        AiWorkflowContextDTO.Node afterNode = selectedNode(after);
        return beforeNode != null && afterNode != null
                && Objects.equals(beforeNode.status(), afterNode.status())
                && Objects.equals(beforeNode.ownerId(), afterNode.ownerId())
                && Objects.equals(beforeNode.startDate(), afterNode.startDate())
                && Objects.equals(beforeNode.endDate(), afterNode.endDate());
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

    private boolean protectedDetailsEqual(Map<String, Object> before, Map<String, Object> after) {
        if (before == null || after == null) return false;
        for (String key : new String[]{"systemId", "targetType", "targetId", "title", "baseTitle",
                "topicId", "baseTopicId", "ownerId", "status", "developmentStatus", "startDate",
                "endDate", "dueDate"}) {
            if (!Objects.equals(before.get(key), after.get(key))) return false;
        }
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
                                                  AiWorkflowContextDTO after) {
        if (!"story".equalsIgnoreCase(plan.getResourceType())) return true;
        AiWorkflowContextDTO.Component beforeComponent = storyComponent(before, plan.getNodeId());
        AiWorkflowContextDTO.Component afterComponent = storyComponent(after, plan.getNodeId());
        if (beforeComponent == null || afterComponent == null) return false;
        Object rawProtected = beforeComponent.saveContract() == null
                ? null : beforeComponent.saveContract().get("protectedFields");
        if (!(rawProtected instanceof List<?> protectedFields)) return false;
        for (Object field : protectedFields) {
            if (!(field instanceof String key) || key.isBlank()) continue;
            JsonNode beforeValue = value(beforeComponent.content(), key);
            JsonNode afterValue = value(afterComponent.content(), key);
            if (!Objects.equals(beforeValue, afterValue)) return false;
        }
        return true;
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
        return containsPatch(resourceDetails, patch) || containsPatchInWorkflowComponents(after, patch);
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
        if (container.isObject() && patch.isObject()) {
            boolean allMatch = true;
            Iterator<Map.Entry<String, JsonNode>> fields = patch.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode actual = container.get(field.getKey());
                if (actual == null || !(actual.equals(field.getValue())
                        || containsPatch(actual, field.getValue()))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        if (container.isContainerNode()) {
            for (JsonNode child : container) {
                if (containsPatch(child, patch)) return true;
            }
        }
        return false;
    }
}
