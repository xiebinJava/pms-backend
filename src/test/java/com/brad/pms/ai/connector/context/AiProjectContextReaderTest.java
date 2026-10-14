package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.response.NodePermissionsDTO;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.dto.response.PersistedContentVersion;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.dto.response.WorkbenchReadSnapshot;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.TaskService;
import com.brad.pms.service.WorkflowTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Vertical slice of the project pipeline with actual nodes and saved facts. */
@ExtendWith(MockitoExtension.class)
class AiProjectContextReaderTest {

    @Mock ProjectService projectService;
    @Mock NodeService nodeService;
    @Mock TaskService taskService;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock AiProjectComponentReader componentReader;
    @Mock AiContextAllowedActions allowedActionsRegistry;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;

    private final AiContextNodeSelector nodeSelector = new AiContextNodeSelector();
    private final AiContextWorkflowStateResolver stateResolver = new AiContextWorkflowStateResolver();
    private final AiContextReferenceProjector referenceProjector = mock(AiContextReferenceProjector.class);
    private final AiContextReadPolicy referencePolicy = mock(AiContextReadPolicy.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_760_000_000), java.time.ZoneOffset.UTC);

    private AiProjectContextReader reader() {
        return new AiProjectContextReader(projectService, nodeService, taskService, workflowTemplateService,
                componentReader, nodeSelector, stateResolver, referenceProjector, referencePolicy,
                allowedActionsRegistry, topicMapper, clock);
    }

    private ProjectDTO project() {
        ProjectDTO project = new ProjectDTO();
        project.setId(7L);
        project.setVersion(3);
        project.setName("订单中心");
        project.setWorkflowTemplateVersionId(88L);
        return project;
    }

    private ProjectNodeDTO node(Long id, String key, int sort, Integer status) {
        ProjectNodeDTO node = new ProjectNodeDTO();
        node.setId(id);
        node.setProjectId(7L);
        node.setNodeKey(key);
        node.setName(key);
        node.setSort(sort);
        node.setStatus(status);
        node.setVersion(1);
        node.setProjectBasicInfo(true);
        node.setComponents(java.util.List.of("project-basic-info"));
        node.setFieldValues(java.util.Map.of("kickoff.summary",
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("项目从订单域开始")));
        node.setFieldValueVersions(Map.of("kickoff.summary", 2));
        node.setPermissions(new NodePermissionsDTO());
        return node;
    }

    @Test
    void actualNodesDrivePositionCurrentAndSelectedFacts() {
        when(projectService.detail(7L)).thenReturn(project());
        when(nodeService.listReadOnly(7L)).thenReturn(List.of(
                node(301L, "kickoff", 0, 0), node(299L, "design", 1, 1)));
        when(workflowTemplateService.getVersionNo(88L)).thenReturn(4);
        com.fasterxml.jackson.databind.JsonNode basicContent =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .valueToTree(java.util.Collections.<String, Object>singletonMap("name", "订单中心"));
        when(componentReader.read(any(), any(), any()))
                .thenReturn(List.of(new AiWorkflowContextDTO.Component("project-basic-info",
                        "project-basic-info", List.of(), List.of(), null,
                        basicContent,
                        "LOADED", Map.of("content", 3), null)));
        when(taskService.summarizeByNode(7L, 299L)).thenReturn(new com.brad.pms.dto.response.NodeTaskSummaryDTO(2, 1, 1));
        lenient().when(topicMapper.selectList(any())).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(allowedActionsRegistry.projectActions()).thenReturn(List.of("close_node"));

        AiWorkflowContextDTO context = reader().read(7L, ContextReadOptions.legacy());

        assertThat(context.workflow().nodes()).extracting(AiWorkflowContextDTO.Node::id)
                .containsExactly(301L, 299L);
        assertThat(context.workflow().nodes()).extracting(AiWorkflowContextDTO.Node::position)
                .containsExactly(0, 1);
        assertThat(context.currentNode().id()).isEqualTo(299L);
        assertThat(context.version()).isEqualTo(3);
        AiWorkflowContextDTO.Node selected = context.workflow().nodes().get(1);
        assertThat(selected.fieldValuesState()).isEqualTo("LOADED");
        assertThat(selected.taskSummary()).isEqualTo(new com.brad.pms.dto.response.NodeTaskSummaryDTO(2, 1, 1));
        assertThat(selected.permissions()).isNotNull();
        assertThat(context.meta().workflowStatus()).isEqualTo("IN_PROGRESS");
        assertThat(context.meta().loadedAt()).isEqualTo(Instant.ofEpochSecond(1_760_000_000));
        assertThat(context.meta().businessTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(context.meta().view()).isEqualTo("legacy");
        assertThat(context.meta().fieldValuesScope()).isEqualTo("all-nodes");
        assertThat(context.meta().formatVersion()).isEqualTo(2);
        assertThat(context.meta().pureRead()).isTrue();
    }

    @Test
    void legacyKeepsRealFieldValuesWhileFocusedStripsUnselectedNodes() {
        when(projectService.detail(7L)).thenReturn(project());
        when(nodeService.listReadOnly(7L)).thenReturn(List.of(
                node(301L, "kickoff", 0, 0), node(299L, "design", 1, 1)));
        when(workflowTemplateService.getVersionNo(88L)).thenReturn(4);
        when(componentReader.read(any(), any(), any())).thenReturn(List.of());
        lenient().when(taskService.summarizeByNode(any(), any()))
                .thenReturn(new com.brad.pms.dto.response.NodeTaskSummaryDTO(0, 0, 0));
        lenient().when(topicMapper.selectList(any())).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(allowedActionsRegistry.projectActions()).thenReturn(List.of());

        AiWorkflowContextDTO legacy = reader().read(7L, ContextReadOptions.legacy());
        assertThat(legacy.workflow().nodes().get(0).fieldValues())
                .containsEntry("kickoff.summary",
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("项目从订单域开始"));
        assertThat(legacy.workflow().nodes().get(0).fieldValueVersions())
                .containsEntry("kickoff.summary", 2);
        assertThat(legacy.workflow().nodes().get(1).fieldValues()).isNotEmpty();
        assertThat(legacy.workflow().nodes().get(1).fieldValuesState()).isEqualTo("LOADED");
        assertThat(legacy.workflow().nodes().get(1).fieldValueVersions()).isNotNull();

        AiWorkflowContextDTO focused = reader().read(7L, new ContextReadOptions("focused", 301L, null));
        assertThat(focused.workflow().nodes().get(1).fieldValues()).isEmpty();
        assertThat(focused.workflow().nodes().get(1).fieldValuesState()).isEqualTo("NOT_REQUESTED");
        assertThat(focused.workflow().nodes().get(1).fieldValueVersions()).isNull();
        assertThat(focused.workflow().nodes().get(0).fieldValues())
                .containsEntry("kickoff.summary",
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.textNode("项目从订单域开始"));
        assertThat(focused.meta().fieldValuesScope()).isEqualTo("selected-node");
        assertThat(focused.meta().componentContentScope()).isEqualTo("selected-node");
        assertThat(focused.meta().selectedNodeId()).isEqualTo(301L);
    }

    @Test
    void aProjectWithoutNodesIsNotConfiguredAndExplicitNodeIdIsRejected() {
        when(projectService.detail(7L)).thenReturn(project());
        when(nodeService.listReadOnly(7L)).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(allowedActionsRegistry.projectActions()).thenReturn(List.of());

        AiWorkflowContextDTO empty = reader().read(7L, ContextReadOptions.legacy());
        assertThat(empty.workflow()).isNull();
        assertThat(empty.meta().workflowConfigured()).isFalse();
        assertThat(empty.meta().workflowStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(empty.currentNode()).isNull();
    }

    @Test
    void componentsWithoutContentOnlyExistOnTheSelectedNode() {
        when(projectService.detail(7L)).thenReturn(project());
        when(nodeService.listReadOnly(7L)).thenReturn(List.of(
                node(301L, "kickoff", 0, 1), node(299L, "design", 1, 0)));
        when(workflowTemplateService.getVersionNo(88L)).thenReturn(4);
        when(componentReader.read(any(), any(), any())).thenReturn(List.of(
                new AiWorkflowContextDTO.Component("project-basic-info", "project-basic-info",
                        List.of(), List.of(), null, null, "EMPTY", null, null)));
        lenient().when(topicMapper.selectList(any())).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(allowedActionsRegistry.projectActions()).thenReturn(List.of());

        AiWorkflowContextDTO context = reader().read(7L, ContextReadOptions.legacy());

        assertThat(context.workflow().nodes().get(1).components()).isNotEmpty();
        AiWorkflowContextDTO.Node unselected = context.workflow().nodes().get(1);
        assertThat(unselected.components()).singleElement().satisfies(component -> {
            assertThat(component.contentState()).isEqualTo("NOT_REQUESTED");
            assertThat(component.content()).isNull();
        });
        AiWorkflowContextDTO.Node selected = context.workflow().nodes().get(0);
        assertThat(selected.components()).singleElement().satisfies(component ->
                assertThat(component.contentState()).isEqualTo("EMPTY"));
    }

    @Test
    void unresolvedLegacyNodeConfigurationWarnsInsteadOfFabricatingValues() {
        when(projectService.detail(7L)).thenReturn(project());
        ProjectNodeDTO unknown = node(301L, "mystery-node", 0, 1);
        unknown.setProjectBasicInfo(null);
        unknown.setFieldValues(new java.util.HashMap<>(node(301L, "mystery", 0, 1).getFieldValues()));
        when(nodeService.listReadOnly(7L)).thenReturn(List.of(unknown));
        when(workflowTemplateService.getVersionNo(88L)).thenReturn(null);
        when(componentReader.read(any(), any(), any())).thenReturn(List.of());
        lenient().when(topicMapper.selectList(any())).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(allowedActionsRegistry.projectActions()).thenReturn(List.of());

        AiWorkflowContextDTO context = reader().read(7L, ContextReadOptions.legacy());

        AiWorkflowContextDTO.Node contractNode = context.workflow().nodes().get(0);
        assertThat(contractNode.fieldValuesState()).isEqualTo("UNAVAILABLE");
        assertThat(contractNode.fieldValues()).isEmpty();
        assertThat(contractNode.id()).isEqualTo(301L);
        assertThat(context.meta().warnings()).extracting(AiWorkflowContextDTO.Warning::code)
                .contains("UNRESOLVED_NODE_CONFIG");
    }

    @Test
    void relationsCarryOnlyReadableTopicReferences() {
        when(projectService.detail(7L)).thenReturn(project());
        when(nodeService.listReadOnly(7L)).thenReturn(List.of());
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        ProjectNodeDevelopmentTopicDO readable = new ProjectNodeDevelopmentTopicDO();
        readable.setId(90L);
        readable.setTitle("可读专题");
        ProjectNodeDevelopmentTopicDO unreadable = new ProjectNodeDevelopmentTopicDO();
        unreadable.setId(91L);
        unreadable.setTitle("不可读专题");
        when(topicMapper.selectList(any())).thenReturn(List.of(readable, unreadable));
        when(referencePolicy.canReadReference("topic", 90L)).thenReturn(true);
        when(referencePolicy.canReadReference("topic", 91L)).thenReturn(false);
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(allowedActionsRegistry.projectActions()).thenReturn(List.of());

        AiWorkflowContextDTO context = reader().read(7L, ContextReadOptions.legacy());

        assertThat(context.relations()).singleElement().satisfies(relation -> {
            assertThat(relation.resourceType()).isEqualTo("topic");
            assertThat(relation.resourceId()).isEqualTo(90L);
            assertThat(relation.label()).isEqualTo("可读专题");
            assertThat(relation.readable()).isTrue();
        });
        assertThat(context.meta().referencesScope()).isEqualTo("readable-only");
        assertThat(context.meta().permissionsScope()).isEqualTo("business-user");
    }
}
