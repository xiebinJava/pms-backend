package com.brad.pms.service;

import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowComponentBindingServiceTest {
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;

    @Test
    void derivesTheConfiguredNodeComponentWithoutMutatingTheStoredDefinition() {
        ProjectDO project = project(1L);
        ProjectNodeDO configured = node(10L, 1L, "topic-host");
        WorkflowNodeDefinition storedNode = nodeDefinition("topic-host", List.of());
        WorkflowTemplateDefinition stored = new WorkflowTemplateDefinition(1, List.of(storedNode));
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("topic-host");
        when(topicMapper.selectList(any())).thenReturn(List.of());

        WorkflowTemplateDefinition effective = apply(project, stored, List.of(configured));

        assertThat(effective.nodes().get(0).runtimeComponents()).containsExactly(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        assertThat(stored.nodes().get(0).runtimeComponents()).isEmpty();
    }

    @Test
    void resolvesTheTopicHostFromTheProjectsPinnedWorkflowVersion() {
        ProjectDO project = project(1L);
        project.setWorkflowTemplateVersionId(42L);
        ProjectNodeDO global = node(10L, 1L, "global-topic-host");
        ProjectNodeDO pinned = node(11L, 1L, "pinned-topic-host");
        WorkflowTemplateDefinition stored = new WorkflowTemplateDefinition(1, List.of(
                nodeDefinition("global-topic-host", List.of()),
                nodeDefinition("pinned-topic-host", List.of())));
        when(workflowTemplateService.resolveTopicSourceProjectNodeKeyForRuntime(42L)).thenReturn("pinned-topic-host");
        when(topicMapper.selectList(any())).thenReturn(List.of());

        WorkflowTemplateDefinition effective = apply(project, stored, List.of(global, pinned));

        assertThat(components(effective, "global-topic-host")).doesNotContain(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        assertThat(components(effective, "pinned-topic-host")).contains(WorkflowComponentKey.DEVELOPMENT_CONTROL);
    }

    @Test
    void keepsTheComponentOnHistoricalNodesWithActiveOrDeletedTopicsOnly() {
        ProjectDO project = project(1L);
        ProjectNodeDO current = node(10L, 1L, "new-topic-host");
        ProjectNodeDO historical = node(11L, 1L, "old-topic-host");
        ProjectNodeDO unrelated = node(12L, 1L, "unrelated");
        ProjectNodeDevelopmentTopicDO activeTopic = topic(20L, 11L, false);
        ProjectNodeDevelopmentTopicDO deletedTopic = topic(21L, 11L, true);
        WorkflowTemplateDefinition stored = new WorkflowTemplateDefinition(1, List.of(
                nodeDefinition("new-topic-host", List.of()),
                nodeDefinition("old-topic-host", List.of()),
                nodeDefinition("unrelated", List.of())));
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("new-topic-host");
        when(topicMapper.selectList(any())).thenReturn(List.of(activeTopic, deletedTopic));

        WorkflowTemplateDefinition effective = apply(project, stored, List.of(current, historical, unrelated));

        assertThat(components(effective, "new-topic-host")).contains(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        assertThat(components(effective, "old-topic-host")).contains(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        assertThat(components(effective, "unrelated")).doesNotContain(WorkflowComponentKey.DEVELOPMENT_CONTROL);
    }

    @Test
    void overlaysV2ContentOrderWithoutDuplicatesAndPreservesItsOrder() {
        ProjectDO project = project(1L);
        ProjectNodeDO configured = node(10L, 1L, "topic-host");
        WorkflowNodeDefinition v2Node = new WorkflowNodeDefinition("topic-host", "专题宿主", "", "", "", null,
                List.of(), false, null, List.of("fields", "component:solution-design"));
        WorkflowTemplateDefinition stored = new WorkflowTemplateDefinition(2, List.of(v2Node));
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("topic-host");
        when(topicMapper.selectList(any())).thenReturn(List.of());

        WorkflowTemplateDefinition effective = apply(project, stored, List.of(configured));

        assertThat(effective.nodes().get(0).contentOrder()).containsExactly(
                "fields", "component:solution-design", "component:development-control");
        assertThat(effective.nodes().get(0).runtimeComponents()).containsExactly(
                WorkflowComponentKey.SOLUTION_DESIGN, WorkflowComponentKey.DEVELOPMENT_CONTROL);
        assertThat(stored.nodes().get(0).contentOrder()).containsExactly("fields", "component:solution-design");
    }

    @Test
    void doesNotDuplicateAComponentAlreadyPresentInEitherSchema() {
        ProjectDO project = project(1L);
        ProjectNodeDO configured = node(10L, 1L, "topic-host");
        WorkflowTemplateDefinition v1 = new WorkflowTemplateDefinition(1,
                List.of(nodeDefinition("topic-host", List.of(WorkflowComponentKey.DEVELOPMENT_CONTROL))));
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("topic-host");
        when(topicMapper.selectList(any())).thenReturn(List.of());

        WorkflowTemplateDefinition effectiveV1 = apply(project, v1, List.of(configured));

        assertThat(effectiveV1.nodes().get(0).runtimeComponents())
                .containsExactly(WorkflowComponentKey.DEVELOPMENT_CONTROL);
    }

    @Test
    void derivesStorySplitOnlyOnThePinnedTopicMountNodeWithoutMutatingTheSnapshot() {
        WorkflowTemplateDefinition stored = new WorkflowTemplateDefinition(2, List.of(
                new WorkflowNodeDefinition("research", "需求调研", "", "", "", null,
                        List.of(), false, List.of(), List.of("fields")),
                new WorkflowNodeDefinition("story-host", "故事承接", "", "", "", null,
                        List.of(), false, List.of(), List.of("fields"))));

        WorkflowTemplateDefinition effective = new WorkflowComponentBindingService(
                workflowTemplateService, topicMapper).applyStoryBinding(stored, "story-host");

        assertThat(effective.nodes().get(0).runtimeComponents()).isEmpty();
        assertThat(effective.nodes().get(1).runtimeComponents())
                .containsExactly(WorkflowComponentKey.STORY_SPLIT);
        assertThat(stored.nodes().get(1).runtimeComponents()).isEmpty();
    }

    @Test
    void doesNotDuplicateStorySplitWhenThePinnedSnapshotAlreadyContainsIt() {
        WorkflowTemplateDefinition stored = new WorkflowTemplateDefinition(1, List.of(
                nodeDefinition("story-host", List.of(WorkflowComponentKey.STORY_SPLIT))));

        WorkflowTemplateDefinition effective = new WorkflowComponentBindingService(
                workflowTemplateService, topicMapper).applyStoryBinding(stored, "story-host");

        assertThat(effective.nodes().get(0).runtimeComponents())
                .containsExactly(WorkflowComponentKey.STORY_SPLIT);
    }

    private WorkflowTemplateDefinition apply(ProjectDO project, WorkflowTemplateDefinition definition,
                                             Collection<ProjectNodeDO> nodes) {
        try {
            Class<?> type = Class.forName("com.brad.pms.service.WorkflowComponentBindingService");
            Constructor<?> constructor = type.getConstructor(WorkflowTemplateService.class,
                    ProjectNodeDevelopmentTopicMapper.class);
            Object service = constructor.newInstance(workflowTemplateService, topicMapper);
            Method apply = java.util.Arrays.stream(type.getMethods())
                    .filter(method -> method.getName().equals("applyTopicBinding") && method.getParameterCount() == 3)
                    .findFirst().orElseThrow();
            return (WorkflowTemplateDefinition) apply.invoke(service, project, definition, nodes);
        } catch (ClassNotFoundException e) {
            return fail("WorkflowComponentBindingService must derive the runtime project-node component overlay", e);
        } catch (ReflectiveOperationException e) {
            return fail("WorkflowComponentBindingService must expose the planned topic-binding resolver", e);
        }
    }

    private static List<String> components(WorkflowTemplateDefinition definition, String key) {
        return definition.nodes().stream().filter(node -> node.key().equals(key)).findFirst().orElseThrow()
                .runtimeComponents();
    }

    private static WorkflowNodeDefinition nodeDefinition(String key, List<String> components) {
        return new WorkflowNodeDefinition(key, key, "", "", "", components, List.of(), false, List.of());
    }

    private static ProjectDO project(Long id) {
        ProjectDO project = new ProjectDO();
        project.setId(id);
        return project;
    }

    private static ProjectNodeDO node(Long id, Long projectId, String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(projectId);
        node.setNodeKey(key);
        return node;
    }

    private static ProjectNodeDevelopmentTopicDO topic(Long id, Long nodeId, boolean deleted) {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(id);
        topic.setProjectId(1L);
        topic.setNodeId(nodeId);
        topic.setDeleted(deleted);
        return topic;
    }
}
