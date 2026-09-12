package com.brad.pms.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTemplateDefinitionValidatorTest {

    @Test
    void compatibilityTemplateReproducesAllNineExistingNodesAndComponents() {
        WorkflowTemplateDefinition definition = BuiltInWorkflowTemplate.compatibilityDefinition();

        assertThat(definition.nodes()).extracting(WorkflowNodeDefinition::key)
                .containsExactly("kickoff", "requirement", "design", "plan", "develop",
                        "acceptance", "release", "review", "knowledge");
        assertThat(definition.nodes()).extracting(WorkflowNodeDefinition::components)
                .containsExactly(
                        List.of("project-basic-info"),
                        List.of("requirement-scope"),
                        List.of("solution-design"),
                        List.of("plan-resource-risk"),
                        List.of("development-control"),
                        List.of("business-acceptance"),
                        List.of("release-handover"),
                        List.of("value-review"),
                        List.of("knowledge-standard"));
        assertThat(definition.nodes().get(0).projectBasicInfoFields())
                .extracting(WorkflowProjectFieldDefinition::key)
                .containsExactly("description", "priority", "projectLevel", "schedule", "businessLine",
                        "projectManager", "projectMembers", "followers");
    }

    @Test
    void acceptsAllSupportedCustomFieldKindsAndSelectOptions() {
        WorkflowTemplateDefinition definition = new WorkflowTemplateDefinition(1, List.of(
                node("intake", List.of(
                        field("title", "标题", WorkflowFieldType.TEXT, true, List.of()),
                        field("notes", "说明", WorkflowFieldType.TEXTAREA, false, List.of()),
                        field("estimate", "估算", WorkflowFieldType.NUMBER, false, List.of()),
                        field("due", "日期", WorkflowFieldType.DATE, false, List.of()),
                        field("kind", "类型", WorkflowFieldType.SINGLE_SELECT, true, List.of("A", "B")),
                        field("labels", "标签", WorkflowFieldType.MULTI_SELECT, false, List.of("A", "B")),
                        field("owner", "协作人", WorkflowFieldType.PERSON, false, List.of()),
                        field("files", "附件", WorkflowFieldType.ATTACHMENT, false, List.of())))));

        assertThat(WorkflowTemplateDefinitionValidator.validate(definition)).isEqualTo(definition);
    }

    @Test
    void rejectsDuplicateNodeKeysAndUnsupportedComponents() {
        WorkflowTemplateDefinition duplicateKeys = new WorkflowTemplateDefinition(1,
                List.of(node("intake", List.of()), node("intake", List.of())));
        WorkflowTemplateDefinition unsupportedComponent = new WorkflowTemplateDefinition(1,
                List.of(new WorkflowNodeDefinition("intake", "intake", "说明", "交付物", "角色",
                        List.of("arbitrary-code"), List.of(), false, List.of())));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(duplicateKeys))
                .hasMessageContaining("节点标识重复");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(unsupportedComponent))
                .hasMessageContaining("不支持的工作台组件");
    }

    @Test
    void allowsTheSameSpecializedWorkbenchOnMultipleConfiguredNodes() {
        WorkflowTemplateDefinition definition = new WorkflowTemplateDefinition(1, List.of(
                new WorkflowNodeDefinition("scope-first", "Scope", "", "", "",
                        List.of("requirement-scope"), List.of(), false, List.of()),
                new WorkflowNodeDefinition("scope-followup", "Follow-up", "", "", "",
                        List.of("requirement-scope"), List.of(), false, List.of())));

        assertThat(WorkflowTemplateDefinitionValidator.validate(definition).nodes()).hasSize(2);
    }

    @Test
    void projectBasicInfoFlagMustMatchItsBuiltInComponentAttachment() {
        WorkflowTemplateDefinition invalid = new WorkflowTemplateDefinition(1, List.of(
                new WorkflowNodeDefinition("intake", "intake", "说明", "交付物", "角色",
                        List.of("project-basic-info"), List.of(), false, List.of())));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(invalid))
                .hasMessageContaining("项目信息组件配置不一致");
    }

    @Test
    void rejectsRequiredCanonicalProjectFieldsThatAreHidden() {
        WorkflowTemplateDefinition invalid = new WorkflowTemplateDefinition(1, List.of(
                new WorkflowNodeDefinition("intake", "intake", "说明", "交付物", "角色",
                        List.of("project-basic-info"), List.of(), true,
                        List.of(new WorkflowProjectFieldDefinition("description", "项目描述", false, true)))));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(invalid))
                .hasMessageContaining("必填项目信息字段必须显示");
    }

    @Test
    void requiresOptionsOnlyForSelectFieldsAndRejectsDuplicateFieldKeys() {
        WorkflowTemplateDefinition missingOptions = new WorkflowTemplateDefinition(1,
                List.of(node("intake", List.of(field("kind", "类型", WorkflowFieldType.SINGLE_SELECT, true, List.of())))));
        WorkflowTemplateDefinition duplicateFields = new WorkflowTemplateDefinition(1,
                List.of(node("intake", List.of(
                        field("value", "字段一", WorkflowFieldType.TEXT, false, List.of()),
                        field("value", "字段二", WorkflowFieldType.TEXT, false, List.of())))));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(missingOptions))
                .hasMessageContaining("至少配置一个选项");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(duplicateFields))
                .hasMessageContaining("字段标识重复");
    }

    private static WorkflowNodeDefinition node(String key, List<WorkflowFieldDefinition> fields) {
        return new WorkflowNodeDefinition(key, key, "说明", "交付物", "角色", List.of(), fields, false, List.of());
    }

    private static WorkflowFieldDefinition field(String key, String label, WorkflowFieldType type,
                                                 boolean required, List<String> options) {
        return new WorkflowFieldDefinition(key, label, type, required, options);
    }
}
