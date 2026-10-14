package com.brad.pms.integration.ai.api;

import com.brad.pms.ai.connector.context.ContextReadOptions;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.NodePermissionsDTO;
import com.brad.pms.dto.response.NodeTaskSummaryDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contract and serialization stability for the AI workflow context DTO. */
class AiWorkflowContextDTOTest {

    @Test
    void legacyTopLevelConstructorKeepsTheOriginalSixArgumentsAndNullOptionalFields() {
        AiWorkflowContextDTO dto = new AiWorkflowContextDTO(
                "topic", 7L, null,
                new AiWorkflowContextDTO.CurrentNode(21L, "design", "设计"),
                new AiWorkflowContextDTO.Workflow(88L, 2, List.of()), List.of());

        assertThat(dto.resourceType()).isEqualTo("topic");
        assertThat(dto.resourceId()).isEqualTo(7L);
        assertThat(dto.entityVersion()).isNull();
        assertThat(dto.resourceDetails()).isNull();
        assertThat(dto.relations()).isEmpty();
        assertThat(dto.meta()).isNull();
        assertThat(dto.workflow().nodes()).isEmpty();
    }

    @Test
    void legacyNodeAndComponentConstructorsKeepNullNewFacts() {
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(1L, "k", "n", 0, 1,
                List.of(new AiWorkflowContextDTO.Component("c", "组件", List.of(), List.of())),
                List.of(), Map.of("field", "v"));

        assertThat(node.id()).isEqualTo(1L);
        assertThat(node.version()).isNull();
        assertThat(node.description()).isNull();
        assertThat(node.deliverable()).isNull();
        assertThat(node.ownerId()).isNull();
        assertThat(node.ownerName()).isNull();
        assertThat(node.startDate()).isNull();
        assertThat(node.endDate()).isNull();
        assertThat(node.fieldValueVersions()).isNull();
        assertThat(node.fieldValuesState()).isNull();
        assertThat(node.permissions()).isNull();
        assertThat(node.taskSummary()).isNull();

        AiWorkflowContextDTO.Component component = node.components().get(0);
        assertThat(component.config()).isNull();
        assertThat(component.content()).isNull();
        assertThat(component.contentState()).isNull();
        assertThat(component.versions()).isNull();
        assertThat(component.saveContract()).isNull();
        assertThat(component.fields()).isEmpty();
        assertThat(component.actions()).isEmpty();
    }

    @Test
    void nullFieldValuesAreNormalizedToEmptyMapsWithoutContent() {
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(1L, "k", "n", 0, 1,
                List.of(), List.of(), null);

        assertThat(node.fieldValues()).isEmpty();
    }

    @Test
    void theFullNodeContractCarriesTypedLifecycleFacts() {
        NodePermissionsDTO permissions = new NodePermissionsDTO();
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(
                1L, "k", "n", 0, 1, List.of(), List.of(), Map.of(),
                4, "描述", "交付物", 42L, "负责人", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 14), Map.of("k", 2), "LOADED", permissions,
                new NodeTaskSummaryDTO(3, 1, 2));

        assertThat(node.version()).isEqualTo(4);
        assertThat(node.fieldValuesState()).isEqualTo("LOADED");
        assertThat(node.permissions()).isSameAs(permissions);
        assertThat(node.taskSummary()).isEqualTo(new NodeTaskSummaryDTO(3, 1, 2));
    }

    @Test
    void contextMetaExposesTheNamedProjectionScopes() {
        AiWorkflowContextDTO.ContextMeta meta = new AiWorkflowContextDTO.ContextMeta(
                2, true, Instant.EPOCH, "Asia/Shanghai", "focused",
                "selected-node", "selected-component", 301L, "solution-design",
                true, "IN_PROGRESS", null, 5,
                "business-user", "readable-only", List.of());

        assertThat(meta.formatVersion()).isEqualTo(2);
        assertThat(meta.pureRead()).isTrue();
        assertThat(meta.loadedAt()).isEqualTo(Instant.EPOCH);
        assertThat(meta.businessTimezone()).isEqualTo("Asia/Shanghai");
        assertThat(meta.view()).isEqualTo("focused");
        assertThat(meta.fieldValuesScope()).isEqualTo("selected-node");
        assertThat(meta.componentContentScope()).isEqualTo("selected-component");
        assertThat(meta.selectedNodeId()).isEqualTo(301L);
        assertThat(meta.selectedComponentKey()).isEqualTo("solution-design");
        assertThat(meta.workflowConfigured()).isTrue();
        assertThat(meta.workflowStatus()).isEqualTo("IN_PROGRESS");
        assertThat(meta.terminalStatus()).isNull();
        assertThat(meta.workflowInstanceVersion()).isEqualTo(5);
        assertThat(meta.permissionsScope()).isEqualTo("business-user");
        assertThat(meta.referencesScope()).isEqualTo("readable-only");
        assertThat(meta.warnings()).isEmpty();
    }

    @Test
    void contextReadOptionsNormalizesMissingViewToLegacyAndRejectsIllegalFlags() {
        assertThat(new ContextReadOptions(null, null, null).view()).isEqualTo("legacy");
        assertThat(new ContextReadOptions(null, 301L, null).nodeId()).isEqualTo(301L);

        assertThatThrownBy(() -> new ContextReadOptions("aggressive", null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("读取模式");
        assertThatThrownBy(() -> new ContextReadOptions("legacy", 0L, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("正整数");
        assertThatThrownBy(() -> new ContextReadOptions("legacy", -1L, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("正整数");
        assertThatThrownBy(() -> new ContextReadOptions("legacy", null, ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能为空白");
        assertThatThrownBy(() -> new ContextReadOptions("legacy", null, "solution-design"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("focused");
        assertThat(new ContextReadOptions("focused", 301L, "solution-design").componentKey())
                .isEqualTo("solution-design");
    }
}
