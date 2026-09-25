package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectTypeDTO;
import com.brad.pms.dto.response.WorkflowTemplateOptionsDTO;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectTypeMapper;
import com.brad.pms.mapper.WorkflowTemplateMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTemplateServiceSelectionTest {
    @Mock ProjectTypeMapper projectTypeMapper;
    @Mock WorkflowTemplateMapper templateMapper;
    @Mock WorkflowTemplateVersionMapper versionMapper;
    @Mock OperationLogService operationLogService;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WorkflowTemplateService service;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "workflow-test");
        TableInfoHelper.initTableInfo(assistant, ProjectTypeDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateVersionDO.class);
    }

    @Test
    void resolvesTypeDefaultAndBindsOnlyThePublishedVersionForThatType() {
        ProjectTypeDO type = type(3L, 21L);
        WorkflowTemplateVersionDO version = version(21L, 8L, "PUBLISHED");
        WorkflowTemplateDO template = template(8L, 3L);
        when(projectTypeMapper.selectOne(any())).thenReturn(type);
        when(versionMapper.selectById(21L)).thenReturn(version);
        when(templateMapper.selectById(8L)).thenReturn(template);

        var binding = service.resolveForProjectCreation(null, null);

        assertThat(binding.projectType().getId()).isEqualTo(3L);
        assertThat(binding.version().getId()).isEqualTo(21L);
        assertThat(binding.template().getId()).isEqualTo(8L);
    }

    @Test
    void resolvesAnExplicitPublishedProcessTemplateInsteadOfTheDefault() {
        ProjectTypeDO type = type(4L, 21L);
        type.setCode("topic-management");
        type.setProjectCreationEnabled(false);
        WorkflowTemplateVersionDO selected = version(31L, 9L, "PUBLISHED");
        WorkflowTemplateDO template = template(9L, 4L);
        when(projectTypeMapper.selectOne(any())).thenReturn(type);
        when(versionMapper.selectById(31L)).thenReturn(selected);
        when(templateMapper.selectById(9L)).thenReturn(template);

        var binding = service.resolveForProcessType("topic-management", 31L);

        assertThat(binding.projectType().getCode()).isEqualTo("topic-management");
        assertThat(binding.version().getId()).isEqualTo(31L);
        assertThat(binding.template().getId()).isEqualTo(9L);
    }

    @Test
    void rejectsDraftAndCrossTypeVersionsWhenTheCreatorSwitchesTemplates() {
        when(projectTypeMapper.selectById(3L)).thenReturn(type(3L, 21L));
        when(versionMapper.selectById(22L)).thenReturn(version(22L, 9L, "DRAFT"));
        when(versionMapper.selectById(23L)).thenReturn(version(23L, 10L, "PUBLISHED"));
        when(templateMapper.selectById(10L)).thenReturn(template(10L, 4L));

        assertThatThrownBy(() -> service.resolveForProjectCreation(3L, 22L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已发布");
        assertThatThrownBy(() -> service.resolveForProjectCreation(3L, 23L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不匹配");
    }

    @Test
    void listsEveryPublishedVersionAndMarksAnOlderDefaultPrecisely() {
        ProjectTypeDO type = type(3L, 21L);
        WorkflowTemplateDO template = template(8L, 3L);
        WorkflowTemplateVersionDO first = version(21L, 8L, "PUBLISHED");
        first.setVersionNo(1);
        WorkflowTemplateVersionDO latest = version(22L, 8L, "PUBLISHED");
        latest.setVersionNo(2);
        WorkflowTemplateVersionDO draft = version(23L, 8L, "DRAFT");
        draft.setVersionNo(3);
        draft.setVersion(7);
        WorkflowTemplateVersionDO archived = version(20L, 8L, "ARCHIVED");
        archived.setVersionNo(4);
        when(templateMapper.selectList(any())).thenReturn(java.util.List.of(template));
        when(projectTypeMapper.selectList(isNull())).thenReturn(java.util.List.of(type));
        when(versionMapper.selectList(any())).thenReturn(java.util.List.of(archived, draft, latest, first));

        var summary = service.listTemplates(3L, true).get(0);

        assertThat(summary.getPublishedVersionId()).isEqualTo(22L);
        assertThat(summary.getPublishedVersions()).extracting(version -> version.getId())
                .containsExactly(21L, 22L);
        assertThat(summary.getVersions()).extracting(version -> version.getStatus())
                .containsExactly("PUBLISHED", "PUBLISHED", "DRAFT", "ARCHIVED");
        assertThat(summary.getVersions().get(0).getIsDefault()).isTrue();
        assertThat(summary.getDefaultTemplateVersionId()).isEqualTo(21L);
        assertThat(summary.getDefaultTemplate()).isTrue();
        assertThat(summary.getDraftRevision()).isEqualTo(7);
    }

    @Test
    void projectCreationOptionsExcludeTemplateOnlyProcessTypes() {
        ProjectTypeDO project = type(3L, 21L);
        project.setProjectCreationEnabled(true);
        ProjectTypeDO topic = type(4L, null);
        topic.setCode("topic-management");
        topic.setProjectCreationEnabled(false);
        when(projectTypeMapper.selectList(any())).thenReturn(java.util.List.of(project));
        when(projectTypeMapper.selectList(isNull())).thenReturn(java.util.List.of(project, topic));
        when(templateMapper.selectList(any())).thenReturn(java.util.List.of(template(8L, 3L), template(9L, 4L)));
        when(versionMapper.selectList(any())).thenReturn(java.util.List.of(
                version(31L, 8L, "PUBLISHED"), version(32L, 9L, "PUBLISHED")));

        WorkflowTemplateOptionsDTO options = service.options();

        assertThat(options.getProjectTypes()).extracting(ProjectTypeDTO::getCode)
                .containsExactly("general");
        assertThat(options.getTemplates()).extracting(item -> item.getProjectTypeId())
                .containsOnly(3L);
    }

    @Test
    void createsProcessTypeWithAnInternalCodeDerivedFromTheDatabaseId() {
        doAnswer(invocation -> {
            ProjectTypeDO inserted = invocation.getArgument(0);
            inserted.setId(42L);
            return 1;
        }).when(projectTypeMapper).insert(any(ProjectTypeDO.class));
        when(projectTypeMapper.updateById(any(ProjectTypeDO.class))).thenReturn(1);
        com.brad.pms.dto.request.ProjectTypeSaveCmd command = new com.brad.pms.dto.request.ProjectTypeSaveCmd();
        command.setName("故事流程");
        command.setDescription("用于配置故事交付");

        ProjectTypeDTO created = service.createProjectType(command);

        ArgumentCaptor<ProjectTypeDO> updatedType = ArgumentCaptor.forClass(ProjectTypeDO.class);
        verify(projectTypeMapper).updateById(updatedType.capture());
        assertThat(updatedType.getValue().getId()).isEqualTo(42L);
        assertThat(updatedType.getValue().getCode()).isEqualTo("process-type-42");
        assertThat(created.getCode()).isEqualTo("process-type-42");
        assertThat(created.getProjectCreationEnabled()).isFalse();
    }

    @Test
    void projectCreationRejectsTemplateOnlyProcessTypeEvenWhenPassedDirectly() {
        ProjectTypeDO topic = type(4L, null);
        topic.setCode("topic-management");
        topic.setProjectCreationEnabled(false);
        when(projectTypeMapper.selectById(4L)).thenReturn(topic);

        assertThatThrownBy(() -> service.resolveForProjectCreation(4L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可用于新建项目");
    }

    private static ProjectTypeDO type(Long id, Long defaultVersion) {
        ProjectTypeDO type = new ProjectTypeDO();
        type.setId(id);
        type.setCode(id == 3L ? "general" : "other");
        type.setName("项目类型");
        type.setStatus(1);
        type.setProjectCreationEnabled(true);
        type.setDefaultTemplateVersionId(defaultVersion);
        return type;
    }

    private static WorkflowTemplateVersionDO version(Long id, Long templateId, String status) {
        WorkflowTemplateVersionDO version = new WorkflowTemplateVersionDO();
        version.setId(id);
        version.setTemplateId(templateId);
        version.setStatus(status);
        version.setVersionNo(1);
        return version;
    }

    private static WorkflowTemplateDO template(Long id, Long projectTypeId) {
        WorkflowTemplateDO template = new WorkflowTemplateDO();
        template.setId(id);
        template.setProjectTypeId(projectTypeId);
        return template;
    }
}
