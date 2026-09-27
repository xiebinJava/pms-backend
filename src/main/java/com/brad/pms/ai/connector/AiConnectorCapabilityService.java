package com.brad.pms.ai.connector;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.PmsCommandDescriptor;
import com.brad.pms.ai.command.PmsCommandMetadata;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.DevelopmentWorkflowTemplateOptionsDTO;
import com.brad.pms.dto.response.WorkflowTemplateSummaryDTO;
import com.brad.pms.dto.response.WorkflowTemplateVersionSummaryDTO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.integration.ai.api.AiCapabilityDTO;
import com.brad.pms.integration.ai.security.AiConnectorScopePolicy;
import com.brad.pms.security.UserContext;
import com.brad.pms.service.UserService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Publishes the server-owned command registry and workflow definitions without
 * hard-coding the current product's node or component names.
 */
@Service
@RequiredArgsConstructor
public class AiConnectorCapabilityService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final PmsCommandRegistry commandRegistry;
    private final WorkflowTemplateService workflowTemplateService;
    private final UserService userService;

    public AiCapabilityDTO capabilities() {
        Map<String, List<AiCapabilityDTO.ActionCapability>> grouped = new LinkedHashMap<>();
        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        List<CommandName> names = commandRegistry.list().stream()
                .sorted(Comparator.comparing(CommandName::code)).toList();
        for (CommandName name : names) {
            PmsCommandDescriptor descriptor = PmsCommandMetadata.descriptor(name);
            if (descriptor.scopes().stream().anyMatch(scope -> !scopeAvailable(scope))) continue;
            descriptor.scopes().stream().filter(this::scopeAvailable).forEach(scopes::add);
            AiCapabilityDTO.ActionCapability action = new AiCapabilityDTO.ActionCapability(
                    name.code(), name.code(), descriptor.description(), "automatic", descriptor.risk(),
                    descriptor.scopes(), descriptor.parameters(), requiresContext(descriptor),
                    descriptor.refreshScopes());
            for (String resourceType : resourceTypes(name)) {
                grouped.computeIfAbsent(resourceType, ignored -> new ArrayList<>()).add(action);
            }
        }
        allConnectorScopes().stream().filter(this::scopeAvailable).forEach(scopes::add);

        List<AiCapabilityDTO.ResourceCapability> resources = grouped.entrySet().stream()
                .map(entry -> new AiCapabilityDTO.ResourceCapability(entry.getKey(), entry.getValue()))
                .toList();
        return new AiCapabilityDTO(
                "v1",
                resources,
                List.copyOf(scopes),
                workflowTypes(),
                viewer(),
                LocalDate.now(BUSINESS_ZONE).toString());
    }

    private List<AiCapabilityDTO.WorkflowTypeCapability> workflowTypes() {
        DevelopmentWorkflowTemplateOptionsDTO options = workflowTemplateService.developmentOptions();
        if (options == null) return List.of();
        List<AiCapabilityDTO.WorkflowTypeCapability> result = new ArrayList<>();
        addWorkflowType(result, "topic-management", "专题管理", options.getTopicTemplates());
        addWorkflowType(result, "story-management", "故事管理", options.getStoryTemplates());
        addWorkflowType(result, "requirement-management", "需求管理", options.getRequirementTemplates());
        return List.copyOf(result);
    }

    private void addWorkflowType(List<AiCapabilityDTO.WorkflowTypeCapability> target,
                                 String processType, String label,
                                 List<WorkflowTemplateSummaryDTO> summaries) {
        if (summaries == null || summaries.isEmpty()) return;
        List<AiCapabilityDTO.WorkflowTemplateCapability> templates = new ArrayList<>();
        for (WorkflowTemplateSummaryDTO summary : summaries) {
            if (summary == null) continue;
            List<WorkflowTemplateVersionSummaryDTO> versions = summary.getPublishedVersions();
            if (versions == null || versions.isEmpty()) {
                versions = summary.getPublishedVersionId() == null ? List.of() : List.of(versionFallback(summary));
            }
            for (WorkflowTemplateVersionSummaryDTO version : versions) {
                if (version == null || version.getId() == null) continue;
                WorkflowTemplateDefinition definition = workflowTemplateService.getDefinition(version.getId());
                templates.add(new AiCapabilityDTO.WorkflowTemplateCapability(
                        version.getId(), version.getVersionNo(), summary.getName(),
                        Boolean.TRUE.equals(version.getIsDefault())
                                || Objects.equals(version.getId(), summary.getDefaultTemplateVersionId())
                                || Boolean.TRUE.equals(summary.getDefaultTemplate()),
                        workflowNodes(definition)));
            }
        }
        if (!templates.isEmpty()) {
            target.add(new AiCapabilityDTO.WorkflowTypeCapability(processType, label, templates));
        }
    }

    private WorkflowTemplateVersionSummaryDTO versionFallback(WorkflowTemplateSummaryDTO summary) {
        WorkflowTemplateVersionSummaryDTO version = new WorkflowTemplateVersionSummaryDTO();
        version.setId(summary.getPublishedVersionId());
        version.setVersionNo(summary.getPublishedVersionNo());
        version.setIsDefault(Boolean.TRUE.equals(summary.getDefaultTemplate()));
        return version;
    }

    private List<AiCapabilityDTO.WorkflowNodeCapability> workflowNodes(WorkflowTemplateDefinition definition) {
        if (definition == null || definition.nodes() == null) return List.of();
        List<AiCapabilityDTO.WorkflowNodeCapability> nodes = new ArrayList<>();
        for (int position = 0; position < definition.nodes().size(); position++) {
            WorkflowNodeDefinition node = definition.nodes().get(position);
            if (node == null) continue;
            List<AiCapabilityDTO.WorkflowComponentCapability> components = node.runtimeComponents().stream()
                    .map(component -> new AiCapabilityDTO.WorkflowComponentCapability(
                            component, component, fields(node.fields())))
                    .toList();
            nodes.add(new AiCapabilityDTO.WorkflowNodeCapability(
                    node.key(), node.name(), position, components));
        }
        return List.copyOf(nodes);
    }

    private List<AiCapabilityDTO.FieldCapability> fields(List<WorkflowFieldDefinition> definitions) {
        if (definitions == null) return List.of();
        return definitions.stream().filter(Objects::nonNull).map(field -> new AiCapabilityDTO.FieldCapability(
                field.key(), field.label(), field.type() == null ? null : field.type().name(),
                field.required(), field.options(), field.visible(), field.binding(), field.fullWidth())).toList();
    }

    private AiCapabilityDTO.Viewer viewer() {
        Long userId = UserContext.userIdOrNull();
        if (userId == null) return null;
        return userService.listByIds(List.of(userId)).stream().findFirst().map(this::toViewer).orElse(null);
    }

    private AiCapabilityDTO.Viewer toViewer(UserDO user) {
        String displayName = Convertors.userDisplayName(user);
        if (displayName == null || displayName.isBlank()) displayName = user.getUsername();
        return new AiCapabilityDTO.Viewer(user.getId(), displayName, user.getUsername(), user.getEmail());
    }

    private boolean requiresContext(PmsCommandDescriptor descriptor) {
        Set<String> ids = Set.of("projectId", "nodeId", "topicId", "storyId", "requirementId",
                "iterationPlanId", "itemId", "taskId");
        return descriptor.parameters().keySet().stream().anyMatch(ids::contains);
    }

    private List<String> resourceTypes(CommandName name) {
        return switch (name) {
            case REQUIREMENT_CREATE, REQUIREMENT_UPDATE,
                 REQUIREMENT_EXECUTION_TARGET_LINK, REQUIREMENT_EXECUTION_TARGET_CHANGE,
                 REQUIREMENT_EXECUTION_TARGET_UNLINK -> List.of("requirement");
            case TOPIC_CREATE, TOPIC_UPDATE, TOPIC_PROJECT_LINK -> List.of("topic");
            case STORY_CREATE, STORY_UPDATE, STORY_TOPIC_LINK -> List.of("story");
            case DEVELOPMENT_ITEM_NODE_OWNER_UPDATE, DEVELOPMENT_ITEM_NODE_SCHEDULE_UPDATE,
                 DEVELOPMENT_ITEM_NODE_FIELD_UPDATE, DEVELOPMENT_ITEM_NODE_COMPLETE,
                 DEVELOPMENT_ITEM_TASK_CREATE -> List.of("topic", "story", "requirement");
            case ITERATION_PLAN_CREATE, ITERATION_PLAN_UPDATE,
                 ITERATION_PLAN_STORY_ADD, ITERATION_PLAN_STORY_REMOVE -> List.of("iteration_plan");
            case TASK_CREATE, TASK_ASSIGN, TASK_UPDATE -> List.of("task");
            case NODE_COMPLETE, NODE_FIELD_UPDATE, NODE_ROLLBACK,
                 NODE_OWNER_UPDATE, NODE_SCHEDULE_UPDATE -> List.of("project_node");
            case PROJECT_ARCHIVE, PROJECT_CREATE, PROJECT_DELETE, PROJECT_UPDATE,
                 FOLLOWER_ADD, FOLLOWER_REMOVE, MEMBER_ADD, MEMBER_REMOVE, BATCH_WRITE -> List.of("project");
        };
    }

    private Set<String> allConnectorScopes() {
        return Set.of(
                AiConnectorScopePolicy.QUERY_READ,
                AiConnectorScopePolicy.COMMAND_PREVIEW,
                AiConnectorScopePolicy.COMMAND_EXECUTE,
                AiConnectorScopePolicy.PROJECT_READ,
                AiConnectorScopePolicy.TASK_READ,
                AiConnectorScopePolicy.TASK_WRITE,
                AiConnectorScopePolicy.WORKFLOW_WRITE,
                AiConnectorScopePolicy.PROJECT_WRITE,
                AiConnectorScopePolicy.DEVELOPMENT_WRITE,
                AiConnectorScopePolicy.REQUIREMENT_WRITE,
                AiConnectorScopePolicy.ITERATION_WRITE);
    }

    private boolean scopeAvailable(String scope) {
        return !UserContext.isDshDelegation() || UserContext.hasDshScope(scope);
    }
}
