package com.brad.pms.ai.command.node;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.node.NodeFieldLabels;
import com.brad.pms.ai.node.NodeWorkbenchRegistry;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.dto.response.NodeReleaseDTO;
import com.brad.pms.dto.request.NodeSolutionPackageUpdateCmd;
import com.brad.pms.dto.response.NodeSolutionDesignDTO;
import com.brad.pms.dto.response.NodeSolutionPackageDTO;
import com.brad.pms.dto.response.WorkflowNodeFieldValuesDTO;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.service.NodeAcceptanceService;
import com.brad.pms.service.NodeCustomFieldService;
import com.brad.pms.service.NodeDevelopmentControlService;
import com.brad.pms.service.NodeKnowledgeStandardService;
import com.brad.pms.service.NodePlanResourceRiskService;
import com.brad.pms.service.NodeReleaseService;
import com.brad.pms.service.NodeRequirementScopeService;
import com.brad.pms.service.NodeSolutionDesignService;
import com.brad.pms.service.NodeValueReviewService;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.service.ProjectPermissionService;
import com.brad.pms.service.WorkflowTemplateService;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class NodeFieldUpdateCommandTest {

    @Mock NodeRequirementScopeService requirementScope;
    @Mock NodeSolutionDesignService solutionDesign;
    @Mock NodePlanResourceRiskService planResourceRisk;
    @Mock NodeDevelopmentControlService developmentControl;
    @Mock NodeAcceptanceService acceptance;
    @Mock NodeReleaseService release;
    @Mock NodeValueReviewService valueReview;
    @Mock NodeKnowledgeStandardService knowledgeStandard;
    @Mock NodeCustomFieldService customField;
    @Mock ProjectPermissionService permissionService;
    @Mock WorkflowTemplateService workflowTemplateService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NodeFieldUpdateCommand command;

    @BeforeEach
    void setUp() {
        NodeWorkbenchRegistry registry = new NodeWorkbenchRegistry(
                requirementScope, solutionDesign, planResourceRisk, developmentControl,
                acceptance, release, valueReview, knowledgeStandard, customField,
                objectMapper, new NodeFieldLabels());
        command = new NodeFieldUpdateCommand(registry, permissionService, workflowTemplateService, objectMapper);
        lenient().when(permissionService.requireProjectManageable(eq(22L), any(String.class))).thenReturn(project(3));
        lenient().when(permissionService.requireNode(eq(22L), any(Long.class))).thenReturn(node(7L, "release"));
        lenient().when(workflowTemplateService.getNodeDefinition(any(), any(String.class)))
                .thenReturn(new WorkflowNodeDefinition("release", "发布决策与运营交接", "d", "x", "r",
                        java.util.List.of("release-handover"), java.util.List.of(), false, java.util.List.of()));
    }

    @Test
    void previewsOnlyThePatchedFieldsWithChineseLabels() {
        when(release.get(22L, 7L)).thenReturn(releaseDocument());

        CommandPreview preview = command.preview(new CommandPreviewRequest(CommandName.NODE_FIELD_UPDATE, Map.of(
                "projectId", 22L,
                "nodeId", 7L,
                "workbench", "release-handover",
                "fields", Map.of("handoverNotes", "已完成运维交接")), "project-detail", "v1"));

        assertThat(preview.changes()).singleElement().satisfies(change -> {
            assertThat(change).containsEntry("entity", "node-workbench");
            assertThat(change).containsEntry("workbench", "release-handover");
            assertThat(change).containsEntry("workbenchVersion", 2);
            assertThat(change).containsEntry("projectVersion", 3);
            assertThat((List<?>) change.get("fields")).singleElement().satisfies(raw -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> diff = (Map<String, Object>) raw;
                assertThat(diff).containsEntry("field", "handoverNotes");
                assertThat(diff).containsEntry("label", "交接说明");
                assertThat(diff).containsEntry("from", "旧交接说明");
                assertThat(diff).containsEntry("to", "已完成运维交接");
            });
        });
    }

    @Test
    void rejectsAFieldTheWorkbenchDoesNotDefine() {
        when(release.get(22L, 7L)).thenReturn(releaseDocument());

        assertThatThrownBy(() -> command.preview(new CommandPreviewRequest(CommandName.NODE_FIELD_UPDATE, Map.of(
                "projectId", 22L,
                "nodeId", 7L,
                "workbench", "release-handover",
                "fields", Map.of("notAField", "x")), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持字段: notAField");
    }

    @Test
    void rejectsAnUnknownWorkbench() {
        assertThatThrownBy(() -> command.preview(new CommandPreviewRequest(CommandName.NODE_FIELD_UPDATE, Map.of(
                "projectId", 22L,
                "nodeId", 7L,
                "workbench", "not-a-workbench",
                "fields", Map.of("handoverNotes", "x")), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的工作台");
    }

    @Test
    void rejectsAWorkbenchTheNodeDoesNotRun() {
        when(permissionService.requireNode(22L, 7L)).thenReturn(node(7L, "kickoff"));
        when(workflowTemplateService.getNodeDefinition(any(), eq("kickoff")))
                .thenReturn(new WorkflowNodeDefinition("kickoff", "项目立项与启动", "d", "x", "r",
                        java.util.List.of("project-basic-info"), java.util.List.of(), true, java.util.List.of()));

        assertThatThrownBy(() -> command.preview(new CommandPreviewRequest(CommandName.NODE_FIELD_UPDATE, Map.of(
                "projectId", 22L,
                "nodeId", 7L,
                "workbench", "release-handover",
                "fields", Map.of("handoverNotes", "x")), "project-detail", "v1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有发布决策与运营交接工作台");
    }

    @Test
    void executeMergesThePatchOntoTheCurrentDocumentInsteadOfReplacingIt() {
        when(release.get(22L, 7L)).thenReturn(releaseDocument());
        when(release.save(eq(22L), eq(7L), any(NodeReleaseUpdateCmd.class))).thenReturn(releaseDocument());

        CommandResult result = command().execute(operation(
                "{\"projectId\":22,\"nodeId\":7,\"workbench\":\"release-handover\","
                        + "\"fields\":{\"handoverNotes\":\"已完成运维交接\"}}",
                "[{\"projectVersion\":3,\"workbenchVersion\":2,\"fieldVersions\":{}}]"));

        ArgumentCaptor<NodeReleaseUpdateCmd> captor = ArgumentCaptor.forClass(NodeReleaseUpdateCmd.class);
        verify(release).save(eq(22L), eq(7L), captor.capture());
        NodeReleaseUpdateCmd sent = captor.getValue();
        assertThat(sent.getHandoverNotes()).isEqualTo("已完成运维交接");
        assertThat(sent.getVersion()).isEqualTo(2);
        // Untouched fields must survive the partial update.
        assertThat(sent.getReleaseVersion()).isEqualTo("v2.6.0");
        assertThat(sent.getEmergencyContact()).isEqualTo("张三 13800000000");
        assertThat(sent.getRollbackReady()).isTrue();
        assertThat(result.message()).isEqualTo("发布决策与运营交接已更新");
    }

    @Test
    void executeRejectsAPreviewThatIsNoLongerFresh() {
        NodeReleaseDTO changed = releaseDocument();
        changed.setVersion(5);
        when(release.get(22L, 7L)).thenReturn(changed);

        assertThatThrownBy(() -> command().execute(operation(
                "{\"projectId\":22,\"nodeId\":7,\"workbench\":\"release-handover\","
                        + "\"fields\":{\"handoverNotes\":\"x\"}}",
                "[{\"projectVersion\":3,\"workbenchVersion\":2,\"fieldVersions\":{}}]")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点工作台");
    }

    @Test
    void writesANestedWorkbenchDocumentWithoutClearingItsSiblingFields() {
        NodeSolutionDesignDTO design = new NodeSolutionDesignDTO();
        NodeSolutionPackageDTO solutionPackage = new NodeSolutionPackageDTO();
        solutionPackage.setProductSolution("旧产品方案");
        solutionPackage.setTechnicalSolution("旧技术方案");
        solutionPackage.setVersion(6);
        design.setSolutionPackage(solutionPackage);
        when(solutionDesign.get(22L, 7L)).thenReturn(design);
        when(workflowTemplateService.getNodeDefinition(any(), any(String.class)))
                .thenReturn(new WorkflowNodeDefinition("design", "方案设计、评审与决策", "d", "x", "r",
                        java.util.List.of("solution-design"), java.util.List.of(), false, java.util.List.of()));
        when(solutionDesign.saveDraft(eq(22L), eq(7L), any(NodeSolutionPackageUpdateCmd.class))).thenReturn(design);

        CommandResult result = command().execute(operation(
                "{\"projectId\":22,\"nodeId\":7,\"workbench\":\"solution-design\","
                        + "\"fields\":{\"productSolution\":\"新产品方案\"}}",
                "[{\"projectVersion\":3,\"workbenchVersion\":6,\"fieldVersions\":{}}]"));

        ArgumentCaptor<NodeSolutionPackageUpdateCmd> captor =
                ArgumentCaptor.forClass(NodeSolutionPackageUpdateCmd.class);
        verify(solutionDesign).saveDraft(eq(22L), eq(7L), captor.capture());
        assertThat(captor.getValue().getProductSolution()).isEqualTo("新产品方案");
        assertThat(captor.getValue().getTechnicalSolution()).isEqualTo("旧技术方案");
        assertThat(captor.getValue().getVersion()).isEqualTo(6);
        assertThat(result.message()).isEqualTo("方案设计与评审已更新");
    }

    @Test
    void writesCustomFieldsWithTheVersionEachStoredKeyWasReadAt() {
        when(workflowTemplateService.getNodeDefinition(any(), any(String.class)))
                .thenReturn(new WorkflowNodeDefinition("release", "发布决策与运营交接", "d", "x", "r",
                        java.util.List.of("release-handover"),
                        java.util.List.of(new com.brad.pms.workflow.WorkflowFieldDefinition(
                                "reviewer", "评审负责人", com.brad.pms.workflow.WorkflowFieldType.TEXT, false,
                                java.util.List.of()),
                                new com.brad.pms.workflow.WorkflowFieldDefinition(
                                        "note", "备注", com.brad.pms.workflow.WorkflowFieldType.TEXT, false,
                                        java.util.List.of())),
                        false, java.util.List.of()));
        WorkflowNodeFieldValuesDTO current = new WorkflowNodeFieldValuesDTO();
        Map<String, JsonNode> values = new LinkedHashMap<>();
        values.put("reviewer", objectMapper.valueToTree(31));
        current.setValues(values);
        current.setVersions(Map.of("reviewer", 4));
        when(customField.get(22L, 7L)).thenReturn(current);
        when(customField.save(eq(22L), eq(7L), any())).thenReturn(current);

        CommandResult result = command().execute(operation(
                "{\"projectId\":22,\"nodeId\":7,\"workbench\":\"custom-fields\","
                        + "\"fields\":{\"reviewer\":19,\"note\":\"新增字段\"}}",
                "[{\"projectVersion\":3,\"workbenchVersion\":null,\"fieldVersions\":{\"reviewer\":4}}]"));

        ArgumentCaptor<com.brad.pms.dto.request.WorkflowNodeFieldValuesCmd> captor =
                ArgumentCaptor.forClass(com.brad.pms.dto.request.WorkflowNodeFieldValuesCmd.class);
        verify(customField).save(eq(22L), eq(7L), captor.capture());
        assertThat(captor.getValue().getVersions()).containsEntry("reviewer", 4);
        assertThat(captor.getValue().getValues()).containsKeys("reviewer", "note");
        assertThat(result.message()).isEqualTo("节点自定义字段已更新");
    }

    private NodeFieldUpdateCommand command() {
        return command;
    }

    private static ProjectDO project(int version) {
        ProjectDO project = new ProjectDO();
        project.setId(22L);
        project.setVersion(version);
        project.setWorkflowTemplateVersionId(1L);
        return project;
    }

    private static ProjectNodeDO node(Long id, String nodeKey) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(22L);
        node.setNodeKey(nodeKey);
        node.setName("发布决策与运营交接");
        return node;
    }

    private static NodeReleaseDTO releaseDocument() {
        NodeReleaseDTO dto = new NodeReleaseDTO();
        dto.setProjectId(22L);
        dto.setNodeId(7L);
        dto.setVersion(2);
        dto.setReleaseVersion("v2.6.0");
        dto.setReleaseType("gray");
        dto.setPackageReady(true);
        dto.setRollbackReady(true);
        dto.setHandoverNotes("旧交接说明");
        dto.setEmergencyContact("张三 13800000000");
        return dto;
    }

    private static AiOperationDO operation(String arguments, String versions) {
        AiOperationDO operation = new AiOperationDO();
        operation.setId("op-node-field-1");
        operation.setArgumentsJson(arguments);
        operation.setExpectedVersionsJson(versions);
        return operation;
    }
}
