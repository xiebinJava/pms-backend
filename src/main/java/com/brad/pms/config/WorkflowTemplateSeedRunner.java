package com.brad.pms.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.entity.WorkflowTemplateDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectTypeMapper;
import com.brad.pms.mapper.WorkflowTemplateMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.BuiltInWorkflowTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Seeds the compatibility workflow and binds legacy projects without touching their nodes. */
@Component
@Order(-1)
@RequiredArgsConstructor
public class WorkflowTemplateSeedRunner implements CommandLineRunner {
    private final ProjectTypeMapper projectTypeMapper;
    private final WorkflowTemplateMapper templateMapper;
    private final WorkflowTemplateVersionMapper versionMapper;
    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        ProjectTypeDO general = projectTypeMapper.selectOne(new LambdaQueryWrapper<ProjectTypeDO>()
                .eq(ProjectTypeDO::getCode, "general"));
        if (general == null) {
            general = new ProjectTypeDO();
            general.setCode("general");
            general.setName("通用项目");
            general.setDescription("适用于未分类项目的默认类型");
            general.setStatus(1);
            general.setSort(0);
            general.setDeleted(false);
            projectTypeMapper.insert(general);
        }

        WorkflowTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<WorkflowTemplateDO>()
                .eq(WorkflowTemplateDO::getCode, "current-process"));
        if (template == null) {
            template = new WorkflowTemplateDO();
            template.setCode("current-process");
            template.setProjectTypeId(general.getId());
            template.setName("当前项目流程");
            template.setDescription("兼容现有项目的九阶段顺序流程");
            template.setLatestVersionNo(0);
            template.setDeleted(false);
            templateMapper.insert(template);
        }

        WorkflowTemplateVersionDO published = versionMapper.selectOne(new LambdaQueryWrapper<WorkflowTemplateVersionDO>()
                .eq(WorkflowTemplateVersionDO::getTemplateId, template.getId())
                .eq(WorkflowTemplateVersionDO::getStatus, "PUBLISHED")
                .orderByDesc(WorkflowTemplateVersionDO::getVersionNo)
                .last("LIMIT 1"));
        if (published == null) {
            published = new WorkflowTemplateVersionDO();
            published.setTemplateId(template.getId());
            published.setVersionNo(1);
            published.setStatus("PUBLISHED");
            published.setDefinitionJson(serializeCompatibilityDefinition());
            published.setPublishedAt(LocalDateTime.now());
            versionMapper.insert(published);
            template.setLatestVersionNo(Math.max(template.getLatestVersionNo() == null ? 0 : template.getLatestVersionNo(), 1));
            templateMapper.updateById(template);
        }

        if (general.getDefaultTemplateVersionId() == null) {
            general.setDefaultTemplateVersionId(published.getId());
            projectTypeMapper.updateById(general);
        }
        projectMapper.bindMissingWorkflowConfiguration(general.getId(), published.getId());
    }

    private String serializeCompatibilityDefinition() {
        try {
            return objectMapper.writeValueAsString(BuiltInWorkflowTemplate.compatibilityDefinition());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化默认流程模板", e);
        }
    }
}
