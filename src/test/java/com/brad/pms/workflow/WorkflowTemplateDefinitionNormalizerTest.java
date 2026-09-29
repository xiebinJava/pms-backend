package com.brad.pms.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTemplateDefinitionNormalizerTest {
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
    void restoresOnlyTheRequiredReleaseVersionFieldOnTheRequirementReleaseNode() {
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("release", "需求上线", "", "", "",
                List.of(), List.of(), false, List.of(), List.of());

        WorkflowTemplateDefinition result = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                "requirement-management", new WorkflowTemplateDefinition(2, List.of(node)));

        assertThat(result.nodes().get(0).fields()).extracting(WorkflowFieldDefinition::key)
                .containsExactly("release-version");
        assertThat(result.nodes().get(0).fields().get(0).label()).isEqualTo("发布版本");
        assertThat(result.nodes().get(0).fields().get(0).type()).isEqualTo(WorkflowFieldType.TEXT);
        assertThat(result.nodes().get(0).fields().get(0).required()).isTrue();
        assertThat(result.nodes().get(0).contentOrder()).containsExactly("legacy-custom-fields");
    }

    @Test
    void restoresReleaseVersionForLegacyRequirementTemplatesWithoutAddingV2Metadata() {
        WorkflowNodeDefinition node = new WorkflowNodeDefinition("release", "需求上线", "", "", "",
                List.of(), List.of(), false, List.of());

        WorkflowTemplateDefinition result = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                "requirement-management", new WorkflowTemplateDefinition(1, List.of(node)));

        WorkflowFieldDefinition releaseVersion = result.nodes().get(0).fields().get(0);
        assertThat(releaseVersion.key()).isEqualTo("release-version");
        assertThat(releaseVersion.visible()).isNull();
        assertThat(releaseVersion.binding()).isNull();
        assertThat(releaseVersion.fullWidth()).isNull();
        assertThat(result.nodes().get(0).contentOrder()).isNull();
    }
}
