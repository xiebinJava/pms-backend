package com.brad.pms.ai.connector.context;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.ai.connector.context.AiContextNodeSelector.Selection;
import com.brad.pms.ai.connector.context.AiContextWorkflowStateResolver.State;
import com.brad.pms.common.enums.ProjectStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.service.NodeService;

import com.brad.pms.service.TaskService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.ai.connector.context.NodeRef;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Assembles the project workflow context from actual nodes and saved facts only: no
 * activation, no owner backfill, no default-template resolution, and the contract status
 * table preserving inconsistent facts instead of repairing them.
 */
@Component
@RequiredArgsConstructor
public class AiProjectContextReader {

    private final com.brad.pms.service.ProjectService projectService;
    private final NodeService nodeService;
    private final TaskService taskService;
    private final WorkflowTemplateService workflowTemplateService;
    private final AiProjectComponentReader componentReader;
    private final AiContextNodeSelector nodeSelector;
    private final AiContextWorkflowStateResolver stateResolver;
    private final AiContextReferenceProjector referenceProjector;
    private final AiContextReadPolicy referencePolicy;
    private final AiContextAllowedActions allowedActionsRegistry;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final Clock clock;

    public AiWorkflowContextDTO read(Long projectId, ContextReadOptions options) {
        com.brad.pms.dto.response.ProjectDTO project = projectService.detail(projectId);
        List<ProjectNodeDTO> nodes = orderedNodes(nodeService.listReadOnly(projectId));
        List<NodeRef> refs = nodeRefs(nodes);
        Selection selection = nodeSelector.select(refs, options.nodeId());

        LinkedHashSet<AiWorkflowContextDTO.Warning> warnings = new LinkedHashSet<>();
        boolean configured = !nodes.isEmpty();
        State state = stateResolver.resolve("project", projectStatus(project), null, configured, refs);

        Long selectedNodeId = selection.selectedNodeId();
        if (options.componentKey() != null && selectedNodeId == null) {
            throw BusinessException.error("NO_SELECTED_NODE: 当前没有选中的流程节点，无法按组件读取");
        }
        boolean focused = ContextReadOptions.VIEW_FOCUSED.equals(options.view());
        List<String> unknownComponents = new ArrayList<>();
        boolean unresolvedNodeConfig = false;
        List<AiWorkflowContextDTO.Node> contractNodes = new ArrayList<>(nodes.size());

        for (int position = 0; position < nodes.size(); position++) {
            ProjectNodeDTO node = nodes.get(position);
            boolean isSelected = Objects.equals(node.getId(), selectedNodeId);
            boolean hasDefinition = hasDefinition(node);
            if (!hasDefinition) unresolvedNodeConfig = true;

            List<AiWorkflowContextDTO.Component> components = isSelected
                    ? readSelectedComponents(project, node, options.componentKey(), unknownComponents)
                    : placeholderComponents(node);
            Map<String, Object> fieldValues;
            Map<String, Integer> fieldValueVersions;
            String fieldValuesState;
            if (!isSelected && focused) {
                fieldValues = Map.of();
                fieldValueVersions = null;
                fieldValuesState = "NOT_REQUESTED";
            } else if (!hasDefinition) {
                fieldValues = Map.of();
                fieldValueVersions = null;
                fieldValuesState = "UNAVAILABLE";
            } else {
                Map<String, Object> original = node.getFieldValues() == null ? Map.of()
                        : new LinkedHashMap<>(node.getFieldValues());
                // 项目 focused 也不携带组件原始 JSON（去重，正文仅来自 selected component 内容）。
                if (focused) original.remove("__components");
                fieldValues = referenceProjector.projectFieldValues("project", projectId, original);
                fieldValueVersions = node.getFieldValueVersions();
                fieldValuesState = "LOADED";
            }

            contractNodes.add(new AiWorkflowContextDTO.Node(
                    node.getId(), node.getNodeKey(), node.getName(), position, node.getStatus(),
                    components, contractFields(node.getFields()), fieldValues,
                    node.getVersion(), node.getDescription(), node.getDeliverable(),
                    node.getOwnerId(), node.getOwnerName(), node.getStartDate(), node.getEndDate(),
                    fieldValueVersions, fieldValuesState, node.getPermissions(),
                    isSelected ? taskService.summarizeByNode(projectId, node.getId()) : null));
        }
        if (unresolvedNodeConfig) {
            warnings.add(new AiWorkflowContextDTO.Warning("UNRESOLVED_NODE_CONFIG",
                    "部分实际节点在工作流模板定义中未声明"));
        }
        for (String flag : unknownComponents) {
            if (flag.startsWith("UNKNOWN_VERSION:")) {
                warnings.add(new AiWorkflowContextDTO.Warning("UNKNOWN_VERSION",
                        "持久化内容的版本未知（部分: " + flag.substring("UNKNOWN_VERSION:".length()) + "）"));
            } else {
                warnings.add(new AiWorkflowContextDTO.Warning("UNKNOWN_COMPONENT",
                        "组件尚未支持只读装载: " + flag));
            }
        }
        mergeWarnings(warnings, selection, state);

        AiWorkflowContextDTO.CurrentNode currentNode = currentNode(nodes, selection);
        AiWorkflowContextDTO.Workflow workflow = configured
                ? new AiWorkflowContextDTO.Workflow(
                        project.getWorkflowTemplateVersionId(),
                        workflowTemplateService.getVersionNo(project.getWorkflowTemplateVersionId()),
                        contractNodes)
                : null;
        Map<String, Object> details = resourceDetails(project);
        List<AiWorkflowContextDTO.Relation> relations = relations(projectId);
        AiWorkflowContextDTO.ContextMeta meta = AiContextMetaFactory.build(options, selection, state,
                options.componentKey(), configured, null, project.getVersion(), warnings, clock);
        return new AiWorkflowContextDTO("project", projectId, project.getVersion(), currentNode,
                workflow, allowedActionsRegistry.projectActions(), project.getVersion(),
                details, relations, meta);
    }

    private boolean hasDefinition(ProjectNodeDTO node) {
        return node.getFields() != null && !node.getFields().isEmpty()
                || Boolean.TRUE.equals(node.getProjectBasicInfo())
                || node.getComponentConfigs() != null && !node.getComponentConfigs().isEmpty();
    }

    private List<AiWorkflowContextDTO.Component> readSelectedComponents(com.brad.pms.dto.response.ProjectDTO project,
            ProjectNodeDTO node, String componentKey, List<String> unknownComponents) {
        List<AiWorkflowContextDTO.Component> components =
                componentReader.read(project, node, componentKey);
        for (AiWorkflowContextDTO.Component component : components) {
            if ("UNSUPPORTED".equals(component.contentState())) unknownComponents.add(component.key());
        }
        return components;
    }

    private List<AiWorkflowContextDTO.Component> placeholderComponents(ProjectNodeDTO node) {
        List<String> attached = node.getComponents() == null ? List.of() : node.getComponents();
        List<AiWorkflowContextDTO.Field> fields = contractFields(node.getFields());
        List<String> componentKeys = attached;
        List<AiWorkflowContextDTO.Component> components = new ArrayList<>(componentKeys.size());
        for (String key : componentKeys) {
            var config = node.getComponentConfigs() == null ? null : node.getComponentConfigs().get(key);
            components.add(new AiWorkflowContextDTO.Component(key, key, fields, List.of(),
                    config, null, "NOT_REQUESTED", null, null));
        }
        return components;
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

    private Map<String, Object> fieldValues(Map<String, Object> values) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (values != null) safe.putAll(values);
        return safe;
    }

    private List<NodeRef> nodeRefs(List<ProjectNodeDTO> nodes) {
        return nodes.stream()
                .map(node -> new NodeRef(node.getId(), node.getStatus(), node.getSort() == null ? 0 : node.getSort()))
                .toList();
    }

    private List<ProjectNodeDTO> orderedNodes(List<ProjectNodeDTO> nodes) {
        return nodes.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((ProjectNodeDTO node) -> node.getSort() == null ? Integer.MAX_VALUE : node.getSort())
                        .thenComparing(ProjectNodeDTO::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private AiWorkflowContextDTO.CurrentNode currentNode(List<ProjectNodeDTO> nodes, Selection selection) {
        Long current = selection.currentNodeId();
        if (current == null) return null;
        return nodes.stream().filter(node -> Objects.equals(node.getId(), current)).findFirst()
                .map(node -> new AiWorkflowContextDTO.CurrentNode(node.getId(), node.getNodeKey(), node.getName()))
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

    private String projectStatus(ProjectDTO project) {
        int normalized = ProjectStatus.normalize(project.getStatus());
        return switch (normalized) {
            case 1 -> "ACTIVE";
            case 2 -> "COMPLETED";
            case 3 -> "TERMINATED";
            default -> "UNKNOWN";
        };
    }

    private Map<String, Object> resourceDetails(ProjectDTO project) {
        Map<String, Object> details = new LinkedHashMap<>();
        put(details, "name", project.getName());
        put(details, "description", project.getDescription());
        put(details, "status", project.getStatus());
        put(details, "priority", project.getPriority());
        put(details, "ownerId", project.getOwnerId());
        put(details, "ownerName", project.getOwnerName());
        put(details, "projectManagerId", project.getProjectManagerId());
        put(details, "projectManagerName", project.getProjectManagerName());
        put(details, "orgUnitId", project.getOrgUnitId());
        put(details, "orgUnitName", project.getOrgUnitName());
        put(details, "startDate", project.getStartDate());
        put(details, "endDate", project.getEndDate());
        put(details, "progress", project.getProgress());
        return referenceProjector.projectResourceDetails("project", project.getId(), details);
    }

    private void put(Map<String, Object> details, String key, Object value) {
        if (value != null) details.put(key, value);
    }

    private List<AiWorkflowContextDTO.Relation> relations(Long projectId) {
        List<ProjectNodeDevelopmentTopicDO> topics = topicMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeDevelopmentTopicDO>()
                        .eq(ProjectNodeDevelopmentTopicDO::getProjectId, projectId)
                        .orderByAsc(ProjectNodeDevelopmentTopicDO::getId));
        List<AiWorkflowContextDTO.Relation> relations = new ArrayList<>();
        for (ProjectNodeDevelopmentTopicDO topic : topics == null ? List.<ProjectNodeDevelopmentTopicDO>of() : topics) {
            if (topic == null || topic.getId() == null || Boolean.TRUE.equals(topic.getDeleted())) continue;
            if (!referencePolicy.canReadReference("topic", topic.getId())) continue;
            relations.add(new AiWorkflowContextDTO.Relation("topic", topic.getId(), topic.getTitle(), true));
        }
        return relations;
    }
}
