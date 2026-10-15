package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.PersistedContentVersion;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.dto.response.WorkbenchReadSnapshot;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.service.NodeRequirementScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads persisted workbench facts for project workflow components. The first slice supports
 * project-basic-info and requirement-scope; everything else stays explicitly UNSUPPORTED until
 * its snapshot adapter ships. Persistence is never inferred from display defaults.
 */
@Component
@RequiredArgsConstructor
public class AiProjectComponentReader {

    private final ObjectMapper objectMapper;
    private final NodeRequirementScopeService requirementScopeService;
    private final com.brad.pms.service.NodeSolutionDesignService solutionDesignService;
    private final com.brad.pms.service.NodePlanResourceRiskService planResourceRiskService;
    private final com.brad.pms.service.NodeDevelopmentControlService developmentControlService;
    private final com.brad.pms.service.NodeAcceptanceService acceptanceService;
    private final com.brad.pms.service.NodeReleaseService releaseService;
    private final com.brad.pms.service.NodeValueReviewService valueReviewService;
    private final com.brad.pms.service.NodeKnowledgeStandardService knowledgeStandardService;
    private final AiContextReferenceProjector referenceProjector;

    public List<AiWorkflowContextDTO.Component> read(ProjectDTO project, ProjectNodeDTO node,
                                                     String componentKey) {
        return read(project, node, componentKey, new ArrayList<>());
    }

    public List<AiWorkflowContextDTO.Component> read(ProjectDTO project, ProjectNodeDTO node,
                                                     String componentKey, List<String> warnings) {
        return read(project, node, componentKey, warnings, referenceProjector);
    }

    public List<AiWorkflowContextDTO.Component> read(ProjectDTO project, ProjectNodeDTO node,
                                                     String componentKey, List<String> warnings,
                                                     AiContextReferenceProjector projector) {
        if (project == null || project.getId() == null) throw BusinessException.notFound("项目不存在");
        if (node == null || node.getId() == null) throw BusinessException.notFound("节点不存在");
        List<String> attached = node.getComponents() == null ? List.of() : node.getComponents();
        List<String> requested;
        if (componentKey == null || componentKey.isBlank()) {
            requested = attached;
        } else {
            if (!attached.contains(componentKey)) {
                throw BusinessException.notFound("组件不存在或未附着于所选节点");
            }
            requested = List.of(componentKey);
        }
        List<AiWorkflowContextDTO.Field> fields = toContractFields(node.getFields());
        List<AiWorkflowContextDTO.Component> components = new ArrayList<>(requested.size());
        for (String key : requested) {
            components.add(toComponent(project, node, key, fields, warnings, projector));
        }
        return components;
    }

    /** Template dynamic fields are shared verbatim with every attached component. */
    private List<AiWorkflowContextDTO.Field> toContractFields(
            List<com.brad.pms.workflow.WorkflowFieldDefinition> definitions) {
        if (definitions == null) return List.of();
        return definitions.stream().filter(Objects::nonNull).map(definition -> new AiWorkflowContextDTO.Field(
                definition.key(), definition.label(),
                definition.type() == null ? null : definition.type().name(),
                definition.required(), definition.options(), definition.visible(),
                definition.binding(), definition.fullWidth())).toList();
    }

    private AiWorkflowContextDTO.Component toComponent(ProjectDTO project, ProjectNodeDTO node, String key,
                                                       List<AiWorkflowContextDTO.Field> fields,
                                                       List<String> warnings,
                                                       AiContextReferenceProjector projector) {
        JsonNode config = node.getComponentConfigs() == null ? null : node.getComponentConfigs().get(key);
        return switch (key) {
            case "project-basic-info" -> {
                Integer entityVersion = project.getVersion() == null ? null : project.getVersion();
                Map<String, Integer> versions = new java.util.HashMap<>();
                versions.put("content", entityVersion);
                yield new AiWorkflowContextDTO.Component(key, key, fields, List.of(), config,
                        objectMapper.valueToTree(projectBasicInfo(project)), "LOADED", versions, null);
            }
            case "requirement-scope" -> readRequirementScope(node, config, fields, warnings, projector);
            case "solution-design" -> readWorkbenchSnapshot(node, "solution-design", config, fields, warnings,
                    solutionDesignService.getReadSnapshot(node.getProjectId(), node.getId()), projector);
            case "plan-resource-risk" -> readWorkbenchSnapshot(node, "plan-resource-risk", config, fields, warnings,
                    planResourceRiskService.getReadSnapshot(node.getProjectId(), node.getId()), projector);
            case "development-control" -> readWorkbenchSnapshot(node, "development-control", config, fields, warnings,
                    developmentControlService.getReadSnapshot(node.getProjectId(), node.getId()), projector);
            case "business-acceptance" -> readWorkbenchSnapshot(node, "business-acceptance", config, fields, warnings,
                    acceptanceService.getReadSnapshot(node.getProjectId(), node.getId()), projector);
            case "release-handover" -> readWorkbenchSnapshot(node, "release-handover", config, fields, warnings,
                    releaseService.getReadSnapshot(node.getProjectId(), node.getId()), projector);
            case "value-review" -> readWorkbenchSnapshot(node, "value-review", config, fields, warnings,
                    valueReviewService.getReadSnapshot(node.getProjectId(), node.getId()), projector);
            case "knowledge-standard" -> readWorkbenchSnapshot(node, "knowledge-standard", config, fields, warnings,
                    knowledgeStandardService.getReadSnapshot(node.getProjectId(), node.getId()), projector);
            default -> new AiWorkflowContextDTO.Component(key, key, fields, List.of(),
                    config, null, "UNSUPPORTED", null, null);
        };
    }

    /** Generic mapping for the single-baseline workbench snapshots. */
    private AiWorkflowContextDTO.Component readWorkbenchSnapshot(ProjectNodeDTO node, String componentKey,
            JsonNode config, List<AiWorkflowContextDTO.Field> fields, List<String> warnings,
            WorkbenchReadSnapshot<?> snapshot, AiContextReferenceProjector projector) {
        boolean persisted = snapshot.hasPersistedContent();
        JsonNode content = persisted ? stripUiFlags(objectMapper.valueToTree(snapshot.display())) : null;
        content = persisted ? projector.projectProjectComponentContent(componentKey, content) : null;
        // 每一个持久化 part 的真实版本都要出现在 versions 中（solution-design 的
        // package/decision/review:* 属于同一组件，缺一部分不能让 saved part 丢失版本事实）。
        java.util.Map<String, Integer> versions = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, PersistedContentVersion> part : snapshot.parts().entrySet()) {
            versions.put(part.getKey(), part.getValue() == null ? null : part.getValue().version());
            if (part.getValue() != null && part.getValue().persisted()
                    && part.getValue().version() == null) {
                warnings.add("UNKNOWN_VERSION:" + part.getKey());
            }
        }
        return new AiWorkflowContextDTO.Component(componentKey, componentKey, fields, List.of(),
                config, content, persisted ? "LOADED" : "EMPTY", versions, null);
    }

    private AiWorkflowContextDTO.Component readRequirementScope(ProjectNodeDTO node, JsonNode config,
                                                                List<AiWorkflowContextDTO.Field> fields,
                                                                List<String> warnings,
                                                                AiContextReferenceProjector projector) {
        WorkbenchReadSnapshot<com.brad.pms.dto.response.NodeRequirementScopeDTO> snapshot =
                requirementScopeService.getReadSnapshot(node.getProjectId(), node.getId());
        PersistedContentVersion contentPart = snapshot.parts().get("content");
        if (contentPart != null && contentPart.persisted() && contentPart.version() == null) {
            warnings.add("UNKNOWN_VERSION:content");
        }
        if (!snapshot.hasPersistedContent()) {
            java.util.Map<String, Integer> emptyVersions = new java.util.HashMap<>();
            emptyVersions.put("content", null);
            return new AiWorkflowContextDTO.Component("requirement-scope", "requirement-scope",
                    fields, List.of(), config, null,
                    "EMPTY", emptyVersions, null);
        }
        Integer version = snapshot.parts().get("content") == null
                ? null : snapshot.parts().get("content").version();
        JsonNode content = stripUiFlags(objectMapper.valueToTree(snapshot.display()));
        content = projector.projectProjectComponentContent("requirement-scope", content);
        java.util.Map<String, Integer> versions = new java.util.HashMap<>();
        versions.put("content", version);
        return new AiWorkflowContextDTO.Component("requirement-scope", "requirement-scope",
                fields, List.of(), config, content, "LOADED",
                versions, null);
    }

    private JsonNode stripUiFlags(JsonNode content) {
        if (content instanceof ObjectNode objectNode) {
            objectNode.remove("canEdit");
            objectNode.remove("topicCreationAllowed");
            objectNode.remove("projectBasicInfo");
            if (objectNode.has("requirements") && objectNode.get("requirements").isArray()) {
                for (JsonNode requirement : objectNode.get("requirements")) {
                    if (requirement instanceof ObjectNode requirementNode) {
                        requirementNode.remove("taskCount");
                        requirementNode.remove("completedTaskCount");
                    }
                }
            }
        }
        return content;
    }

    private Map<String, Object> projectBasicInfo(ProjectDTO project) {
        Map<String, Object> content = new LinkedHashMap<>();
        put(content, "name", project.getName());
        put(content, "code", project.getCode());
        put(content, "description", project.getDescription());
        put(content, "status", project.getStatus());
        put(content, "priority", project.getPriority());
        put(content, "projectLevel", project.getProjectLevel());
        put(content, "ownerId", project.getOwnerId());
        put(content, "ownerName", project.getOwnerName());
        put(content, "projectManagerId", project.getProjectManagerId());
        put(content, "projectManagerName", project.getProjectManagerName());
        put(content, "orgUnitId", project.getOrgUnitId());
        put(content, "orgUnitName", project.getOrgUnitName());
        put(content, "startDate", project.getStartDate());
        put(content, "endDate", project.getEndDate());
        put(content, "progress", project.getProgress());
        return content;
    }

    private void put(Map<String, Object> content, String key, Object value) {
        if (value != null) content.put(key, value);
    }
}
