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
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiOrchestratorVerificationService {

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
                    new ContextReadOptions(ContextReadOptions.VIEW_FOCUSED, plan.getNodeId(), null));

            boolean resourceReadback = resource != null
                    && Objects.equals(resource.resourceType(), plan.getResourceType())
                    && Objects.equals(resource.resourceId(), plan.getResourceId());
            boolean contextReadback = after != null
                    && Objects.equals(after.resourceType(), plan.getResourceType())
                    && Objects.equals(after.resourceId(), plan.getResourceId());
            boolean protectedFieldsUnchanged = contextReadback
                    && unchangedWorkflowState(before, after)
                    && versionDidNotMoveBack(plan, after);
            Map<String, Object> resourceDetails = resource == null ? null : resource.details();
            Map<String, Object> operationData = operation == null ? null : operation.data();
            boolean patchApplied = "SUCCEEDED".equals(operation == null ? null : operation.status())
                    && (containsPatch(resourceDetails, draft.draft().patch())
                    || containsPatch(operationData, draft.draft().patch()));

            if (!resourceReadback || !contextReadback) {
                return new AiOrchestratorResponse.VerificationView(resourceReadback, contextReadback,
                        protectedFieldsUnchanged, patchApplied, "WRITE_RESULT_UNKNOWN",
                        "业务写入结果无法通过回读确认");
            }
            if (!protectedFieldsUnchanged || !patchApplied) {
                return new AiOrchestratorResponse.VerificationView(resourceReadback, contextReadback,
                        protectedFieldsUnchanged, patchApplied, "VERIFICATION_FAILED",
                        "回读结果未满足分析保存验证条件");
            }
            return new AiOrchestratorResponse.VerificationView(true, true, true, true,
                    "SUCCEEDED", "写入并回读验证成功");
        } catch (Exception exception) {
            return new AiOrchestratorResponse.VerificationView(false, false, false, false,
                    "WRITE_RESULT_UNKNOWN", "业务写入结果无法通过回读确认");
        }
    }

    private boolean versionDidNotMoveBack(AiPlanDO plan, AiWorkflowContextDTO after) {
        return after.version() == null || plan.getContextVersion() == null
                || after.version() >= plan.getContextVersion();
    }

    private boolean unchangedWorkflowState(AiWorkflowContextDTO before, AiWorkflowContextDTO after) {
        if (before == null || after == null) return false;
        if (!Objects.equals(nodeId(before), nodeId(after))
                || !Objects.equals(nodeKey(before), nodeKey(after))) {
            return false;
        }
        return protectedDetailsEqual(before.resourceDetails(), after.resourceDetails());
    }

    private Long nodeId(AiWorkflowContextDTO context) {
        return context.currentNode() == null ? null : context.currentNode().id();
    }

    private String nodeKey(AiWorkflowContextDTO context) {
        return context.currentNode() == null ? null : context.currentNode().key();
    }

    private boolean protectedDetailsEqual(Map<String, Object> before, Map<String, Object> after) {
        if (before == null || after == null) return true;
        for (String key : new String[]{"systemId", "targetType", "targetId", "ownerId", "status", "startDate", "endDate"}) {
            if (!Objects.equals(before.get(key), after.get(key))) return false;
        }
        return true;
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
