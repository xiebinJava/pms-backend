package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.dto.request.WorkflowNodeFieldValuesCmd;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeFieldValueDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeFieldAttachmentMapper;
import com.brad.pms.mapper.ProjectNodeFieldValueMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.storage.FileStorageService;
import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.brad.pms.workflow.WorkflowFieldType;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeCustomFieldServiceTest {
    private final ProjectNodeFieldValueMapper valueMapper = mock(ProjectNodeFieldValueMapper.class);
    private final ProjectNodeFieldAttachmentMapper attachmentMapper = mock(ProjectNodeFieldAttachmentMapper.class);
    private final ProjectNodeMapper nodeMapper = mock(ProjectNodeMapper.class);
    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectPermissionService permissionService = mock(ProjectPermissionService.class);
    private final WorkflowTemplateService workflowTemplateService = mock(WorkflowTemplateService.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final OperationLogService operationLogService = mock(OperationLogService.class);
    private final NodeCustomFieldService service = new NodeCustomFieldService(valueMapper, attachmentMapper, nodeMapper,
            projectMapper, permissionService, workflowTemplateService, fileStorageService, operationLogService,
            new ObjectMapper());

    @BeforeEach
    void initTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectNodeFieldValueDO.class);
        TableInfoHelper.initTableInfo(assistant, com.brad.pms.entity.ProjectNodeFieldAttachmentDO.class);
    }

    @Test
    void clearingAnOptionalDateOverwritesThePreviouslySavedValueWithNull() {
        ProjectDO project = new ProjectDO();
        project.setWorkflowTemplateVersionId(55L);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setNodeKey("entry");
        ProjectNodeFieldValueDO existing = new ProjectNodeFieldValueDO();
        existing.setId(9L);
        existing.setProjectId(3L);
        existing.setNodeId(7L);
        existing.setFieldKey("releaseDate");
        existing.setValueJson("\"2026-09-12\"");
        existing.setVersion(2);
        WorkflowNodeDefinition definition = new WorkflowNodeDefinition("entry", "Entry", "", "", "",
                List.of(), List.of(new WorkflowFieldDefinition("releaseDate", "Release date", WorkflowFieldType.DATE,
                false, List.of())), false, List.of());
        Map<String, com.fasterxml.jackson.databind.JsonNode> values = new LinkedHashMap<>();
        values.put("releaseDate", NullNode.instance);
        WorkflowNodeFieldValuesCmd cmd = new WorkflowNodeFieldValuesCmd();
        cmd.setValues(values);
        cmd.setVersions(Map.of("releaseDate", 2));

        when(permissionService.requireProjectReadable(3L)).thenReturn(project);
        when(permissionService.requireManageableNode(3L, 7L, "编辑节点字段")).thenReturn(node);
        when(workflowTemplateService.getNodeDefinition(55L, "entry")).thenReturn(definition);
        when(valueMapper.selectOne(any())).thenReturn(existing);
        when(valueMapper.selectList(any())).thenReturn(List.of());
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        when(valueMapper.updateById(existing)).thenReturn(1);

        service.save(3L, 7L, cmd);

        assertThat(existing.getValueJson()).isEqualTo("null");
        verify(valueMapper).updateById(existing);
    }
}
