package com.brad.pms.service;

import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class DevelopmentItemWorkflowServiceFieldTest {
    private final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
    private final RequirementMapper requirementMapper = mock(RequirementMapper.class);
    private final ProjectNodeDevelopmentTopicMapper topicMapper = mock(ProjectNodeDevelopmentTopicMapper.class);
    private final ProjectNodeDevelopmentStoryMapper storyMapper = mock(ProjectNodeDevelopmentStoryMapper.class);
    private final ProjectNodeMapper projectNodeMapper = mock(ProjectNodeMapper.class);
    private final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
    private final DevelopmentItemWorkflowService service = new DevelopmentItemWorkflowService(
            mock(DevelopmentItemWorkflowMapper.class), mock(DevelopmentItemWorkflowNodeMapper.class),
            mock(DevelopmentItemTaskMapper.class), topicMapper,
            storyMapper, requirementMapper, projectNodeMapper,
            mock(ProjectNodeIterationPlanMapper.class), mock(WorkflowTemplateVersionMapper.class),
            permissionService, mock(UserService.class), workflowTemplateService,
            mock(WorkflowComponentBindingService.class), mock(ProjectMemberAssignmentService.class), new ObjectMapper());

    @Test
    void nodeDtoIncludesTemplateFieldDefinitionsAndSavedValues() {
        WorkflowFieldDefinition field = new WorkflowFieldDefinition(
                "researchSummary", "调研结论", WorkflowFieldType.TEXTAREA, true, List.of());
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("research", "需求调研", "", "", "",
                List.of(), List.of(field), false, List.of());
        DevelopmentItemWorkflowNodeDO node = new DevelopmentItemWorkflowNodeDO();
        node.setId(21L);
        node.setNodeKey("research");
        node.setName("需求调研");
        node.setSort(0);
        node.setStatus(1);
        node.setOwnerId(9L);
        node.setVersion(3);
        node.setFieldValuesJson("{\"researchSummary\":\"已记录调研结论\"}");
        when(workflowTemplateService.getNodeDefinition(88L, "research")).thenReturn(definition);

        DevelopmentItemWorkflowNodeDTO result = ReflectionTestUtils.invokeMethod(
                service, "toNodeDTO", node, List.of(), Map.of(9L, new UserDO()),
                new WorkflowTemplateDefinition(1, List.of(definition)));

        assertThat(result.getFields()).containsExactly(field);
        assertThat(result.getFieldValues()).containsEntry("researchSummary",
                new ObjectMapper().getNodeFactory().textNode("已记录调研结论"));
    }

    @Test
    void requiredRequirementBindingsReadFromTheRequirementRecord() {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(7L);
        requirement.setTitle("需求标题");
        requirement.setDescription(null);
        requirement.setPriority(1);
        requirement.setOwnerId(9L);
        requirement.setStatus("IN_PROGRESS");
        requirement.setDeleted(false);
        when(requirementMapper.selectById(requirement.getId())).thenReturn(requirement);

        WorkflowFieldDefinition description = new WorkflowFieldDefinition(
                "description", "需求描述", WorkflowFieldType.TEXTAREA, true, List.of(), true,
                "requirement.description", true);
        WorkflowFieldDefinition owner = new WorkflowFieldDefinition(
                "owner", "需求负责人", WorkflowFieldType.PERSON, true, List.of(), true,
                "requirement.owner", false);
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("intake", "需求录入", "", "", "",
                List.of(), List.of(description, owner), false, List.of(), List.of("fields"));

        Object context = ReflectionTestUtils.invokeMethod(
                service, "loadContext", DevelopmentItemType.REQUIREMENT, 7L);
        List<String> missing = ReflectionTestUtils.invokeMethod(
                service, "missingRequiredBoundFields", context, definition);

        assertThat(missing).containsExactly("需求描述");
    }

    @Test
    void topicProjectBindingReadsTheAssociatedProjectName() {
        ProjectDO project = new ProjectDO();
        project.setId(78L);
        project.setName("项目 A");
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(7L);
        topic.setTitle("专题一");
        topic.setProjectId(78L);
        topic.setNodeId(88L);
        topic.setOwnerId(9L);
        topic.setDeleted(false);
        ProjectNodeDO sourceNode = new ProjectNodeDO();
        sourceNode.setId(88L);
        sourceNode.setProjectId(78L);
        when(topicMapper.selectById(7L)).thenReturn(topic);
        when(storyMapper.selectList(any())).thenReturn(List.of());
        when(projectNodeMapper.selectById(88L)).thenReturn(sourceNode);
        when(permissionService.requireProjectReadable(78L)).thenReturn(project);

        Object context = ReflectionTestUtils.invokeMethod(service, "loadContext", DevelopmentItemType.TOPIC, 7L);
        Object value = ReflectionTestUtils.invokeMethod(service, "boundFieldValue", context, "topic.project");

        assertThat(value).isEqualTo("项目 A");
    }

    @Test
    void savesEditableRequirementBindingsFromNodeFields() {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(7L);
        requirement.setTitle("旧标题");
        requirement.setDescription("旧描述");
        requirement.setPriority(1);
        requirement.setOwnerId(9L);
        requirement.setStatus("ACTIVE");
        requirement.setDeleted(false);
        when(requirementMapper.selectById(7L)).thenReturn(requirement);
        when(requirementMapper.selectByIdForUpdate(7L)).thenReturn(requirement);
        when(requirementMapper.updateById(requirement)).thenReturn(1);

        WorkflowFieldDefinition title = new WorkflowFieldDefinition(
                "title", "需求名称", WorkflowFieldType.TEXT, true, List.of(), true,
                "requirement.title", false);
        WorkflowFieldDefinition description = new WorkflowFieldDefinition(
                "description", "需求描述", WorkflowFieldType.TEXTAREA, true, List.of(), true,
                "requirement.description", true);
        WorkflowFieldDefinition priority = new WorkflowFieldDefinition(
                "priority", "需求优先级", WorkflowFieldType.NUMBER, true, List.of(), true,
                "requirement.priority", false);
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("intake", "需求录入", "", "", "",
                List.of(), List.of(title, description, priority), false, List.of(), List.of("fields"));

        Object context = ReflectionTestUtils.invokeMethod(
                service, "loadContext", DevelopmentItemType.REQUIREMENT, 7L);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> values = Map.of(
                "title", mapper.valueToTree("新标题"),
                "description", mapper.valueToTree("新描述"),
                "priority", mapper.valueToTree(3));

        ReflectionTestUtils.invokeMethod(service, "syncEditableRequirementBindings", context, definition, values);

        assertThat(requirement.getTitle()).isEqualTo("新标题");
        assertThat(requirement.getDescription()).isEqualTo("新描述");
        assertThat(requirement.getPriority()).isEqualTo(3);
    }

    @Test
    void keepsRuntimeComponentStateWhenSavingTemplateFields() {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, JsonNode> values = Map.of(
                "researchSummary", mapper.valueToTree("新的调研结论"));

        Map<String, JsonNode> merged = ReflectionTestUtils.invokeMethod(
                service, "preserveComponentValues", values,
                "{\"__components\":{\"requirement-receiving-analysis\":{\"decision\":\"PASS\"}}}");

        assertThat(merged).containsKey("researchSummary");
        assertThat(merged.get("__components").path("requirement-receiving-analysis").path("decision").asText())
                .isEqualTo("PASS");
    }

    @Test
    void acceptsClarificationWorkbenchStateWhenNodeUsesTheWorkbenchComponent() {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition(
                "clarify", "需求澄清", "", "", "", List.of(), List.of(), false, List.of(),
                List.of("component:requirement-node-workbench"), Map.of());
        Map<String, JsonNode> values = Map.of();
        Map<String, JsonNode> incoming = Map.of("__components", mapper.valueToTree(Map.of(
                "requirement-node-workbench", Map.of(
                        "background", "补充背景",
                        "acceptanceCriteria", "可验证"))));

        Map<String, JsonNode> merged = ReflectionTestUtils.invokeMethod(
                service, "preserveComponentValues", values,
                "{\"__components\":{\"requirement-receiving-analysis\":{\"decision\":\"PASS\"}}}",
                incoming, definition);

        assertThat(merged.get("__components").path("requirement-receiving-analysis").path("decision").asText())
                .isEqualTo("PASS");
        assertThat(merged.get("__components").path("requirement-node-workbench").path("background").asText())
                .isEqualTo("补充背景");
    }

}
