package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.dto.response.NodeRequirementDTO;
import com.brad.pms.dto.response.PersistedContentVersion;
import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.dto.response.WorkbenchReadSnapshot;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.service.NodeRequirementScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Projects persisted workbench facts into AI component records without falling back to display defaults. */
@ExtendWith(MockitoExtension.class)
class AiProjectComponentReaderTest {

    @Mock NodeRequirementScopeService requirementScopeService;

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Test
    void projectBasicInfoReportsTheRealProjectWhitelistWithEntityVersion() {
        AiProjectComponentReader reader = reader();
        ProjectDTO project = project();
        ProjectNodeDTO node = node(List.of("project-basic-info"));

        List<AiWorkflowContextDTO.Component> components =
                reader.read(project, node, "project-basic-info");

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.key()).isEqualTo("project-basic-info");
            assertThat(component.contentState()).isEqualTo("LOADED");
            assertThat(component.versions()).containsEntry("content", 3);
            Map<String, Object> content = objectMapper.convertValue(component.content(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
            assertThat(content).containsEntry("name", "订单中心")
                    .containsEntry("description", "统一订单描述")
                    .containsEntry("progress", 40)
                    .containsKey("startDate").containsKey("endDate");
            assertThat(content).doesNotContainKeys("createdByAvatar", "workflowTemplateVersionId");
        });
        verify(requirementScopeService, never()).getReadSnapshot(any(), any());
    }

    @Test
    void requirementScopeContentComesFromTheSnapshotWithoutUiFlags() {
        AiProjectComponentReader reader = reader();
        ProjectDTO project = project();
        ProjectNodeDTO node = node(List.of("requirement-scope"));
        NodeRequirementScopeDTO display = new NodeRequirementScopeDTO();
        display.setNodeId(301L);
        display.setVersion(2);
        NodeRequirementDTO requirement = new NodeRequirementDTO();
        requirement.setCode("REQ-001");
        requirement.setName("统一订单状态");
        requirement.setDescription("大描述：" + "系统状态。".repeat(1200) + "✅🚀");
        display.setRequirements(new java.util.ArrayList<>(List.of(requirement)));
        when(requirementScopeService.getReadSnapshot(7L, 301L)).thenReturn(new WorkbenchReadSnapshot<>(
                display, true, Map.of("content", new PersistedContentVersion(true, 2))));

        List<AiWorkflowContextDTO.Component> components =
                reader.read(project, node, "requirement-scope");

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.key()).isEqualTo("requirement-scope");
            assertThat(component.contentState()).isEqualTo("LOADED");
            assertThat(component.versions()).containsEntry("content", 2);
            String content = component.content().toString();
            assertThat(content).contains("统一订单状态");
            assertThat(content).contains("✅🚀");
            assertThat(component.content().has("canEdit")).isFalse();
        });
    }

    @Test
    void requirementScopeWithoutPersistedFactIsEmptyInsteadOfDisplayDefaults() {
        AiProjectComponentReader reader = reader();
        ProjectNodeDTO node = node(List.of("requirement-scope"));
        when(requirementScopeService.getReadSnapshot(7L, 301L)).thenReturn(new WorkbenchReadSnapshot<>(
                new NodeRequirementScopeDTO(), false,
                Map.of("content", new PersistedContentVersion(false, null))));

        List<AiWorkflowContextDTO.Component> components = reader.read(project(), node, "requirement-scope");

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.contentState()).isEqualTo("EMPTY");
            assertThat(component.content()).isNull();
            assertThat(component.versions()).containsEntry("content", null);
        });
    }

    @Test
    void attachedButUnsupportedComponentsStayUnsupportedWithReadableConfig() {
        AiProjectComponentReader reader = reader();
        ProjectNodeDTO node = node(List.of("solution-design", "project-basic-info"));
        node.setComponentConfigs(Map.of("solution-design",
                objectMapper.valueToTree(Map.of("visibility", "hidden"))));

        List<AiWorkflowContextDTO.Component> components =
                reader.read(project(), node, null);

        assertThat(components).extracting(AiWorkflowContextDTO.Component::key)
                .containsExactly("solution-design", "project-basic-info");
        // solution-design now has a real snapshot adapter; the EMPTY state is the truth for
        // a node with no saved package/decision/review.
        assertThat(components.get(0).contentState()).isEqualTo("EMPTY");
        assertThat(components.get(0).content()).isNull();
        assertThat(components.get(0).config()).isNotNull();
        assertThat(components.get(1).contentState()).isEqualTo("LOADED");
    }

    @Test
    void aComponentKeyOutsideTheNodeIsRejectedAsNotFound() {
        AiProjectComponentReader reader = reader();

        assertThatThrownBy(() -> reader.read(project(), node(List.of("project-basic-info")), "release-handover"))
                .isInstanceOf(BusinessException.class)
                .isInstanceOfSatisfying(BusinessException.class, thrown ->
                        assertThat(thrown.getCode()).isEqualTo(404));
    }

    @Test
    void aNullKeyReadsEveryAttachedComponentInDeclaredOrder() {
        AiProjectComponentReader reader = reader();
        ProjectNodeDTO node = node(List.of("project-basic-info"));

        List<AiWorkflowContextDTO.Component> components = reader.read(project(), node, null);

        assertThat(components).extracting(AiWorkflowContextDTO.Component::key)
                .containsExactly("project-basic-info");
    }

    @Test
    void aDomainServiceFailureIsNeverTreatedAsEmpty() {
        AiProjectComponentReader reader = reader();
        when(requirementScopeService.getReadSnapshot(7L, 301L))
                .thenThrow(new BusinessException("数据库异常"));

        assertThatThrownBy(() -> reader.read(project(), node(List.of("requirement-scope")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据库异常");
    }

    private final com.brad.pms.service.NodeSolutionDesignService solutionDesignService =
            mock(com.brad.pms.service.NodeSolutionDesignService.class);
    private final com.brad.pms.service.NodePlanResourceRiskService planResourceRiskService =
            mock(com.brad.pms.service.NodePlanResourceRiskService.class);
    private final com.brad.pms.service.NodeDevelopmentControlService developmentControlService =
            mock(com.brad.pms.service.NodeDevelopmentControlService.class);
    private final com.brad.pms.service.NodeAcceptanceService acceptanceService =
            mock(com.brad.pms.service.NodeAcceptanceService.class);
    private final com.brad.pms.service.NodeReleaseService releaseService =
            mock(com.brad.pms.service.NodeReleaseService.class);
    private final com.brad.pms.service.NodeValueReviewService valueReviewService =
            mock(com.brad.pms.service.NodeValueReviewService.class);
    private final com.brad.pms.service.NodeKnowledgeStandardService knowledgeStandardService =
            mock(com.brad.pms.service.NodeKnowledgeStandardService.class);

    private AiProjectComponentReader reader() {
        lenient().when(solutionDesignService.getReadSnapshot(any(), any())).thenAnswer(invocation ->
                new com.brad.pms.dto.response.WorkbenchReadSnapshot<>(
                        new com.brad.pms.dto.response.NodeSolutionDesignDTO(), false, java.util.Map.of()));
        lenient().when(planResourceRiskService.getReadSnapshot(any(), any())).thenAnswer(invocation ->
                new com.brad.pms.dto.response.WorkbenchReadSnapshot<>(
                        new com.brad.pms.dto.response.NodePlanResourceRiskDTO(), false, java.util.Map.of()));
        lenient().when(developmentControlService.getReadSnapshot(any(), any())).thenAnswer(invocation ->
                new com.brad.pms.dto.response.WorkbenchReadSnapshot<>(
                        new com.brad.pms.dto.response.NodeDevelopmentControlDTO(), false, java.util.Map.of()));
        lenient().when(acceptanceService.getReadSnapshot(any(), any())).thenAnswer(invocation ->
                new com.brad.pms.dto.response.WorkbenchReadSnapshot<>(
                        new com.brad.pms.dto.response.NodeAcceptanceDTO(), false, java.util.Map.of()));
        lenient().when(releaseService.getReadSnapshot(any(), any())).thenAnswer(invocation ->
                new com.brad.pms.dto.response.WorkbenchReadSnapshot<>(
                        new com.brad.pms.dto.response.NodeReleaseDTO(), false, java.util.Map.of()));
        lenient().when(valueReviewService.getReadSnapshot(any(), any())).thenAnswer(invocation ->
                new com.brad.pms.dto.response.WorkbenchReadSnapshot<>(
                        new com.brad.pms.dto.response.NodeValueReviewDTO(), false, java.util.Map.of()));
        lenient().when(knowledgeStandardService.getReadSnapshot(any(), any())).thenAnswer(invocation ->
                new com.brad.pms.dto.response.WorkbenchReadSnapshot<>(
                        new com.brad.pms.dto.response.NodeKnowledgeStandardDTO(), false, java.util.Map.of()));
        return new AiProjectComponentReader(objectMapper, requirementScopeService, solutionDesignService,
                planResourceRiskService, developmentControlService, acceptanceService, releaseService,
                valueReviewService, knowledgeStandardService);
    }

    private static ProjectDTO project() {
        ProjectDTO project = new ProjectDTO();
        project.setId(7L);
        project.setVersion(3);
        project.setName("订单中心");
        project.setDescription("统一订单描述");
        project.setProgress(40);
        project.setStartDate(java.time.LocalDate.of(2026, 10, 1));
        project.setEndDate(java.time.LocalDate.of(2026, 10, 14));
        project.setProjectManagerName("张伟");
        project.setOrgUnitName("交付一部");
        return project;
    }

    private static ProjectNodeDTO node(List<String> components) {
        ProjectNodeDTO node = new ProjectNodeDTO();
        node.setId(301L);
        node.setProjectId(7L);
        node.setNodeKey("requirement");
        node.setComponents(components);
        return node;
    }
}
