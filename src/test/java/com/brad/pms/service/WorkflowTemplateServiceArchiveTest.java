package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.audit.AuditEvent;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectTypeMapper;
import com.brad.pms.mapper.WorkflowTemplateMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTemplateServiceArchiveTest {
    @Mock ProjectTypeMapper projectTypeMapper;
    @Mock WorkflowTemplateMapper templateMapper;
    @Mock WorkflowTemplateVersionMapper versionMapper;
    @Mock OperationLogService operationLogService;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WorkflowTemplateService service;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "workflow-archive-test");
        TableInfoHelper.initTableInfo(assistant, ProjectTypeDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateVersionDO.class);
    }

    @Test
    void archivesPublishedNonDefaultVersionWithoutDeletingItsDefinition() {
        WorkflowTemplateDO template = template(7L, "CUSTOM");
        WorkflowTemplateVersionDO version = version(71L, 7L, 2, "PUBLISHED");
        version.setDefinitionJson("{\"nodes\":[]}");
        when(templateMapper.selectActiveByIdForUpdate(7L)).thenReturn(template);
        when(versionMapper.selectByIdForUpdate(71L)).thenReturn(version);
        when(projectTypeMapper.selectCount(any())).thenReturn(0L);
        when(versionMapper.updateById(version)).thenReturn(1);

        service.archiveVersion(7L, 71L);

        assertThat(version.getStatus()).isEqualTo("ARCHIVED");
        assertThat(version.getDefinitionJson()).isEqualTo("{\"nodes\":[]}");
        verify(versionMapper).updateById(version);
        verify(versionMapper, never()).deleteById(71L);
        verify(operationLogService).record(any(AuditEvent.class));
    }

    @Test
    void rejectsArchivingTheCurrentDefaultVersion() {
        when(templateMapper.selectActiveByIdForUpdate(7L)).thenReturn(template(7L, "CUSTOM"));
        when(versionMapper.selectByIdForUpdate(71L)).thenReturn(version(71L, 7L, 1, "PUBLISHED"));
        when(projectTypeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.archiveVersion(7L, 71L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("默认版本")
                .hasMessageContaining("先更改");

        verify(versionMapper, never()).updateById(any(WorkflowTemplateVersionDO.class));
    }

    @Test
    void rejectsArchivingDraftsAndVersionsOwnedByAnotherTemplate() {
        when(templateMapper.selectActiveByIdForUpdate(7L)).thenReturn(template(7L, "CUSTOM"));
        when(versionMapper.selectByIdForUpdate(72L)).thenReturn(version(72L, 7L, 3, "DRAFT"));
        when(versionMapper.selectByIdForUpdate(73L)).thenReturn(version(73L, 8L, 1, "PUBLISHED"));

        assertThatThrownBy(() -> service.archiveVersion(7L, 72L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已发布");
        assertThatThrownBy(() -> service.archiveVersion(7L, 73L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不属于");

        verify(versionMapper, never()).updateById(any(WorkflowTemplateVersionDO.class));
    }

    @Test
    void archivesCustomTemplateSoftlyAndLeavesVersionsForExistingProjectBindings() {
        WorkflowTemplateDO template = template(7L, "CUSTOM");
        when(templateMapper.selectActiveByIdForUpdate(7L)).thenReturn(template);
        when(versionMapper.selectList(any())).thenReturn(List.of(version(71L, 7L, 1, "PUBLISHED")));
        when(projectTypeMapper.selectCount(any())).thenReturn(0L);
        when(templateMapper.deleteById(7L)).thenReturn(1);

        service.archiveTemplate(7L);

        verify(templateMapper).deleteById(7L);
        verify(versionMapper, never()).delete(any());
        verify(operationLogService).record(any(AuditEvent.class));
    }

    @Test
    void doesNotAllowArchivingTheSeedTemplateOrATemplateContainingTheDefaultVersion() {
        when(templateMapper.selectActiveByIdForUpdate(1L)).thenReturn(template(1L, "current-process"));
        when(templateMapper.selectActiveByIdForUpdate(7L)).thenReturn(template(7L, "CUSTOM"));
        when(versionMapper.selectList(any())).thenReturn(List.of(version(71L, 7L, 1, "PUBLISHED")));
        when(projectTypeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.archiveTemplate(1L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("内置模板");
        assertThatThrownBy(() -> service.archiveTemplate(7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("默认版本");

        verify(templateMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void settingDefaultRechecksTheVersionAfterTakingTheTemplateLock() {
        ProjectTypeDO type = new ProjectTypeDO();
        type.setId(3L);
        type.setStatus(1);
        WorkflowTemplateVersionDO versionBeforeLock = version(71L, 7L, 2, "PUBLISHED");
        WorkflowTemplateVersionDO versionAfterLock = version(71L, 7L, 2, "ARCHIVED");
        when(projectTypeMapper.selectById(3L)).thenReturn(type);
        when(versionMapper.selectById(71L)).thenReturn(versionBeforeLock);
        when(templateMapper.selectActiveByIdForUpdate(7L)).thenReturn(template(7L, "CUSTOM"));
        when(versionMapper.selectByIdForUpdate(71L)).thenReturn(versionAfterLock);

        assertThatThrownBy(() -> service.setDefaultTemplate(3L, 71L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已发布");

        var order = inOrder(templateMapper, versionMapper);
        order.verify(templateMapper).selectActiveByIdForUpdate(7L);
        order.verify(versionMapper).selectByIdForUpdate(71L);
        verify(projectTypeMapper, never()).updateById(any(ProjectTypeDO.class));
    }

    private static WorkflowTemplateDO template(Long id, String code) {
        WorkflowTemplateDO template = new WorkflowTemplateDO();
        template.setId(id);
        template.setCode(code);
        template.setName("模板");
        template.setProjectTypeId(3L);
        template.setDeleted(false);
        return template;
    }

    private static WorkflowTemplateVersionDO version(Long id, Long templateId, int versionNo, String status) {
        WorkflowTemplateVersionDO version = new WorkflowTemplateVersionDO();
        version.setId(id);
        version.setTemplateId(templateId);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        return version;
    }
}
