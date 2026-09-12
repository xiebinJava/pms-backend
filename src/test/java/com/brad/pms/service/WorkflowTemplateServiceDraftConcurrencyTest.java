package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.WorkflowTemplateSaveCmd;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectTypeMapper;
import com.brad.pms.mapper.WorkflowTemplateMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.BuiltInWorkflowTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTemplateServiceDraftConcurrencyTest {
    @Mock ProjectTypeMapper projectTypeMapper;
    @Mock WorkflowTemplateMapper templateMapper;
    @Mock WorkflowTemplateVersionMapper versionMapper;
    @Mock OperationLogService operationLogService;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WorkflowTemplateService service;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "workflow-draft-concurrency-test");
        TableInfoHelper.initTableInfo(assistant, ProjectTypeDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateDO.class);
        TableInfoHelper.initTableInfo(assistant, WorkflowTemplateVersionDO.class);
    }

    @Test
    void rejectsAStaleEditorBeforeOverwritingTheCurrentDraft() {
        stubExistingTemplateAndDraft();

        assertThatThrownBy(() -> service.saveDraft(7L, command(4)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("草稿已被其他人修改")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);

        verify(versionMapper, never()).updateById(any(WorkflowTemplateVersionDO.class));
        verify(templateMapper, never()).updateById(any(WorkflowTemplateDO.class));
    }

    @Test
    void reportsOptimisticLockConflictWhenTheDraftChangesDuringTheSave() throws Exception {
        stubExistingTemplateAndDraft();
        when(templateMapper.updateById(any(WorkflowTemplateDO.class))).thenReturn(1);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(versionMapper.updateById(any(WorkflowTemplateVersionDO.class))).thenReturn(0);

        assertThatThrownBy(() -> service.saveDraft(7L, command(5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("草稿已被其他人修改")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);
    }

    @Test
    void reportsConflictWhenAnotherEditorCreatesTheFirstDraftAtTheSameTime() throws Exception {
        WorkflowTemplateDO template = new WorkflowTemplateDO();
        template.setId(7L);
        template.setProjectTypeId(2L);
        template.setLatestVersionNo(3);
        when(templateMapper.selectById(7L)).thenReturn(template);
        when(templateMapper.updateById(any(WorkflowTemplateDO.class))).thenReturn(1);
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doThrow(new DuplicateKeyException("duplicate draft version"))
                .when(versionMapper).insert(any(WorkflowTemplateVersionDO.class));

        assertThatThrownBy(() -> service.saveDraft(7L, command(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("草稿已被其他人创建")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);
    }

    @Test
    void reportsConflictWhenTheDraftInsertAffectsNoRows() throws Exception {
        WorkflowTemplateDO template = new WorkflowTemplateDO();
        template.setId(7L);
        template.setProjectTypeId(2L);
        template.setLatestVersionNo(3);
        when(templateMapper.selectById(7L)).thenReturn(template);
        when(templateMapper.updateById(any(WorkflowTemplateDO.class))).thenReturn(1);
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(versionMapper.insert(any(WorkflowTemplateVersionDO.class))).thenReturn(0);

        assertThatThrownBy(() -> service.saveDraft(7L, command(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("草稿未能保存")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);
    }

    @Test
    void doesNotSaveDraftWhenTheTemplateUpdateAffectsNoRows() {
        stubExistingTemplateAndDraft();
        when(templateMapper.updateById(any(WorkflowTemplateDO.class))).thenReturn(0);

        assertThatThrownBy(() -> service.saveDraft(7L, command(5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程模板无法更新")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);

        verify(versionMapper, never()).updateById(any(WorkflowTemplateVersionDO.class));
    }

    private void stubExistingTemplateAndDraft() {
        WorkflowTemplateDO template = new WorkflowTemplateDO();
        template.setId(7L);
        template.setProjectTypeId(2L);
        template.setLatestVersionNo(3);
        when(templateMapper.selectById(7L)).thenReturn(template);

        WorkflowTemplateVersionDO draft = new WorkflowTemplateVersionDO();
        draft.setId(13L);
        draft.setTemplateId(7L);
        draft.setVersionNo(3);
        draft.setStatus("DRAFT");
        draft.setVersion(5);
        when(versionMapper.selectOne(any())).thenReturn(draft);
    }

    private static WorkflowTemplateSaveCmd command(Integer expectedDraftRevision) {
        WorkflowTemplateSaveCmd command = new WorkflowTemplateSaveCmd();
        command.setName("流程草稿");
        command.setDefinition(BuiltInWorkflowTemplate.compatibilityDefinition());
        command.setExpectedDraftRevision(expectedDraftRevision);
        return command;
    }
}
