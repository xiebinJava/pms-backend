package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.dto.response.RequirementExecutionTargetDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Development components split by their real sources: workbench JSON or execution binding. */
@ExtendWith(MockitoExtension.class)
class AiDevelopmentComponentReaderTest {

    private final AiContextReadPolicy policy = mock(AiContextReadPolicy.class);

    private final com.brad.pms.service.DevelopmentStoryManagementService storyService =
            mock(com.brad.pms.service.DevelopmentStoryManagementService.class);

    private AiDevelopmentComponentReader reader(AiContextReferenceProjector projector,
                                                AiContextReadPolicy policy) {
        return new AiDevelopmentComponentReader(projector, policy, storyService);
    }

    private DevelopmentItemWorkflowNodeDTO node(String json, Integer version) {
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(101L);
        node.setNodeKey("analysis");
        node.setVersion(version);
        node.setRuntimeComponents(List.of("requirement-receiving-analysis", "requirement-execution",
                "story-list", "unknown-component"));
        var mapper = JsonMapper.builder().build();
        com.fasterxml.jackson.databind.node.ObjectNode components = mapper.createObjectNode();
        if (json != null) components.set("requirement-receiving-analysis",
                mapper.valueToTree(Map.<String, Object>of("conclusion", "可行", "topicId", "9")));
        node.setFieldValues(json == null ? null : Map.<String, com.fasterxml.jackson.databind.JsonNode>of(
                "__components", components));
        return node;
    }

    private DevelopmentItemWorkflowDetailDTO item(RequirementExecutionTargetDTO target) {
        DevelopmentItemWorkflowDetailDTO item = new DevelopmentItemWorkflowDetailDTO();
        item.setItemType("REQUIREMENT");
        item.setId(23L);
        item.setExecutionTarget(target);
        return item;
    }

    private void requireTopicDenied() {
        when(policy.canReadReference(org.mockito.ArgumentMatchers.eq("topic"), any()))
                .thenReturn(false);
    }

    @Test
    void savedJsonIsProjectedThroughReferenceFilteringAndCarriesTheNodeVersion() {
        requireTopicDenied();
        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        AiDevelopmentComponentReader reader = reader(real, policy);
        List<String> unknown = new ArrayList<>();

        List<AiWorkflowContextDTO.Component> components = reader.read(DevelopmentItemType.REQUIREMENT,
                item(null), node("{\"saved\":true}", 4), 5, "requirement-receiving-analysis", unknown);

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.key()).isEqualTo("requirement-receiving-analysis");
            assertThat(component.contentState()).isEqualTo("LOADED");
            assertThat(component.versions()).containsEntry("node", 4);
            assertThat(component.content().get("conclusion").asText()).isEqualTo("可行");
            assertThat(component.content().has("topicId")).isFalse();
        assertThat(component.saveContract()).containsEntry("contractVersion", "node-detail/v1");
        assertThat(component.saveContract()).containsEntry("completionCommand",
                "development-item.node.complete-with-data");
        });
        assertThat(unknown).isEmpty();
    }

    @Test
    void missingJsonIsEmptyWithoutLosingTheNodeVersion() {
        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        AiDevelopmentComponentReader reader = reader(real, policy);
        List<String> unknown = new ArrayList<>();

        List<AiWorkflowContextDTO.Component> components = reader.read(DevelopmentItemType.REQUIREMENT,
                item(null), node(null, 4), 5, null, unknown);

        java.util.Map<String, AiWorkflowContextDTO.Component> byKey = new java.util.HashMap<>();
        components.forEach(component -> byKey.put(component.key(), component));
        assertThat(byKey.get("requirement-receiving-analysis").contentState()).isEqualTo("EMPTY");
        assertThat(byKey.get("requirement-receiving-analysis").content()).isNull();
        assertThat(byKey.get("requirement-receiving-analysis").versions()).containsEntry("node", 4);
        assertThat(byKey.get("story-list").contentState()).isEqualTo("UNSUPPORTED");
        assertThat(byKey.get("unknown-component").contentState()).isEqualTo("UNSUPPORTED");
        assertThat(unknown).contains("story-list", "unknown-component");
    }

    @Test
    void executionTargetReadsWithTheEntityVersionAndNeverExposesTargetIds() {
        when(policy.canReadReference(org.mockito.ArgumentMatchers.eq("project"), any())).thenReturn(true);
        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        AiDevelopmentComponentReader reader = reader(real, policy);
        List<String> unknown = new ArrayList<>();
        RequirementExecutionTargetDTO target = new RequirementExecutionTargetDTO();
        target.setTargetType(com.brad.pms.common.enums.RequirementExecutionTargetType.PROJECT);
        target.setTargetId(7L);
        target.setTitle("订单项目");
        target.setStatus("ACTIVE");

        List<AiWorkflowContextDTO.Component> components = reader.read(DevelopmentItemType.REQUIREMENT,
                item(target), node(null, 4), 5, "requirement-execution", unknown);

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.contentState()).isEqualTo("LOADED");
            assertThat(component.versions()).containsEntry("entity", 5);
            assertThat(component.content().get("title").asText()).isEqualTo("订单项目");
            assertThat(component.content().has("targetId")).isFalse();
            assertThat(component.content().has("targetType")).isFalse();
        });
    }

    @Test
    void aBoundButUnreadableExecutionTargetStaysLoadedWithEmptyContent() {
        when(policy.canReadReference(org.mockito.ArgumentMatchers.eq("project"), any())).thenReturn(false);
        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        AiDevelopmentComponentReader reader = reader(real, policy);
        List<String> unknown = new ArrayList<>();
        RequirementExecutionTargetDTO target = new RequirementExecutionTargetDTO();
        target.setTargetType(com.brad.pms.common.enums.RequirementExecutionTargetType.PROJECT);
        target.setTargetId(7L);
        target.setTitle("不可读项目");

        List<AiWorkflowContextDTO.Component> components = reader.read(DevelopmentItemType.REQUIREMENT,
                item(target), node(null, 4), 5, "requirement-execution", unknown);

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.contentState()).isEqualTo("LOADED");
            assertThat(component.content().isEmpty()).isTrue();
        });
    }

    @Test
    void withoutAnyBindingTheExecutionComponentReportsEmpty() {
        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        AiDevelopmentComponentReader reader = reader(real, policy);
        List<String> unknown = new ArrayList<>();

        List<AiWorkflowContextDTO.Component> components = reader.read(DevelopmentItemType.REQUIREMENT,
                item(null), node(null, 4), 5, "requirement-execution", unknown);

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.contentState()).isEqualTo("EMPTY");
            assertThat(component.versions()).containsEntry("entity", 5);
        });
    }


    @Test
    void storyListReadsRealStoryRowsAndKeepsNullListVersion() {
        when(policy.canReadReference(org.mockito.ArgumentMatchers.eq("story"), any())).thenReturn(true);
        when(storyService.listByTopic(15L)).thenReturn(List.of());
        DevelopmentItemWorkflowDetailDTO item = new DevelopmentItemWorkflowDetailDTO();
        item.setItemType("TOPIC");
        item.setId(15L);
        item.setTopicId(15L);
        item.setTopicWorkflowNodeId(101L);
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(101L);
        node.setVersion(7);
        node.setRuntimeComponents(List.of("story-list"));
        AiDevelopmentComponentReader realReader = reader(new AiContextReferenceProjector(policy), policy);
        List<String> unknown = new ArrayList<>();

        List<AiWorkflowContextDTO.Component> components = realReader.read(
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, item, node, null, "story-list", unknown);

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.key()).isEqualTo("story-list");
            assertThat(component.contentState()).isEqualTo("EMPTY");
            assertThat(component.versions()).containsEntry("node", 7);
            assertThat(component.versions()).containsEntry("stories", null);
        });
        assertThat(unknown).isEmpty();
    }

    @Test
    void aComponentKeyOutsideTheAttachedSetIsRejectedAsNotFound() {
        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        AiDevelopmentComponentReader reader = reader(real, policy);
        List<String> unknown = new ArrayList<>();

        assertThatThrownBy(() -> reader.read(DevelopmentItemType.REQUIREMENT, item(null),
                node(null, 4), 5, "release-handover", unknown))
                .isInstanceOf(com.brad.pms.common.exception.BusinessException.class);
    }

    @Test
    void storyWorkbenchExposesAStableSaveContractForAiDrafts() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO story = new DevelopmentItemWorkflowDetailDTO();
        story.setItemType("STORY");
        story.setId(34L);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setVersion(2);
        node.setRuntimeComponents(List.of("story-node-workbench"));
        node.setComponentConfigs(Map.of("story-node-workbench",
                mapper.valueToTree(Map.of("variant", "writing"))));
        node.setFieldValues(Map.of("__components", mapper.valueToTree(Map.of(
                "story-node-workbench", Map.of("descriptionAndAcceptance", "现有内容")))));

        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        List<AiWorkflowContextDTO.Component> components = reader(real, policy).read(
                DevelopmentItemType.STORY, story, node, null, "story-node-workbench", new ArrayList<>());

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.saveContract()).isNotNull();
            assertThat(component.saveContract()).containsEntry("command", "development-item.node.field.update");
            assertThat(component.saveContract()).containsEntry("itemType", "STORY");
            assertThat(component.saveContract()).containsEntry("itemId", 34L);
            assertThat(component.saveContract()).containsEntry("nodeId", 383L);
            assertThat(component.saveContract()).containsEntry("version", 2);
            assertThat(component.saveContract()).containsEntry("componentKey", "story-node-workbench");
            assertThat(component.saveContract()).containsEntry("variant", "writing");
            assertThat(component.saveContract()).containsEntry("fieldValuesPath",
                    "__components.story-node-workbench");
            assertThat(component.saveContract()).containsEntry("contractVersion", "node-detail/v1");
            assertThat(component.saveContract()).containsEntry("componentContractVersion", "story-node-workbench/v1");
            assertThat(component.saveContract().get("writableFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("acceptanceCriteria", "background", "descriptionAndAcceptance", "priority")
                    .doesNotContain("title", "topicId", "baseTitle", "baseTopicId");
            assertThat(component.saveContract().get("effectiveWritableFields"))
                    .isEqualTo(component.saveContract().get("writableFields"));
            assertThat(component.saveContract().get("requiredFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .isEmpty();
            assertThat(component.saveContract().get("protectedFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .contains("title", "status", "ownerId", "startDate", "dueDate", "topicId");
            assertThat(component.saveContract()).containsEntry("completionCommand",
                    "development-item.node.complete-with-data");
            assertThat(component.saveContract()).containsEntry("completionSupported", true);
            assertThat(component.saveContract().get("completionRequiredMetadata"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("ownerId", "startDate", "endDate");
            assertThat(component.saveContract().get("writableFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .doesNotContain("title", "status", "ownerId", "startDate", "endDate");
        });
    }

    @Test
    void storyDevelopmentWorkbenchPublishesRequiredFieldContract() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO story = new DevelopmentItemWorkflowDetailDTO();
        story.setItemType("STORY");
        story.setId(34L);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(383L);
        node.setVersion(3);
        node.setRuntimeComponents(List.of("story-node-workbench"));
        node.setComponentConfigs(Map.of("story-node-workbench",
                mapper.valueToTree(Map.of("variant", "development"))));
        node.setFieldValues(Map.of("__components", mapper.valueToTree(Map.of(
                "story-node-workbench", Map.of("implementationNote", "已有实现")))));

        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        List<AiWorkflowContextDTO.Component> components = reader(real, policy).read(
                DevelopmentItemType.STORY, story, node, null, "story-node-workbench", new ArrayList<>());

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.saveContract()).containsEntry("variant", "development");
            assertThat(component.saveContract().get("requiredFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("implementationNote", "selfTestResult");
            assertThat(component.saveContract().get("fieldContracts"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .hasSize(6);
            assertThat(component.saveContract()).containsEntry("completionCommand",
                    "development-item.node.complete-with-data");
            assertThat(component.saveContract()).containsEntry("completionSupported", true);
            assertThat(component.saveContract().get("completionRequiredMetadata"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("ownerId", "startDate", "endDate");
            assertThat(component.saveContract().get("writableFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .doesNotContain("title", "status", "ownerId", "startDate", "endDate");
        });
    }

    @Test
    void storyAcceptanceWorkbenchPublishesCompletionContractWithoutOpeningProtectedFields() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO story = new DevelopmentItemWorkflowDetailDTO();
        story.setItemType("STORY");
        story.setId(34L);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(387L);
        node.setVersion(1);
        node.setRuntimeComponents(List.of("story-node-workbench"));
        node.setComponentConfigs(Map.of("story-node-workbench",
                mapper.valueToTree(Map.of("variant", "acceptance"))));
        node.setFieldValues(Map.of("__components", mapper.valueToTree(Map.of(
                "story-node-workbench", Map.of("acceptanceConclusion", "PASS")))));

        AiWorkflowContextDTO.Component component = reader(new AiContextReferenceProjector(policy), policy).read(
                DevelopmentItemType.STORY, story, node, null, "story-node-workbench", new ArrayList<>())
                .get(0);

        assertThat(component.saveContract()).containsEntry("completionCommand",
                "development-item.node.complete-with-data");
        assertThat(component.saveContract()).containsEntry("completionSupported", true);
        assertThat(component.saveContract().get("completionRequiredMetadata"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("ownerId", "startDate", "endDate");
        assertThat(component.saveContract().get("writableFields"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .containsExactly("acceptanceConclusion", "acceptanceNote")
                .doesNotContain("title", "status", "ownerId", "startDate", "endDate");
    }

    @Test
    void storyTestingPublishesAStableSaveContractForAiTestResults() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO story = new DevelopmentItemWorkflowDetailDTO();
        story.setItemType("STORY");
        story.setId(34L);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(386L);
        node.setVersion(1);
        node.setRuntimeComponents(List.of("story-testing"));
        node.setComponentConfigs(Map.of("story-testing",
                mapper.valueToTree(Map.of("testingResultsEnabled", true))));
        node.setFieldValues(Map.of("__components", mapper.valueToTree(Map.of(
                "story-testing", Map.of("testStatus", "IN_PROGRESS")))));

        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        List<AiWorkflowContextDTO.Component> components = reader(real, policy).read(
                DevelopmentItemType.STORY, story, node, null, "story-testing", new ArrayList<>());

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.saveContract()).isNotNull();
            assertThat(component.saveContract()).containsEntry("command", "development-item.node.field.update");
            assertThat(component.saveContract()).containsEntry("itemType", "STORY");
            assertThat(component.saveContract()).containsEntry("itemId", 34L);
            assertThat(component.saveContract()).containsEntry("nodeId", 386L);
            assertThat(component.saveContract()).containsEntry("version", 1);
            assertThat(component.saveContract()).containsEntry("componentKey", "story-testing");
            assertThat(component.saveContract()).containsEntry("fieldValuesPath", "__components.story-testing");
            assertThat(component.saveContract()).containsEntry("contractVersion", "node-detail/v1");
            assertThat(component.saveContract()).containsEntry("componentContractVersion", "story-testing/v1");
            assertThat(component.saveContract().get("writableFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("buildVersion", "reportUrl", "residualIssues", "testStatus");
            assertThat(component.saveContract().get("requiredFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .isEmpty();
            assertThat(component.saveContract().get("protectedFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("dueDate", "ownerId", "startDate", "status", "title");
            assertThat(component.saveContract().get("fieldContracts"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly(
                            Map.of("key", "buildVersion", "type", "string", "required", false,
                                    "nullable", true, "collection", false),
                            Map.of("key", "reportUrl", "type", "string", "required", false,
                                    "nullable", true, "collection", false),
                            Map.of("key", "residualIssues", "type", "array", "required", false,
                                    "nullable", true, "collection", true),
                            Map.of("key", "testStatus", "type", "string", "required", false,
                                    "nullable", true, "collection", false));
            assertThat(component.saveContract()).containsEntry("completionCommand",
                    "development-item.node.complete-with-data");
            assertThat(component.saveContract()).containsEntry("completionSupported", true);
            assertThat(component.saveContract().get("completionRequiredMetadata"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("ownerId", "startDate", "endDate");
            assertThat(component.saveContract().get("writableFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .doesNotContain("title", "status", "ownerId", "startDate", "endDate");
        });
    }

    @Test
    void disabledStoryTestingDoesNotPublishAWriteContract() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO story = new DevelopmentItemWorkflowDetailDTO();
        story.setItemType("STORY");
        story.setId(34L);

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(386L);
        node.setVersion(1);
        node.setRuntimeComponents(List.of("story-testing"));
        node.setComponentConfigs(Map.of("story-testing",
                mapper.valueToTree(Map.of("testingResultsEnabled", false))));

        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        List<AiWorkflowContextDTO.Component> components = reader(real, policy).read(
                DevelopmentItemType.STORY, story, node, null, "story-testing", new ArrayList<>());

        assertThat(components).singleElement().satisfies(component ->
                assertThat(component.saveContract()).isNull());
    }

    @Test
    void topicResearchPublishesPersistedNodeFieldContract() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO topic = new DevelopmentItemWorkflowDetailDTO();
        topic.setItemType("TOPIC");
        topic.setId(12L);
        topic.setProjectMountNodeKey("develop");

        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(301L);
        node.setVersion(4);
        node.setRuntimeComponents(List.of("topic-research"));
        node.setFields(List.of(
                new WorkflowFieldDefinition("needed", "是否需要调研", WorkflowFieldType.SINGLE_SELECT, true,
                        List.of("YES", "NO")),
                new WorkflowFieldDefinition("goal", "调研目标", WorkflowFieldType.TEXTAREA, false, List.of())));
        node.setFieldValues(Map.of("__components", mapper.valueToTree(Map.of(
                "topic-research", Map.of("needed", "YES")))));

        AiContextReferenceProjector real = new AiContextReferenceProjector(policy);
        List<AiWorkflowContextDTO.Component> components = reader(real, policy).read(
                DevelopmentItemType.TOPIC, topic, node, null, "topic-research", new ArrayList<>());

        assertThat(components).singleElement().satisfies(component -> {
            assertThat(component.saveContract()).isNotNull();
            assertThat(component.saveContract()).containsEntry("command", "development-item.node.field.update");
            assertThat(component.saveContract()).containsEntry("itemType", "TOPIC");
            assertThat(component.saveContract()).containsEntry("itemId", 12L);
            assertThat(component.saveContract()).containsEntry("nodeId", 301L);
            assertThat(component.saveContract()).containsEntry("version", 4);
            assertThat(component.saveContract()).containsEntry("componentKey", "topic-research");
            assertThat(component.saveContract()).containsEntry("sourceProjectNodeKey", "develop");
            assertThat(component.saveContract().get("writableFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("needed", "goal", "reportUrl", "skipReason");
            assertThat(component.saveContract()).containsEntry("contractVersion", "node-detail/v1");
            assertThat(component.saveContract()).containsEntry("completionCommand",
                    "development-item.node.complete-with-data");
            assertThat(component.saveContract()).containsEntry("completionSupported", true);
            assertThat(component.saveContract().get("requiredFields"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .containsExactly("needed");
            assertThat(component.saveContract().get("fieldContracts"))
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                    .anySatisfy(value -> assertThat(value).isEqualTo(Map.of(
                            "key", "needed", "label", "是否需要调研", "type", "enum", "required", true,
                            "nullable", false, "collection", false, "options", List.of("YES", "NO"))));
        });
    }

    @Test
    void requirementNodeWorkbenchPublishesCompletionContractFromTemplateFields() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO requirement = item(null);
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(401L);
        node.setVersion(6);
        node.setRuntimeComponents(List.of("requirement-node-workbench"));
        node.setFields(List.of(new WorkflowFieldDefinition(
                "acceptanceCriteria", "验收标准", WorkflowFieldType.TEXTAREA, true, List.of())));
        node.setFieldValues(Map.of("__components", mapper.valueToTree(Map.of(
                "requirement-node-workbench", Map.of("acceptanceCriteria", "可验收")))));

        AiWorkflowContextDTO.Component component = reader(new AiContextReferenceProjector(policy), policy).read(
                DevelopmentItemType.REQUIREMENT, requirement, node, null, "requirement-node-workbench",
                new ArrayList<>()).get(0);

        assertThat(component.saveContract()).containsEntry("contractVersion", "node-detail/v1");
        assertThat(component.saveContract()).containsEntry("version", 6);
        assertThat(component.saveContract()).containsEntry("completionSupported", true);
        assertThat(component.saveContract().get("protectedFields"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .contains("status", "ownerId", "startDate", "endDate");
        assertThat(component.saveContract().get("fieldContracts"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .anySatisfy(value -> assertThat(value).isEqualTo(Map.of(
                        "key", "acceptanceCriteria", "label", "验收标准", "type", "textarea", "required", true,
                        "nullable", false, "collection", false)));
    }

    @Test
    void unsupportedStoryWorkbenchVariantDoesNotPublishCompletionCapability() {
        var mapper = JsonMapper.builder().build();
        DevelopmentItemWorkflowDetailDTO story = new DevelopmentItemWorkflowDetailDTO();
        story.setItemType("STORY");
        story.setId(34L);
        DevelopmentItemWorkflowNodeDTO node = new DevelopmentItemWorkflowNodeDTO();
        node.setId(399L);
        node.setVersion(1);
        node.setRuntimeComponents(List.of("story-node-workbench"));
        node.setComponentConfigs(Map.of("story-node-workbench",
                mapper.valueToTree(Map.of("variant", "unknown"))));

        assertThat(reader(new AiContextReferenceProjector(policy), policy).read(
                DevelopmentItemType.STORY, story, node, null, "story-node-workbench", new ArrayList<>()))
                .singleElement().satisfies(component -> assertThat(component.saveContract()).isNull());
    }
}
