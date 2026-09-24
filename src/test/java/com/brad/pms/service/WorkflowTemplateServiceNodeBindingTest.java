package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.WorkflowTemplateSaveCmd;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectTypeMapper;
import com.brad.pms.mapper.WorkflowTemplateMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

@ExtendWith(MockitoExtension.class)
class WorkflowTemplateServiceNodeBindingTest {

    @Mock ProjectTypeMapper projectTypeMapper;
    @Mock WorkflowTemplateMapper templateMapper;
    @Mock WorkflowTemplateVersionMapper versionMapper;
    @Mock ProjectMapper projectMapper;
    @Mock OperationLogService operationLogService;
    @Spy ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    @InjectMocks WorkflowTemplateService service;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "workflow-node-binding-test");
        TableInfoHelper.initTableInfo(assistant, ProjectTypeDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateVersionDO.class);
    }

    @Test
    void listsSelectablePublishedNodesPinnedArchivedNodesAndCompatibilityNodes() throws Exception {
        ProjectTypeDO general = type(1L, "general", true);
        ProjectTypeDO partner = type(2L, "partner", true);
        ProjectTypeDO topicOnly = type(3L, "topic-management", false);
        WorkflowTemplateVersionDO publishedGeneral = version(101L, 11L, "PUBLISHED", definitionJson(
                node("without-component", "无组件节点", false),
                node("duplicate-a", "同名节点", false),
                node("shared", "共享节点", false)));
        WorkflowTemplateVersionDO publishedPartner = version(102L, 12L, "PUBLISHED", definitionJson(
                node("with-component", "有组件节点", true),
                node("duplicate-b", "同名节点", true),
                node("shared", "共享节点", true)));
        WorkflowTemplateVersionDO archivedPinned = version(103L, 13L, "ARCHIVED", definitionJson(
                node("archived-only", "历史模板节点", false)));
        when(projectTypeMapper.selectList(any())).thenReturn(List.of(general, partner, topicOnly));
        when(templateMapper.selectList(any())).thenReturn(List.of(template(11L, 1L), template(12L, 2L)));
        when(versionMapper.selectList(any())).thenReturn(List.of(publishedGeneral, publishedPartner),
                List.of(archivedPinned));
        stubActivePinnedVersionIds(List.of(103L));

        List<JsonNode> options = invokeOptions();
        List<String> keys = options.stream().map(option -> option.path("key").asText()).toList();

        assertThat(keys).contains("without-component", "with-component", "archived-only", "develop");
        assertThat(keys).doesNotContain("topic-process-template-node");
        assertThat(keys.stream().filter("shared"::equals)).hasSize(1);
        assertThat(option(options, "duplicate-a").path("name").asText()).isEqualTo("同名节点 (duplicate-a)");
        assertThat(option(options, "duplicate-b").path("name").asText()).isEqualTo("同名节点 (duplicate-b)");
    }

    @Test
    void alwaysIncludesTheBuiltInCompatibilityNodeWhenNoProjectTemplateIsSelectable() throws Exception {
        when(projectTypeMapper.selectList(any())).thenReturn(List.of());
        stubActivePinnedVersionIds(List.of());

        List<JsonNode> options = invokeOptions();

        assertThat(options).anySatisfy(option -> {
            assertThat(option.path("key").asText()).isEqualTo("develop");
            assertThat(option.path("name").asText()).isEqualTo("开发测试与项目控制");
        });
    }

    @Test
    void usesDevelopForAnUnconfiguredTopicWorkflowAndKeepsAConfiguredStableKey() throws Exception {
        ProjectTypeDO topic = type(7L, "topic-management", false);
        topic.setDefaultTemplateVersionId(701L);
        when(projectTypeMapper.selectOne(any())).thenReturn(topic);
        when(versionMapper.selectById(701L)).thenReturn(version(701L, 70L, "PUBLISHED",
                """
                        {"schemaVersion":1,"nodes":[{"key":"intake","name":"调研","description":"","deliverable":"","roles":"","components":[],"fields":[],"projectBasicInfo":false,"projectBasicInfoFields":[]}]}
                        """));
        when(templateMapper.selectById(70L)).thenReturn(template(70L, 7L));

        assertThat(invokeString("resolveTopicSourceProjectNodeKey")).isEqualTo("develop");

        when(versionMapper.selectById(701L)).thenReturn(version(701L, 70L, "PUBLISHED",
                """
                        {"schemaVersion":1,"sourceProjectNodeKey":"design","nodes":[{"key":"intake","name":"调研","description":"","deliverable":"","roles":"","components":[],"fields":[],"projectBasicInfo":false,"projectBasicInfoFields":[]}]}
                        """));
        assertThat(invokeString("resolveTopicSourceProjectNodeKey")).isEqualTo("design");
    }

    @Test
    void rejectsUnknownTopicBindingKeysButAcceptsAndPersistsASelectableKey() throws Exception {
        ProjectTypeDO topic = type(8L, "topic-management", false);
        when(projectTypeMapper.selectById(8L)).thenReturn(topic);
        when(projectTypeMapper.selectList(any())).thenReturn(List.of());
        stubActivePinnedVersionIds(List.of());

        WorkflowTemplateSaveCmd invalid = saveCommand(topic.getId(), "not-a-project-node");
        assertThatThrownBy(() -> service.saveDraft(null, invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目节点");

        WorkflowTemplateSaveCmd valid = saveCommand(topic.getId(), "develop");
        when(templateMapper.insert(any(WorkflowTemplateDO.class))).thenAnswer(invocation -> {
            ((WorkflowTemplateDO) invocation.getArgument(0)).setId(80L);
            return 1;
        });
        when(versionMapper.insert(any(WorkflowTemplateVersionDO.class))).thenReturn(1);
        when(templateMapper.updateById(any(WorkflowTemplateDO.class))).thenReturn(1);

        service.saveDraft(null, valid);

        verify(versionMapper).insert(argThat((WorkflowTemplateVersionDO version) ->
                version.getDefinitionJson().contains("\"sourceProjectNodeKey\":\"develop\"")));
    }

    @Test
    void activeProjectVersionQueryExcludesClosedAndDeletedProjects() throws Exception {
        Optional<Method> method = Arrays.stream(ProjectMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals("selectActiveWorkflowTemplateVersionIds"))
                .findFirst();
        assertThat(method).as("active project template reference query").isPresent();

        Select select = method.orElseThrow().getAnnotation(Select.class);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");
        assertThat(sql).contains("deleted = FALSE", "status IS NULL", "status = 0", "status = 1",
                "workflow_template_version_id IS NOT NULL");
    }

    private List<JsonNode> invokeOptions() throws Exception {
        Object result = invoke("listTopicSourceNodeOptions");
        return StreamSupport.stream(objectMapper.valueToTree(result).spliterator(), false).toList();
    }

    private String invokeString(String methodName) throws Exception {
        return (String) invoke(methodName);
    }

    private Object invoke(String methodName) throws Exception {
        Optional<Method> method = Arrays.stream(WorkflowTemplateService.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName) && candidate.getParameterCount() == 0)
                .findFirst();
        assertThat(method).as("WorkflowTemplateService.%s()", methodName).isPresent();
        return method.orElseThrow().invoke(service);
    }

    @SuppressWarnings("unchecked")
    private void stubActivePinnedVersionIds(List<Long> ids) throws Exception {
        Optional<Method> method = Arrays.stream(ProjectMapper.class.getMethods())
                .filter(candidate -> candidate.getName().equals("selectActiveWorkflowTemplateVersionIds"))
                .findFirst();
        assertThat(method).as("active project template reference query").isPresent();
        when((List<Long>) method.orElseThrow().invoke(projectMapper)).thenReturn(ids);
    }

    private WorkflowTemplateSaveCmd saveCommand(Long typeId, String sourceKey) throws Exception {
        String json = """
                {"schemaVersion":1,"sourceProjectNodeKey":"%s","nodes":[{"key":"intake",
                "name":"调研","description":"","deliverable":"","roles":"","components":[],
                "fields":[],"projectBasicInfo":false,"projectBasicInfoFields":[]}]}
                """.formatted(sourceKey);
        WorkflowTemplateSaveCmd command = new WorkflowTemplateSaveCmd();
        command.setProjectTypeId(typeId);
        command.setName("专题流程");
        command.setDefinition(objectMapper.readValue(json, WorkflowTemplateDefinition.class));
        return command;
    }

    private String definitionJson(WorkflowNodeDefinition... nodes) throws Exception {
        return objectMapper.writeValueAsString(new WorkflowTemplateDefinition(1, List.of(nodes)));
    }

    private static JsonNode option(List<JsonNode> options, String key) {
        return options.stream().filter(option -> key.equals(option.path("key").asText())).findFirst().orElseThrow();
    }

    private static ProjectTypeDO type(Long id, String code, boolean projectCreationEnabled) {
        ProjectTypeDO type = new ProjectTypeDO();
        type.setId(id);
        type.setCode(code);
        type.setName(code);
        type.setStatus(1);
        type.setProjectCreationEnabled(projectCreationEnabled);
        return type;
    }

    private static WorkflowTemplateDO template(Long id, Long typeId) {
        WorkflowTemplateDO template = new WorkflowTemplateDO();
        template.setId(id);
        template.setProjectTypeId(typeId);
        return template;
    }

    private static WorkflowTemplateVersionDO version(Long id, Long templateId, String status, String definitionJson) {
        WorkflowTemplateVersionDO version = new WorkflowTemplateVersionDO();
        version.setId(id);
        version.setTemplateId(templateId);
        version.setStatus(status);
        version.setVersionNo(1);
        version.setDefinitionJson(definitionJson);
        return version;
    }

    private static WorkflowNodeDefinition node(String key, String name, boolean developmentComponent) {
        return new WorkflowNodeDefinition(key, name, "", "", "",
                developmentComponent ? List.of("development-control") : List.of(), List.of(), false, List.of());
    }
}
