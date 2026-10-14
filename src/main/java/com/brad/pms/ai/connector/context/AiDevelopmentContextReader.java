package com.brad.pms.ai.connector.context;

import com.brad.pms.ai.connector.context.AiContextNodeSelector.Selection;
import com.brad.pms.ai.connector.context.AiContextWorkflowStateResolver.State;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.service.RequirementManagementService;
import com.brad.pms.ai.connector.context.NodeRef;
import com.brad.pms.workflow.DevelopmentItemType;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembles requirement/topic/story workflow context from the pure read entries only
 * (detailForContext + detailIfInitialized), with no fallback facts and no initialization.
 */
@Component
@RequiredArgsConstructor
public class AiDevelopmentContextReader {

    private final DevelopmentItemWorkflowService workflowService;
    private final RequirementManagementService requirementManagementService;
    private final AiDevelopmentComponentReader componentReader;
    private final AiContextNodeSelector nodeSelector;
    private final AiContextWorkflowStateResolver stateResolver;
    private final AiContextReferenceProjector referenceProjector;
    private final AiContextReadPolicy referencePolicy;
    private final AiContextAllowedActions allowedActionsRegistry;
    private final Clock clock;

    public AiWorkflowContextDTO read(DevelopmentItemType itemType, Long itemId, ContextReadOptions options) {
        String resourceType = itemType.name().toLowerCase(Locale.ROOT);
        if (itemType == DevelopmentItemType.REQUIREMENT) return readRequirement(itemId, options, resourceType);

        DevelopmentItemWorkflowDetailDTO detail = workflowService.detailIfInitialized(itemType, itemId);
        List<NodeRef> refs = nodeRefs(nodes(detail));
        Selection selection = nodeSelector.select(refs, options.nodeId());
        boolean configured = Boolean.TRUE.equals(detail.getWorkflowConfigured()) && !nodes(detail).isEmpty();
        State state = stateResolver.resolve(resourceType, detail.getDevelopmentStatus(),
                detail.getTerminalStatus(), configured, refs);

        LinkedHashSet<AiWorkflowContextDTO.Warning> warnings = new LinkedHashSet<>();
        List<String> unknownComponents = new ArrayList<>();
        Long selectedNodeId = selection.selectedNodeId();
        boolean focused = ContextReadOptions.VIEW_FOCUSED.equals(options.view());
        List<AiWorkflowContextDTO.Node> contractNodes = new ArrayList<>(nodes(detail).size());

        for (int position = 0; position < nodes(detail).size(); position++) {
            DevelopmentItemWorkflowNodeDTO node = nodes(detail).get(position);
            boolean isSelected = Objects.equals(node.getId(), selectedNodeId);
            List<AiWorkflowContextDTO.Component> components = isSelected
                    ? componentReader.read(itemType, detail, node, null,
                            options.componentKey(), unknownComponents)
                    : placeholderComponents(node);

            Map<String, Object> fieldValues = fieldsFor(node, isSelected, focused, resourceType, itemId);
            String fieldValuesState = !isSelected ? (focused ? "NOT_REQUESTED" : "LOADED") : "LOADED";

            contractNodes.add(new AiWorkflowContextDTO.Node(
                    node.getId(), node.getNodeKey(), node.getName(), position, node.getStatus(),
                    components, contractFields(node.getFields()), fieldValues,
                    node.getVersion(), node.getDescription(), node.getDeliverable(),
                    node.getOwnerId(), node.getOwnerName(), node.getStartDate(), node.getEndDate(),
                    null, fieldValuesState, null,
                    isSelected ? taskSummary(detail, node) : null));
        }
        for (String unknown : unknownComponents) {
            warnings.add(new AiWorkflowContextDTO.Warning("UNKNOWN_COMPONENT",
                    "组件尚未支持只读读取: " + unknown));
        }
        mergeWarnings(warnings, selection, state);

        return assemble(resourceType, itemId, detail, nodes(detail), selection, state, configured,
                null, null, detail.getWorkflowVersion(), null, warnings, contractNodes, detail,
                itemType, options);
    }

    private AiWorkflowContextDTO readRequirement(Long itemId, ContextReadOptions options, String resourceType) {
        RequirementListDTO detail = requirementManagementService.detailForContext(itemId);
        DevelopmentItemWorkflowDetailDTO workflow =
                workflowService.detailIfInitialized(DevelopmentItemType.REQUIREMENT, itemId);
        List<NodeRef> refs = nodeRefs(nodes(workflow));
        Selection selection = nodeSelector.select(refs, options.nodeId());
        boolean configured = Boolean.TRUE.equals(workflow.getWorkflowConfigured()) && !nodes(workflow).isEmpty();
        State state = stateResolver.resolve(resourceType, detail.getStatus(),
                detail.getTerminalStatus(), configured, refs);

        LinkedHashSet<AiWorkflowContextDTO.Warning> warnings = new LinkedHashSet<>();
        List<String> unknownComponents = new ArrayList<>();
        Long selectedNodeId = selection.selectedNodeId();
        boolean focused = ContextReadOptions.VIEW_FOCUSED.equals(options.view());
        List<AiWorkflowContextDTO.Node> contractNodes = new ArrayList<>(nodes(workflow).size());

        for (int position = 0; position < nodes(workflow).size(); position++) {
            DevelopmentItemWorkflowNodeDTO node = nodes(workflow).get(position);
            boolean isSelected = Objects.equals(node.getId(), selectedNodeId);
            List<AiWorkflowContextDTO.Component> components = isSelected
                    ? componentReader.read(DevelopmentItemType.REQUIREMENT, workflow, node,
                            detail.getVersion(), options.componentKey(), unknownComponents)
                    : placeholderComponents(node);

            Map<String, Object> fieldValues = fieldsFor(node, isSelected, focused, resourceType, itemId);
            String fieldValuesState = !isSelected ? (focused ? "NOT_REQUESTED" : "LOADED") : "LOADED";

            contractNodes.add(new AiWorkflowContextDTO.Node(
                    node.getId(), node.getNodeKey(), node.getName(), position, node.getStatus(),
                    components, contractFields(node.getFields()), fieldValues,
                    node.getVersion(), node.getDescription(), node.getDeliverable(),
                    node.getOwnerId(), node.getOwnerName(), node.getStartDate(), node.getEndDate(),
                    null, fieldValuesState, null,
                    isSelected ? taskSummary(workflow, node) : null));
        }
        for (String unknown : unknownComponents) {
            warnings.add(new AiWorkflowContextDTO.Warning("UNKNOWN_COMPONENT",
                    "组件尚未支持只读读取: " + unknown));
        }
        mergeWarnings(warnings, selection, state);

        RequirementFacts facts = new RequirementFacts(detail.getStatus(), detail.getTitle(),
                detail.getDescription(), detail.getPriority(), detail.getOwnerId(), detail.getOwnerName(),
                detail.getOrgUnitId(), detail.getExecutionTarget(), detail.getVersion(),
                detail.getTerminalStatus());
        return assemble(resourceType, itemId, workflow, nodes(workflow), selection, state, configured,
                detail.getVersion(), detail.getVersion(), workflow.getWorkflowVersion(), facts,
                warnings, contractNodes, workflow, DevelopmentItemType.REQUIREMENT, options);
    }

    private record RequirementFacts(String status, String title, String description, Integer priority,
                                    Long ownerId, String ownerName, Long orgUnitId,
                                    com.brad.pms.dto.response.RequirementExecutionTargetDTO executionTarget,
                                    Integer version, String terminalStatus) {
    }

    private AiWorkflowContextDTO assemble(String resourceType, Long itemId,
            DevelopmentItemWorkflowDetailDTO detail, List<DevelopmentItemWorkflowNodeDTO> nodes,
            Selection selection, State state, boolean configured, Integer entityVersionFactVersion,
            Integer entityVersion, Integer workflowInstanceVersion, RequirementFacts requirement,
            LinkedHashSet<AiWorkflowContextDTO.Warning> warnings,
            List<AiWorkflowContextDTO.Node> contractNodes, DevelopmentItemWorkflowDetailDTO workflowDetail,
            DevelopmentItemType itemType, ContextReadOptions options) {
        AiWorkflowContextDTO.CurrentNode currentNode = currentNode(nodes, selection);
        AiWorkflowContextDTO.Workflow workflow = configured
                ? new AiWorkflowContextDTO.Workflow(detail.getTemplateVersionId(),
                        detail.getTemplateVersionNo(), contractNodes)
                : null;
        Map<String, Object> details = resourceDetails(itemType, workflowDetail, requirement);
        List<AiWorkflowContextDTO.Relation> relations = relations(itemType, workflowDetail, requirement);
        AiWorkflowContextDTO.ContextMeta meta = AiContextMetaFactory.build(options, selection, state,
                options.componentKey(), Boolean.TRUE.equals(detail.getWorkflowConfigured()),
                workflowInstanceVersion, entityVersion, warnings, clock);
        Integer topVersion = currentNodeVersion(nodes, selection);
        return new AiWorkflowContextDTO(resourceType, itemId,
                topVersion == null ? entityVersionFactVersion : topVersion, currentNode,
                workflow, allowedActionsRegistry.developmentActions(resourceType), entityVersion,
                details, relations, meta);
    }

    private List<AiWorkflowContextDTO.Component> placeholderComponents(DevelopmentItemWorkflowNodeDTO node) {
        List<String> attached = node.getRuntimeComponents() == null ? List.of() : node.getRuntimeComponents();
        List<AiWorkflowContextDTO.Component> components = new ArrayList<>(attached.size());
        for (String key : attached) {
            components.add(new AiWorkflowContextDTO.Component(key, key, List.of(), List.of(),
                    node.getComponentConfigs() == null ? null : node.getComponentConfigs().get(key),
                    null, "NOT_REQUESTED", null, null));
        }
        return components;
    }

    private Map<String, Object> fieldsFor(DevelopmentItemWorkflowNodeDTO node, boolean isSelected,
                                          boolean focused, String resourceType, Long resourceId) {
        Map<String, Object> fieldValues;
        if (!isSelected) {
            fieldValues = Map.of();
        } else if (focused) {
            Map<String, Object> selectedValues = new LinkedHashMap<>();
            selectedValues.putAll(node.getFieldValues() == null ? Map.of() : node.getFieldValues());
            selectedValues.remove("__components");
            fieldValues = referenceProjector.projectFieldValues(resourceType, resourceId, selectedValues);
        } else {
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.putAll(node.getFieldValues() == null ? Map.of() : node.getFieldValues());
            merged.putAll(node.getBoundFieldValues() == null ? Map.of() : node.getBoundFieldValues());
            fieldValues = referenceProjector.projectFieldValues(resourceType, resourceId, merged);
        }
        return fieldValues;
    }

    private com.brad.pms.dto.response.NodeTaskSummaryDTO taskSummary(
            DevelopmentItemWorkflowDetailDTO detail, DevelopmentItemWorkflowNodeDTO node) {
        return detail.getNodeTaskSummaries() == null ? null : detail.getNodeTaskSummaries().get(node.getId());
    }

    private List<NodeRef> nodeRefs(List<DevelopmentItemWorkflowNodeDTO> nodes) {
        return nodes.stream()
                .map(node -> new NodeRef(node.getId(), node.getStatus(),
                        node.getSort() == null ? 0 : node.getSort()))
                .toList();
    }

    private List<DevelopmentItemWorkflowNodeDTO> nodes(DevelopmentItemWorkflowDetailDTO detail) {
        return orderedNodes(detail.getNodes());
    }

    private List<DevelopmentItemWorkflowNodeDTO> orderedNodes(List<DevelopmentItemWorkflowNodeDTO> nodes) {
        return nodes == null ? List.of() : nodes.stream()
                .sorted(Comparator
                        .comparing((DevelopmentItemWorkflowNodeDTO node) -> node.getSort() == null
                                ? Integer.MAX_VALUE : node.getSort())
                        .thenComparing(DevelopmentItemWorkflowNodeDTO::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Integer currentNodeVersion(List<DevelopmentItemWorkflowNodeDTO> nodes, Selection selection) {
        Long current = selection.currentNodeId();
        if (current == null) return null;
        return nodes.stream().filter(node -> Objects.equals(node.getId(), current)).findFirst()
                .map(DevelopmentItemWorkflowNodeDTO::getVersion).orElse(null);
    }

    private AiWorkflowContextDTO.CurrentNode currentNode(List<DevelopmentItemWorkflowNodeDTO> nodes,
                                                         Selection selection) {
        Long current = selection.currentNodeId();
        if (current == null) return null;
        return nodes.stream().filter(node -> Objects.equals(node.getId(), current)).findFirst()
                .map(node -> new AiWorkflowContextDTO.CurrentNode(node.getId(), node.getNodeKey(),
                        node.getName()))
                .orElse(null);
    }

    private void mergeWarnings(LinkedHashSet<AiWorkflowContextDTO.Warning> target,
                               Selection selection, State state) {
        boolean abnormal = "INCONSISTENT".equals(state.status())
                || "NOT_CONFIGURED".equals(state.status());
        for (AiWorkflowContextDTO.Warning warning : selection.warnings()) {
            if ("NO_ACTIVE_NODE".equals(warning.code()) && !abnormal) continue;
            target.add(warning);
        }
        target.addAll(state.warnings());
    }

    private Map<String, Object> resourceDetails(DevelopmentItemType itemType,
                                                DevelopmentItemWorkflowDetailDTO detail,
                                                RequirementFacts requirement) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (requirement != null) {
            put(details, "title", requirement.title());
            put(details, "description", requirement.description());
            put(details, "status", requirement.status());
            put(details, "priority", requirement.priority());
            put(details, "ownerId", requirement.ownerId());
            put(details, "ownerName", requirement.ownerName());
            put(details, "orgUnitId", requirement.orgUnitId());
            if (requirement.executionTarget() != null) {
                Map<String, Object> target = executionTargetMap(itemType, requirement.executionTarget());
                if (!target.isEmpty()) details.put("executionTarget", target);
            }
        } else {
            put(details, "title", detail.getTitle());
            put(details, "developmentStatus", detail.getDevelopmentStatus());
            put(details, "developmentProgress", detail.getDevelopmentProgress());
            put(details, "ownerId", detail.getOwnerId());
            put(details, "ownerName", detail.getOwnerName());
            if (itemType == DevelopmentItemType.STORY) {
                put(details, "startDate", detail.getStartDate());
                put(details, "dueDate", detail.getDueDate());
                put(details, "storyPoints", detail.getStoryPoints());
                put(details, "blocker", detail.getBlocker());
                put(details, "latestBuildVersion", detail.getLatestBuildVersion());
                put(details, "testStatus", detail.getTestStatus());
                put(details, "iterationPlanId", detail.getIterationPlanId());
                put(details, "iterationPlanName", detail.getIterationPlanName());
            }
            if (detail.getSourceRequirements() != null) {
                List<Map<String, Object>> sources = new ArrayList<>();
                for (var source : detail.getSourceRequirements()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    put(item, "id", source.getId());
                    put(item, "title", source.getTitle());
                    put(item, "status", source.getStatus());
                    put(item, "ownerName", source.getOwnerName());
                    sources.add(item);
                }
                details.put("sourceRequirements", sources);
            }
        }
        return referenceProjector.projectResourceDetails(
                itemType.name().toLowerCase(Locale.ROOT), detail.getId(), details);
    }

    private Map<String, Object> executionTargetMap(DevelopmentItemType ignored,
            com.brad.pms.dto.response.RequirementExecutionTargetDTO target) {
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "targetType", target.getTargetType() == null ? null : target.getTargetType().name());
        put(map, "title", target.getTitle());
        put(map, "code", target.getCode());
        put(map, "status", target.getStatus());
        put(map, "ownerId", target.getOwnerId());
        put(map, "ownerName", target.getOwnerName());
        put(map, "progress", target.getProgress());
        return map;
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private List<AiWorkflowContextDTO.Relation> relations(DevelopmentItemType itemType,
            DevelopmentItemWorkflowDetailDTO detail, RequirementFacts requirement) {
        List<AiWorkflowContextDTO.Relation> relations = new ArrayList<>();
        if (itemType == DevelopmentItemType.REQUIREMENT) {
            var target = requirement != null ? requirement.executionTarget() : null;
            if (target != null && target.getTargetType() != null && target.getTargetId() != null) {
                String type = target.getTargetType().name().toLowerCase(Locale.ROOT);
                if (referencePolicy.canReadReference(type, target.getTargetId())) {
                    relations.add(new AiWorkflowContextDTO.Relation(type, target.getTargetId(),
                            target.getTitle(), true));
                }
            }
            return relations;
        }
        if (itemType == DevelopmentItemType.STORY) {
            if (detail.getProjectId() != null
                    && referencePolicy.canReadReference("project", detail.getProjectId())) {
                relations.add(new AiWorkflowContextDTO.Relation("project", detail.getProjectId(),
                        detail.getProjectName(), true));
            }
            if (detail.getIterationPlanId() != null
                    && referencePolicy.canReadReference("iteration_plan", detail.getIterationPlanId())) {
                relations.add(new AiWorkflowContextDTO.Relation("iteration_plan",
                        detail.getIterationPlanId(), detail.getIterationPlanName(), true));
            }
        }
        if (detail.getSourceRequirements() != null) {
            for (var source : detail.getSourceRequirements()) {
                if (source.getId() == null) continue;
                if (!referencePolicy.canReadReference("requirement", source.getId())) continue;
                relations.add(new AiWorkflowContextDTO.Relation("requirement", source.getId(),
                        source.getTitle(), true));
            }
        }
        return relations;
    }

    private List<AiWorkflowContextDTO.Field> contractFields(
            List<com.brad.pms.workflow.WorkflowFieldDefinition> definitions) {
        if (definitions == null) return List.of();
        return definitions.stream().filter(Objects::nonNull).map(definition -> new AiWorkflowContextDTO.Field(
                definition.key(), definition.label(),
                definition.type() == null ? null : definition.type().name(),
                definition.required(), definition.options(), definition.visible(),
                definition.binding(), definition.fullWidth())).toList();
    }
}
