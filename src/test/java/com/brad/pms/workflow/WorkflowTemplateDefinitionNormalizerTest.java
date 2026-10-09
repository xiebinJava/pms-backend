package com.brad.pms.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTemplateDefinitionNormalizerTest {
    @Test
    void addsTheFunctionalSystemFieldToLegacyRequirementClarificationTemplates() {
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("clarify", "需求澄清", "", "", "",
                List.of(WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH), List.of(), false, List.of());

        WorkflowTemplateDefinition result = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                "requirement-management", new WorkflowTemplateDefinition(1, List.of(node)));

        assertThat(result.nodes().get(0).componentConfigs())
                .containsKey(WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH);
        assertThat(result.nodes().get(0).componentConfigs()
                .get(WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH).path("systemField").path("visibleWhenCategory").asText())
                .isEqualTo("FUNCTIONAL");
        assertThat(result.nodes().get(0).componentConfigs()
                .get(WorkflowComponentKey.REQUIREMENT_NODE_WORKBENCH).path("systemField").path("required").asBoolean())
                .isFalse();
    }

    @Test
    void migratesLegacyRequirementFieldsToRecordBindingsAndKeepsTheFieldSection() {
        WorkflowFieldDefinition description = new WorkflowFieldDefinition(
                "field-a", "需求描述", WorkflowFieldType.TEXTAREA, true, List.of(), true, null, true);
        WorkflowFieldDefinition owner = new WorkflowFieldDefinition(
                "field-b", "需求负责人", WorkflowFieldType.PERSON, true, List.of(), true, null, false);
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("intake", "需求录入", "", "", "",
                List.of(), List.of(description, owner), false, List.of(), List.of("legacy-custom-fields"));

        WorkflowTemplateDefinition result = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                "requirement-management", new WorkflowTemplateDefinition(2, List.of(node)));

        assertThat(result.nodes().get(0).fields()).extracting(WorkflowFieldDefinition::binding)
                .containsExactly("requirement.description", "requirement.owner");
        assertThat(result.nodes().get(0).contentOrder()).containsExactly("fields");
    }

    @Test
    void doesNotRewriteUnrelatedProcessTypesOrAmbiguousCustomFields() {
        WorkflowFieldDefinition field = new WorkflowFieldDefinition(
                "description", "描述", WorkflowFieldType.TEXTAREA, false, List.of(), true, null, null);
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("intake", "录入", "", "", "",
                List.of(), List.of(field), false, List.of(), List.of("fields"));
        WorkflowTemplateDefinition definition = new WorkflowTemplateDefinition(2, List.of(node));

        assertThat(WorkflowTemplateDefinitionNormalizer.normalizeForProcessType("topic-management", definition))
                .isSameAs(definition);
        assertThat(WorkflowTemplateDefinitionNormalizer.normalizeForProcessType("requirement-management", definition))
                .isSameAs(definition);
    }

    @Test
    void migratesLegacyRequirementPriorityBindingToSingleSelect() {
        WorkflowFieldDefinition priority = new WorkflowFieldDefinition(
                "requirement-priority", "需求优先级", WorkflowFieldType.NUMBER, true, List.of(), true,
                "requirement.priority", null);
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("intake", "需求录入", "", "", "",
                List.of(), List.of(priority), false, List.of(), List.of("fields"));

        WorkflowTemplateDefinition result = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                "requirement-management", new WorkflowTemplateDefinition(2, List.of(node)));

        assertThat(result.nodes().get(0).fields().get(0).type()).isEqualTo(WorkflowFieldType.SINGLE_SELECT);
    }

    @Test
    void doesNotAddAReleaseVersionFieldToANewRequirementReleaseNode() {
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("release", "需求上线", "", "", "",
                List.of(), List.of(), false, List.of(), List.of("component:requirement-node-workbench"));

        WorkflowTemplateDefinition definition = new WorkflowTemplateDefinition(2, List.of(node));
        WorkflowTemplateDefinition result = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                "requirement-management", definition);

        assertThat(result).isSameAs(definition);
    }

    @Test
    void removesTheLegacyReleaseVersionFieldFromRequirementReleaseNodes() {
        WorkflowFieldDefinition releaseVersion = new WorkflowFieldDefinition(
                "release-version", "发布版本", WorkflowFieldType.TEXT, true, List.of(), true, null, false);
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("release", "需求上线", "", "", "",
                List.of(), List.of(releaseVersion), false, List.of(), List.of("legacy-custom-fields"));

        WorkflowTemplateDefinition result = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                "requirement-management", new WorkflowTemplateDefinition(2, List.of(node)));

        assertThat(result.nodes().get(0).fields()).isEmpty();
        assertThat(result.nodes().get(0).contentOrder()).isEmpty();
    }
}
