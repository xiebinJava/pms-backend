package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Resolves workflow components derived by configuration without rewriting pinned template versions. */
@Service
@RequiredArgsConstructor
public class WorkflowComponentBindingService {

    private final WorkflowTemplateService workflowTemplateService;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;

    public WorkflowTemplateDefinition applyTopicBinding(ProjectDO project, WorkflowTemplateDefinition definition,
                                                        Collection<ProjectNodeDO> projectNodes) {
        if (project == null || definition == null || project.getId() == null) return definition;
        List<ProjectNodeDevelopmentTopicDO> topics = loadTopicRoots(List.of(project.getId()));
        String configuredNodeKey = workflowTemplateService.resolveTopicSourceProjectNodeKey();
        return overlay(project.getId(), definition, projectNodes, topics, configuredNodeKey);
    }

    /** Applies the same overlay to a whole project snapshot with one historical-topic lookup. */
    public Map<Long, WorkflowTemplateDefinition> applyTopicBindings(Collection<ProjectDO> projects,
                                                                    Map<Long, WorkflowTemplateDefinition> definitions,
                                                                    Collection<ProjectNodeDO> projectNodes) {
        if (projects == null || projects.isEmpty()) return Map.of();
        Map<Long, Long> versionIdsByProjectId = new HashMap<>();
        for (ProjectDO project : projects) {
            if (project != null && project.getId() != null) {
                versionIdsByProjectId.putIfAbsent(project.getId(), project.getWorkflowTemplateVersionId());
            }
        }
        return applyTopicBindings(versionIdsByProjectId, definitions, projectNodes);
    }

    /** Batch variant keyed by project id, suitable for read snapshots already projected to DTOs. */
    public Map<Long, WorkflowTemplateDefinition> applyTopicBindings(Map<Long, Long> versionIdsByProjectId,
                                                                    Map<Long, WorkflowTemplateDefinition> definitions,
                                                                    Collection<ProjectNodeDO> projectNodes) {
        if (versionIdsByProjectId == null || versionIdsByProjectId.isEmpty()) return Map.of();
        List<Long> projectIds = versionIdsByProjectId.keySet().stream().filter(Objects::nonNull).distinct().toList();
        List<ProjectNodeDevelopmentTopicDO> topics = projectIds.isEmpty() ? List.of() : loadTopicRoots(projectIds);
        Map<Long, List<ProjectNodeDevelopmentTopicDO>> topicsByProject = topics.stream()
                .collect(Collectors.groupingBy(ProjectNodeDevelopmentTopicDO::getProjectId));
        Map<Long, List<ProjectNodeDO>> nodesByProject = (projectNodes == null ? List.<ProjectNodeDO>of() : projectNodes)
                .stream().filter(node -> node.getProjectId() != null)
                .collect(Collectors.groupingBy(ProjectNodeDO::getProjectId));
        String configuredNodeKey = workflowTemplateService.resolveTopicSourceProjectNodeKey();
        Map<Long, WorkflowTemplateDefinition> effective = new HashMap<>();
        for (Map.Entry<Long, Long> projectEntry : versionIdsByProjectId.entrySet()) {
            Long projectId = projectEntry.getKey();
            if (projectId == null) continue;
            WorkflowTemplateDefinition definition = definitions == null ? null
                    : definitions.get(projectEntry.getValue());
            effective.put(projectId, overlay(projectId, definition,
                    nodesByProject.getOrDefault(projectId, List.of()),
                    topicsByProject.getOrDefault(projectId, List.of()), configuredNodeKey));
        }
        return effective;
    }

    public boolean topicCreationAllowed(ProjectNodeDO node) {
        return node != null
                && Objects.equals(node.getNodeKey(), workflowTemplateService.resolveTopicSourceProjectNodeKey());
    }

    private List<ProjectNodeDevelopmentTopicDO> loadTopicRoots(Collection<Long> projectIds) {
        List<ProjectNodeDevelopmentTopicDO> topics = topicMapper.selectList(new QueryWrapper<ProjectNodeDevelopmentTopicDO>()
                .select("project_id", "node_id")
                .in("project_id", projectIds));
        return topics == null ? List.of() : topics;
    }

    private WorkflowTemplateDefinition overlay(Long projectId, WorkflowTemplateDefinition definition,
                                               Collection<ProjectNodeDO> projectNodes,
                                               Collection<ProjectNodeDevelopmentTopicDO> topics,
                                               String configuredNodeKey) {
        if (definition == null) return null;
        Set<String> hostKeys = new HashSet<>();
        if (projectNodes != null) {
            Map<Long, String> nodeKeysById = projectNodes.stream()
                    .filter(node -> node.getId() != null && Objects.equals(node.getProjectId(), projectId))
                    .collect(Collectors.toMap(ProjectNodeDO::getId, ProjectNodeDO::getNodeKey, (left, right) -> left));
            for (ProjectNodeDO node : projectNodes) {
                if (Objects.equals(node.getProjectId(), projectId)
                        && Objects.equals(node.getNodeKey(), configuredNodeKey)) {
                    hostKeys.add(node.getNodeKey());
                }
            }
            if (topics != null) {
                for (ProjectNodeDevelopmentTopicDO topic : topics) {
                    if (topic != null && topic.getNodeId() != null) {
                        String historicNodeKey = nodeKeysById.get(topic.getNodeId());
                        if (historicNodeKey != null) hostKeys.add(historicNodeKey);
                    }
                }
            }
        }
        if (hostKeys.isEmpty()) return definition;

        List<WorkflowNodeDefinition> effectiveNodes = new ArrayList<>(definition.nodes().size());
        boolean changed = false;
        for (WorkflowNodeDefinition node : definition.nodes()) {
            if (hostKeys.contains(node.key()) && !node.runtimeComponents().contains(WorkflowComponentKey.DEVELOPMENT_CONTROL)) {
                effectiveNodes.add(withDevelopmentControl(node));
                changed = true;
            } else {
                effectiveNodes.add(node);
            }
        }
        return changed ? new WorkflowTemplateDefinition(definition.schemaVersion(), effectiveNodes,
                definition.sourceProjectNodeKey(), definition.sourceTopicNodeKey()) : definition;
    }

    private WorkflowNodeDefinition withDevelopmentControl(WorkflowNodeDefinition node) {
        List<String> components = node.components() == null ? null : new ArrayList<>(node.components());
        List<String> contentOrder = node.contentOrder() == null ? null : new ArrayList<>(node.contentOrder());
        if (contentOrder == null) {
            components = components == null ? new ArrayList<>() : components;
            components.add(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        } else {
            contentOrder.add("component:" + WorkflowComponentKey.DEVELOPMENT_CONTROL);
        }
        return new WorkflowNodeDefinition(node.key(), node.name(), node.description(), node.deliverable(), node.roles(),
                components, node.fields(), node.projectBasicInfo(), node.projectBasicInfoFields(), contentOrder);
    }
}
