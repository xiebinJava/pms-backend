package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.dto.response.NodeTaskSummaryDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.service.RequirementManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Vertical slice for requirement/topic/story pure context reads. */
@ExtendWith(MockitoExtension.class)
class AiDevelopmentContextReaderTest {

    @Mock DevelopmentItemWorkflowService workflowService;
    @Mock RequirementManagementService requirementManagementService;
    @Mock AiDevelopmentComponentReader componentReader;
    @Mock AiContextAllowedActions allowedActionsRegistry;

    private final AiContextNodeSelector nodeSelector = new AiContextNodeSelector();
    private final AiContextWorkflowStateResolver stateResolver = new AiContextWorkflowStateResolver();
    private final AiContextReferenceProjector referenceProjector = org.mockito.Mockito.mock(
            AiContextReferenceProjector.class);
    private final AiContextReadPolicy referencePolicy = org.mockito.Mockito.mock(AiContextReadPolicy.class);
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1_760_000_000), java.time.ZoneOffset.UTC);

    private AiDevelopmentContextReader reader() {
        return new AiDevelopmentContextReader(workflowService, requirementManagementService,
                componentReader, nodeSelector, stateResolver, referenceProjector, referencePolicy,
                allowedActionsRegistry, clock);
    }

    private DevelopmentItemWorkflowNodeDTO node(Long id, int sort, Integer status, Integer version) {
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(id);
        node.setNodeKey("design");
        node.setName("设计");
        node.setSort(sort);
        node.setStatus(status);
        node.setVersion(version);
        node.setRuntimeComponents(List.of("requirement-node-workbench", "made-up-component"));
        node.setComponentConfigs(Map.of("made-up-component",
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                        .valueToTree(Map.<String, Object>of("variant", "x"))));
        node.setComponentConfigs(node.getComponentConfigs() == null ? Map.of() : node.getComponentConfigs());
        return node;
    }

    private DevelopmentItemWorkflowDetailDTO configured() {
        DevelopmentItemWorkflowDetailDTO detail = new DevelopmentItemWorkflowDetailDTO();
        detail.setItemType("REQUIREMENT");
        detail.setId(23L);
        detail.setTitle("跨端登录");
        detail.setTemplateVersionId(88L);
        detail.setTemplateVersionNo(2);
        detail.setWorkflowConfigured(true);
        detail.setWorkflowVersion(7);
        detail.setNodes(List.of(node(101L, 0, 0, 4), node(102L, 1, 1, 9)));
        detail.setNodeTaskSummaries(Map.of(102L, new NodeTaskSummaryDTO(3, 1, 2)));
        return detail;
    }

    @Test
    void requirementContextKeepsRealVersionsAndSelectedFacts() {
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(requirementManagementService.detailForContext(23L)).thenReturn(requirementDetail());
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L))
                .thenReturn(configured());
        lenient().when(componentReader.read(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(allowedActionsRegistry.developmentActions("requirement")).thenReturn(List.of("save_node"));
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AiWorkflowContextDTO context = reader().read(
                com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L, ContextReadOptions.legacy());

        assertThat(context.resourceType()).isEqualTo("requirement");
        assertThat(context.entityVersion()).isEqualTo(5);
        assertThat(context.currentNode().id()).isEqualTo(102L);
        assertThat(context.version()).isEqualTo(9);
        assertThat(context.meta().workflowInstanceVersion()).isEqualTo(7);
        assertThat(context.meta().workflowConfigured()).isTrue();
        assertThat(context.meta().workflowStatus()).isEqualTo("IN_PROGRESS");
        assertThat(context.meta().contextVersion()).isEqualTo("requirement:23:entity:5");
        assertThat(context.workflow().templateVersionId()).isEqualTo(88L);
        AiWorkflowContextDTO.Node selected = context.workflow().nodes().get(1);
        assertThat(selected.taskSummary()).isEqualTo(new NodeTaskSummaryDTO(3, 1, 2));
        assertThat(selected.permissions()).isNull();
        assertThat(selected.fieldValueVersions()).isNull();
        assertThat(context.meta().warnings()).isEmpty();
    }

    @Test
    void unconfiguredRequirementStaysUnconfiguredOnTheContract() {
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        DevelopmentItemWorkflowDetailDTO unconfigured = new DevelopmentItemWorkflowDetailDTO();
        unconfigured.setItemType("REQUIREMENT");
        unconfigured.setId(23L);
        unconfigured.setWorkflowConfigured(false);
        when(requirementManagementService.detailForContext(23L)).thenReturn(requirementDetail());
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L))
                .thenReturn(unconfigured);
        lenient().when(allowedActionsRegistry.developmentActions("requirement")).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AiWorkflowContextDTO context = reader().read(
                com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L, ContextReadOptions.legacy());

        assertThat(context.meta().workflowConfigured()).isFalse();
        assertThat(context.meta().workflowStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(context.currentNode()).isNull();
        assertThat(context.workflow()).isNull();
        assertThat(context.relations()).isEmpty();
    }

    @Test
    void focusedReplacesHistoricalValuesWithNotRequestedAndStripsComponentsDuplicate() {
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(requirementManagementService.detailForContext(23L)).thenReturn(requirementDetail());
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L))
                .thenReturn(configured());
        lenient().when(componentReader.read(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(allowedActionsRegistry.developmentActions("requirement")).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AiWorkflowContextDTO focused = reader().read(
                com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L,
                new ContextReadOptions("focused", 101L, null));

        assertThat(focused.meta().view()).isEqualTo("focused");
        assertThat(focused.meta().selectedNodeId()).isEqualTo(101L);
        assertThat(focused.workflow().nodes().get(1).fieldValues()).isEmpty();
        assertThat(focused.workflow().nodes().get(1).fieldValuesState()).isEqualTo("NOT_REQUESTED");
        assertThat(focused.workflow().nodes().get(1).taskSummary()).isNull();
    }

    private com.brad.pms.dto.response.RequirementListDTO requirementDetail() {
        com.brad.pms.dto.response.RequirementListDTO detail = new com.brad.pms.dto.response.RequirementListDTO();
        detail.setId(23L);
        detail.setTitle("跨端登录");
        detail.setStatus("ACTIVE");
        detail.setVersion(5);
        detail.setWorkflowConfigured(true);
        detail.setWorkflowStatus("IN_PROGRESS");
        detail.setTerminalStatus(null);
        return detail;
    }

    @Test
    void instanceWithoutNodesIsInconsistentInsteadOfUnconfigured() {
        DevelopmentItemWorkflowDetailDTO brokenInstance = new DevelopmentItemWorkflowDetailDTO();
        brokenInstance.setItemType("REQUIREMENT");
        brokenInstance.setId(23L);
        brokenInstance.setWorkflowConfigured(true);
        brokenInstance.setWorkflowVersion(7);
        when(requirementManagementService.detailForContext(23L)).thenReturn(requirementDetail());
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L))
                .thenReturn(brokenInstance);
        lenient().when(allowedActionsRegistry.developmentActions("requirement")).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AiWorkflowContextDTO context = reader().read(
                com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L, ContextReadOptions.legacy());

        // spec: 研发有实例无节点 → INCONSISTENT（保留事实），而不是 NOT_CONFIGURED。
        assertThat(context.meta().workflowStatus()).isEqualTo("INCONSISTENT");
        assertThat(context.meta().workflowInstanceVersion()).isEqualTo(7);
    }

    @Test
    void componentKeyWithoutSelectedNodeIsRejectedAs422() {
        DevelopmentItemWorkflowDetailDTO brokenInstance = new DevelopmentItemWorkflowDetailDTO();
        brokenInstance.setItemType("REQUIREMENT");
        brokenInstance.setId(23L);
        brokenInstance.setWorkflowConfigured(true);
        when(requirementManagementService.detailForContext(23L)).thenReturn(requirementDetail());
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L))
                .thenReturn(brokenInstance);
        lenient().when(allowedActionsRegistry.developmentActions("requirement")).thenReturn(List.of());
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        assertThatThrownBy(() -> reader().read(
                com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L,
                new ContextReadOptions("focused", null, "requirement-node-workbench")))
                .isInstanceOfSatisfying(com.brad.pms.common.exception.BusinessException.class, thrown -> {
                    assertThat(thrown.getCode()).isEqualTo(422);
                    assertThat(thrown.getMessage()).startsWith("NO_SELECTED_NODE:");
                });
    }

    @Test
    void emptyNodePublishesDirectCompletionContract() {
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(requirementManagementService.detailForContext(23L)).thenReturn(requirementDetail());
        DevelopmentItemWorkflowDetailDTO detail = configured();
        DevelopmentItemWorkflowNodeDTO empty = new DevelopmentItemWorkflowNodeDTO();
        empty.setId(109L);
        empty.setNodeKey("release");
        empty.setName("需求上线");
        empty.setSort(0);
        empty.setStatus(1);
        empty.setVersion(2);
        empty.setRuntimeComponents(null);
        empty.setFields(null);
        detail.setNodes(List.of(empty));
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L))
                .thenReturn(detail);
        lenient().when(componentReader.read(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(allowedActionsRegistry.developmentActions("requirement"))
                .thenReturn(List.of("development-item.node.complete-with-data"));
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));

        AiWorkflowContextDTO context = reader().read(
                com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L,
                new ContextReadOptions("focused", 109L, null));

        var json = JsonMapper.builder().addModule(new JavaTimeModule()).build().valueToTree(context);
        assertThat(json.at("/workflow/nodes/0/completionContract/command").asText())
                .isEqualTo("development-item.node.complete-with-data");
        assertThat(json.at("/workflow/nodes/0/completionContract/completionSupported").asBoolean())
                .isTrue();
        assertThat(json.at("/workflow/nodes/0/completionContract/writableFields").isArray())
                .isTrue();
    }

    @Test
    void requirementEntryPublishesRootSaveContractAndFocusedBoundFacts() {
        var field = new com.brad.pms.workflow.WorkflowFieldDefinition("description", "需求描述",
                com.brad.pms.workflow.WorkflowFieldType.TEXTAREA, true, List.of(), true, "requirement.description");
        var selected = node(109L, 0, 1, 2);
        selected.setRuntimeComponents(List.of());
        selected.setFields(List.of(field));
        selected.setBoundFieldValues(Map.of("description", JsonMapper.builder().build().valueToTree("已有描述")));
        var context = readSingleRequirementNode(selected);
        var json = JsonMapper.builder().addModule(new JavaTimeModule()).build().valueToTree(context);
        assertThat(json.at("/workflow/nodes/0/saveContract/fieldValuesPath").asText()).isEqualTo("$");
        assertThat(json.at("/workflow/nodes/0/saveContract/writableFields/0").asText()).isEqualTo("description");
        assertThat(json.at("/workflow/nodes/0/fieldValues/description").asText()).isEqualTo("已有描述");
    }

    @Test
    void requirementExecutionPublishesCompletionWithoutMakingTargetWritable() {
        var selected = node(109L, 0, 1, 2);
        selected.setRuntimeComponents(List.of("requirement-execution"));
        selected.setFields(List.of());
        var json = JsonMapper.builder().addModule(new JavaTimeModule()).build()
                .valueToTree(readSingleRequirementNode(selected));
        assertThat(json.at("/workflow/nodes/0/completionContract/command").asText())
                .isEqualTo("development-item.node.complete-with-data");
        assertThat(json.at("/workflow/nodes/0/completionContract/effectiveWritableFields").size()).isZero();
        assertThat(json.at("/workflow/nodes/0/completionContract/completionRequiredMetadata/0").asText())
                .isEqualTo("ownerId");
    }

    private AiWorkflowContextDTO readSingleRequirementNode(DevelopmentItemWorkflowNodeDTO selected) {
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(requirementManagementService.detailForContext(23L)).thenReturn(requirementDetail());
        var detail = configured();
        detail.setNodes(List.of(selected));
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L))
                .thenReturn(detail);
        lenient().when(componentReader.read(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(allowedActionsRegistry.developmentActions("requirement"))
                .thenReturn(List.of("development-item.node.field.update", "development-item.node.complete-with-data"));
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        return reader().read(com.brad.pms.workflow.DevelopmentItemType.REQUIREMENT, 23L,
                new ContextReadOptions("focused", selected.getId(), null));
    }

    @Test
    void topicContextPublishesProjectFactsAndReadableProjectRelation() {
        DevelopmentItemWorkflowDetailDTO topic = new DevelopmentItemWorkflowDetailDTO();
        topic.setItemType("TOPIC");
        topic.setId(12L);
        topic.setTitle("研发流程专题");
        topic.setProjectId(9L);
        topic.setProjectName("PMS 项目");
        topic.setProjectMountNodeKey("develop");
        topic.setWorkflowConfigured(true);
        topic.setWorkflowVersion(4);
        topic.setTemplateVersionId(77L);
        topic.setTemplateVersionNo(2);
        topic.setNodes(List.of(node(301L, 0, 1, 4)));
        when(workflowService.detailIfInitialized(com.brad.pms.workflow.DevelopmentItemType.TOPIC, 12L))
                .thenReturn(topic);
        lenient().when(referencePolicy.canReadReference("project", 9L)).thenReturn(true);
        lenient().when(referenceProjector.projectResourceDetails(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(referenceProjector.projectFieldValues(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(componentReader.read(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(allowedActionsRegistry.developmentActions("topic")).thenReturn(List.of());

        AiWorkflowContextDTO context = reader().read(
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 12L, ContextReadOptions.legacy());

        assertThat(context.resourceDetails())
                .containsEntry("projectId", 9L)
                .containsEntry("projectName", "PMS 项目")
                .containsEntry("sourceProjectNodeKey", "develop");
        assertThat(context.relations()).anySatisfy(relation -> {
            assertThat(relation.resourceType()).isEqualTo("project");
            assertThat(relation.resourceId()).isEqualTo(9L);
            assertThat(relation.readable()).isTrue();
        });
    }
}
