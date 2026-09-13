package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.dto.response.ProjectBoardDTO;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.entity.ProjectNodeAcceptanceDefectDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeRiskDO;
import com.brad.pms.mapper.ProjectNodeAcceptanceDefectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeRiskMapper;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProjectBoardService {
    private final ProjectService projectService;
    private final WorkflowTemplateService workflowService;
    private final ProjectNodeRiskMapper riskMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeAcceptanceDefectMapper defectMapper;
    private final Clock clock;

    @Autowired
    public ProjectBoardService(ProjectService projectService, WorkflowTemplateService workflowService,
            ProjectNodeRiskMapper riskMapper, ProjectNodeDevelopmentStoryMapper storyMapper,
            ProjectNodeAcceptanceDefectMapper defectMapper) {
        this(projectService, workflowService, riskMapper, storyMapper, defectMapper, Clock.systemDefaultZone());
    }

    ProjectBoardService(ProjectService projectService, WorkflowTemplateService workflowService,
            ProjectNodeRiskMapper riskMapper, ProjectNodeDevelopmentStoryMapper storyMapper,
            ProjectNodeAcceptanceDefectMapper defectMapper, Clock clock) {
        this.projectService = projectService;
        this.workflowService = workflowService;
        this.riskMapper = riskMapper;
        this.storyMapper = storyMapper;
        this.defectMapper = defectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProjectBoardDTO getBoard(Long orgUnitId) {
        // Capture once so a request crossing midnight never compares against two dates.
        ZonedDateTime generatedAt = ZonedDateTime.now(clock);
        var snapshot = projectService.loadReadableBoardProjects(orgUnitId);
        boolean allCompanyScope = projectService.hasAllCompanyProjectRead();
        if (snapshot.projects().isEmpty()) {
            return new ProjectBoardDTO(generatedAt.toLocalDate().toString(), generatedAt.toOffsetDateTime().toString(),
                    allCompanyScope, List.of());
        }

        var definitions = workflowService.getDefinitions(snapshot.projects().stream()
                .map(ProjectDTO::getWorkflowTemplateVersionId).collect(Collectors.toSet()));
        Map<Long, Map<String, WorkflowNodeDefinition>> definitionNodes = new HashMap<>();
        definitions.forEach((id, definition) -> definitionNodes.put(id, definition.nodes().stream()
                .collect(Collectors.toMap(WorkflowNodeDefinition::key, Function.identity()))));
        Map<Long, ProjectDTO> projects = snapshot.projects().stream().collect(Collectors.toMap(ProjectDTO::getId, Function.identity()));
        Map<Long, List<ProjectNodeDO>> nodesByProject = new HashMap<>();
        Map<Long, Long> riskNodes = new HashMap<>(), storyNodes = new HashMap<>(), acceptanceNodes = new HashMap<>();
        Set<Long> riskConfiguredProjects = new HashSet<>(), storyConfiguredProjects = new HashSet<>(), acceptanceConfiguredProjects = new HashSet<>();
        for (ProjectDTO project : snapshot.projects()) {
            var boundNodes = definitionNodes.get(project.getWorkflowTemplateVersionId());
            if (boundNodes == null) continue;
            if (hasComponent(boundNodes, WorkflowComponentKey.PLAN_RESOURCE_RISK)) riskConfiguredProjects.add(project.getId());
            if (hasComponent(boundNodes, WorkflowComponentKey.DEVELOPMENT_CONTROL)) storyConfiguredProjects.add(project.getId());
            if (hasComponent(boundNodes, WorkflowComponentKey.BUSINESS_ACCEPTANCE)) acceptanceConfiguredProjects.add(project.getId());
        }
        for (ProjectNodeDO node : snapshot.nodes()) {
            ProjectDTO project = projects.get(node.getProjectId());
            if (project == null || Boolean.TRUE.equals(node.getDeleted())) continue;
            nodesByProject.computeIfAbsent(project.getId(), ignored -> new ArrayList<>()).add(node);
            var definition = definitionNodes.get(project.getWorkflowTemplateVersionId()).get(node.getNodeKey());
            if (definition == null || node.getId() == null) continue;
            // runtimeComponents is authoritative for both legacy components and v2 contentOrder.
            var components = definition.runtimeComponents();
            if (components.contains(WorkflowComponentKey.PLAN_RESOURCE_RISK)) riskNodes.put(node.getId(), project.getId());
            if (components.contains(WorkflowComponentKey.DEVELOPMENT_CONTROL)) storyNodes.put(node.getId(), project.getId());
            if (components.contains(WorkflowComponentKey.BUSINESS_ACCEPTANCE)) acceptanceNodes.put(node.getId(), project.getId());
        }
        var risks = riskNodes.isEmpty() ? List.<ProjectNodeRiskDO>of() : riskMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeRiskDO>()
                        .in(ProjectNodeRiskDO::getProjectId, new HashSet<>(riskNodes.values()))
                        .in(ProjectNodeRiskDO::getNodeId, riskNodes.keySet()));
        var stories = storyNodes.isEmpty() ? List.<ProjectNodeDevelopmentStoryDO>of() : storyMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                        .in(ProjectNodeDevelopmentStoryDO::getProjectId, new HashSet<>(storyNodes.values()))
                        .in(ProjectNodeDevelopmentStoryDO::getNodeId, storyNodes.keySet()));
        var defects = acceptanceNodes.isEmpty() ? List.<ProjectNodeAcceptanceDefectDO>of() : defectMapper.selectList(
                new LambdaQueryWrapper<ProjectNodeAcceptanceDefectDO>()
                        .in(ProjectNodeAcceptanceDefectDO::getProjectId, new HashSet<>(acceptanceNodes.values()))
                        .in(ProjectNodeAcceptanceDefectDO::getNodeId, acceptanceNodes.keySet()));
        var risksByProject = groupConfigured(risks, riskNodes, riskConfiguredProjects, ProjectNodeRiskDO::getProjectId, ProjectNodeRiskDO::getNodeId);
        var storiesByProject = groupConfigured(stories, storyNodes, storyConfiguredProjects, ProjectNodeDevelopmentStoryDO::getProjectId, ProjectNodeDevelopmentStoryDO::getNodeId);
        var defectsByProject = groupConfigured(defects, acceptanceNodes, acceptanceConfiguredProjects, ProjectNodeAcceptanceDefectDO::getProjectId, ProjectNodeAcceptanceDefectDO::getNodeId);
        List<ProjectBoardDTO.ProjectRow> rows = snapshot.projects().stream().map(project -> ProjectBoardCalculator.summarize(
                project, nodesByProject.getOrDefault(project.getId(), List.of()), risksByProject.get(project.getId()),
                storiesByProject.get(project.getId()), defectsByProject.get(project.getId()), generatedAt.toLocalDate())).toList();
        return new ProjectBoardDTO(generatedAt.toLocalDate().toString(), generatedAt.toOffsetDateTime().toString(), allCompanyScope, rows);
    }

    private static boolean hasComponent(Map<String, WorkflowNodeDefinition> nodes, String componentKey) {
        return nodes.values().stream().anyMatch(node -> node.runtimeComponents().contains(componentKey));
    }

    private static <T> Map<Long, List<T>> groupConfigured(List<T> records, Map<Long, Long> nodeProjects,
            Set<Long> configuredProjects, Function<T, Long> projectId, Function<T, Long> nodeId) {
        Map<Long, List<T>> result = new HashMap<>();
        configuredProjects.forEach(id -> result.put(id, new ArrayList<>()));
        for (T record : records) {
            Long expectedProject = nodeProjects.get(nodeId.apply(record));
            if (expectedProject != null && expectedProject.equals(projectId.apply(record))) result.get(expectedProject).add(record);
        }
        return result;
    }
}
