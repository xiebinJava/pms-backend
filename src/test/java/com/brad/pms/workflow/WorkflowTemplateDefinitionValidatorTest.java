package com.brad.pms.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
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

    @Test
    void keepsV1FieldTypesLimitedToTheLegacyContract() {
        WorkflowTemplateDefinition definition = new WorkflowTemplateDefinition(1,
                List.of(node("intake", List.of(field("priority", "优先级", WorkflowFieldType.RADIO,
                        false, List.of("P0", "P1"))))));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(definition))
                .hasMessageContaining("字段类型无效");
    }

    @Test
    void acceptsLegacyV1JsonWithNewMetadataAbsent() throws Exception {
        String legacyJson = """
                {"schemaVersion":1,"nodes":[{"key":"intake","name":"intake","description":"说明",
                "deliverable":"交付物","roles":"角色","components":[],"fields":[{"key":"title",
                "label":"标题","type":"TEXT","required":true,"options":[]}],"projectBasicInfo":false,
                "projectBasicInfoFields":[]}]}
                """;
        WorkflowTemplateDefinition definition = new ObjectMapper().readValue(legacyJson, WorkflowTemplateDefinition.class);

        assertThat(definition.nodes().get(0).fields().get(0).visible()).isNull();
        assertThat(definition.nodes().get(0).fields().get(0).binding()).isNull();
        assertThat(definition.nodes().get(0).contentOrder()).isNull();
        assertThat(WorkflowTemplateDefinitionValidator.validate(definition)).isEqualTo(definition);
    }

    @Test
    void roundTripsTopicSourceProjectNodeKeyWithoutBreakingUnknownLegacyMetadata() throws Exception {
        String json = """
                {"schemaVersion":1,"sourceProjectNodeKey":"develop","sourceTopicNodeKey":"requirements","nodes":[{"key":"intake",
                "name":"立项","description":"","deliverable":"","roles":"","components":[],
                "fields":[],"projectBasicInfo":false,"projectBasicInfoFields":[]}]}
                """;
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        WorkflowTemplateDefinition definition = mapper.readValue(json, WorkflowTemplateDefinition.class);
        String serialized = mapper.writeValueAsString(definition);
        WorkflowTemplateDefinition roundTripped = mapper.readValue(serialized, WorkflowTemplateDefinition.class);

        assertThat(mapper.readTree(serialized).path("sourceProjectNodeKey").asText()).isEqualTo("develop");
        assertThat(mapper.readTree(serialized).path("sourceTopicNodeKey").asText()).isEqualTo("requirements");
        assertThat(WorkflowTemplateDefinitionValidator.validate(roundTripped)).isEqualTo(roundTripped);
    }

    @Test
    void templateJsonRoundTripsExplicitFieldWidth() throws Exception {
        String json = """
                {"schemaVersion":2,"nodes":[{"key":"intake","name":"立项","description":"",
                "deliverable":"","roles":"","fields":[{"key":"summary","label":"摘要",
                "type":"TEXTAREA","required":false,"options":[],"fullWidth":false}],
                "contentOrder":["fields"]}]}
                """;
        ObjectMapper mapper = new ObjectMapper();

        WorkflowTemplateDefinition definition = mapper.readValue(json, WorkflowTemplateDefinition.class);
        WorkflowTemplateDefinition roundTripped = mapper.readValue(mapper.writeValueAsString(definition),
                WorkflowTemplateDefinition.class);

        assertThat(mapper.readTree(mapper.writeValueAsString(roundTripped))
                .at("/nodes/0/fields/0/fullWidth").asBoolean()).isFalse();
        assertThat(WorkflowTemplateDefinitionValidator.validate(roundTripped)).isEqualTo(roundTripped);
    }

    @Test
    void rejectsV1BindingAndVisibilityMetadata() {
        WorkflowTemplateDefinition withBinding = new WorkflowTemplateDefinition(1,
                List.of(node("intake", List.of(v2Field("title", "标题", WorkflowFieldType.TEXT,
                        true, List.of(), true, "project.description")))));
        WorkflowTemplateDefinition withHiddenRequired = new WorkflowTemplateDefinition(1,
                List.of(node("intake", List.of(v2Field("title", "标题", WorkflowFieldType.TEXT,
                        true, List.of(), false, null)))));
        WorkflowTemplateDefinition withVisibleMetadata = new WorkflowTemplateDefinition(1,
                List.of(node("intake", List.of(v2Field("title", "标题", WorkflowFieldType.TEXT,
                        false, List.of(), true, null)))));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(withBinding))
                .hasMessageContaining("v1 字段不能配置绑定");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(withHiddenRequired))
                .hasMessageContaining("v1 字段不能配置显示属性");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(withVisibleMetadata))
                .hasMessageContaining("v1 字段不能配置显示属性");
    }

    @Test
    void acceptsV2FieldsBindingsAndCompleteContentOrder() {
        WorkflowTemplateDefinition definition = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(
                        v2Field("description", "项目说明", WorkflowFieldType.TEXTAREA, true, List.of(), true,
                                "project.description"),
                        v2Field("priority", "优先级", WorkflowFieldType.RADIO, false, List.of(), true,
                                "project.priority"),
                        v2Field("level", "等级", WorkflowFieldType.SINGLE_SELECT, false, List.of(), true,
                                "project.projectLevel"),
                        v2Field("schedule", "计划", WorkflowFieldType.DATE_RANGE, false, List.of(), true,
                                "project.schedule"),
                        v2Field("business", "业务线", WorkflowFieldType.SINGLE_SELECT, false, List.of(), true,
                                "project.businessLine"),
                        v2Field("manager", "项目经理", WorkflowFieldType.PERSON, false, List.of(), true,
                                "project.projectManager"),
                        v2Field("members", "成员", WorkflowFieldType.PERSON_MULTI, false, List.of(), true,
                                "project.projectMembers"),
                        v2Field("followers", "关注人", WorkflowFieldType.PERSON_MULTI, false, List.of(), true,
                                "project.followers"),
                        v2Field("note", "备注", WorkflowFieldType.TEXT, false, List.of(), true, null),
                        v2Field("estimate", "估算", WorkflowFieldType.NUMBER, false, List.of(), true, null),
                        v2Field("due", "日期", WorkflowFieldType.DATE, false, List.of(), true, null),
                        v2Field("labels", "标签", WorkflowFieldType.MULTI_SELECT, false, List.of("A", "B"), true, null),
                        v2Field("files", "附件", WorkflowFieldType.ATTACHMENT, false, List.of(), true, null)),
                        List.of("fields", "component:solution-design"))));

        assertThat(WorkflowTemplateDefinitionValidator.validate(definition)).isEqualTo(definition);
    }

    @Test
    void acceptsEmptyV2NodesAndRequiresOrderSlotsOnlyForDefinedFields() {
        WorkflowTemplateDefinition emptyNode = new WorkflowTemplateDefinition(2, List.of(
                v2Node("empty", List.of(), List.of()),
                v2Node("workbench-only", List.of(), List.of("component:solution-design"))));
        WorkflowTemplateDefinition missingFieldSlot = new WorkflowTemplateDefinition(2, List.of(
                v2Node("field-without-slot", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        false, List.of(), true, null)), List.of())));
        WorkflowTemplateDefinition danglingFieldsSlot = new WorkflowTemplateDefinition(2, List.of(
                v2Node("dangling-fields", List.of(), List.of("fields"))));
        WorkflowTemplateDefinition danglingLegacySlot = new WorkflowTemplateDefinition(2, List.of(
                v2Node("dangling-legacy", List.of(), List.of("legacy-custom-fields"))));

        assertThat(WorkflowTemplateDefinitionValidator.validate(emptyNode).nodes()).hasSize(2);
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(missingFieldSlot))
                .hasMessageContaining("节点内容排序");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(danglingFieldsSlot))
                .hasMessageContaining("节点内容排序");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(danglingLegacySlot))
                .hasMessageContaining("节点内容排序");
    }

    @Test
    void acceptsMigrationOnlyLegacyCustomFieldOrderWithoutChangingTheUnifiedV2Slot() {
        WorkflowTemplateDefinition migrated = new WorkflowTemplateDefinition(2, List.of(
                v2Node("migrated", List.of(
                        v2Field("project-description", "项目描述", WorkflowFieldType.TEXTAREA,
                                false, List.of(), true, "project.description"),
                        v2Field("legacy-note", "旧备注", WorkflowFieldType.TEXT,
                                false, List.of(), true, null)),
                        List.of("fields", "component:solution-design", "legacy-custom-fields")),
                v2Node("legacy-only", List.of(v2Field("legacy-note", "旧备注", WorkflowFieldType.TEXT,
                        false, List.of(), true, null)), List.of("component:solution-design", "legacy-custom-fields"))));

        assertThat(WorkflowTemplateDefinitionValidator.validate(migrated)).isEqualTo(migrated);
    }

    @Test
    void rejectsNullContentOrderEntriesAsUnknownSchemaItems() {
        WorkflowTemplateDefinition nullOrderEntry = new WorkflowTemplateDefinition(2, List.of(
                v2Node("null-entry", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        false, List.of(), true, null)), java.util.Arrays.asList("fields", null))));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(nullOrderEntry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("节点内容排序存在未知项");
    }

    @Test
    void rejectsInvalidV2BindingsFieldsAndContentOrder() {
        WorkflowTemplateDefinition duplicateOrder = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        false, List.of(), true, null)), List.of("fields", "fields", "component:solution-design"))));
        WorkflowTemplateDefinition unknownOrder = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        false, List.of(), true, null)), List.of("fields", "component:unknown"))));
        WorkflowTemplateDefinition projectBasicInfoOrder = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        false, List.of(), true, null)), List.of("fields", "component:project-basic-info"))));
        WorkflowTemplateDefinition unknownBinding = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        false, List.of(), true, "project.owner")), List.of("fields"))));
        WorkflowTemplateDefinition hiddenRequired = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        true, List.of(), false, null)), List.of("fields"))));
        WorkflowTemplateDefinition wrongBindingType = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(v2Field("priority", "优先级", WorkflowFieldType.TEXT,
                        false, List.of(), true, "project.priority")), List.of("fields"))));
        WorkflowTemplateDefinition invalidOptions = new WorkflowTemplateDefinition(2, List.of(
                v2Node("intake", List.of(v2Field("note", "备注", WorkflowFieldType.TEXT,
                        false, List.of("错误"), true, null)), List.of("fields"))));

        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(duplicateOrder))
                .hasMessageContaining("内容排序");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(unknownOrder))
                .hasMessageContaining("内容排序");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(projectBasicInfoOrder))
                .hasMessageContaining("内容排序");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(unknownBinding))
                .hasMessageContaining("绑定");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(hiddenRequired))
                .hasMessageContaining("必填字段必须显示");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(wrongBindingType))
                .hasMessageContaining("控件类型");
        assertThatThrownBy(() -> WorkflowTemplateDefinitionValidator.validate(invalidOptions))
                .hasMessageContaining("只有单选或多选字段可以配置选项");
    }

    private static WorkflowNodeDefinition node(String key, List<WorkflowFieldDefinition> fields) {
        return new WorkflowNodeDefinition(key, key, "说明", "交付物", "角色", List.of(), fields, false, List.of());
    }

    private static WorkflowFieldDefinition field(String key, String label, WorkflowFieldType type,
                                                 boolean required, List<String> options) {
        return new WorkflowFieldDefinition(key, label, type, required, options);
    }

    private static WorkflowNodeDefinition v2Node(String key, List<WorkflowFieldDefinition> fields,
                                                 List<String> contentOrder) {
        return new WorkflowNodeDefinition(key, key, "说明", "交付物", "角色", null, fields, false, null, contentOrder);
    }

    private static WorkflowFieldDefinition v2Field(String key, String label, WorkflowFieldType type,
                                                    boolean required, List<String> options, boolean visible,
                                                    String binding) {
        return new WorkflowFieldDefinition(key, label, type, required, options, visible, binding);
    }
}
