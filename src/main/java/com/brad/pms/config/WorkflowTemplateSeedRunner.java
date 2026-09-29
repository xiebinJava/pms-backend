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
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinitionNormalizer;
import com.brad.pms.workflow.WorkflowTemplateDefinitionValidator;
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
    private final WorkflowDefaultTemplateCatalog defaultTemplateCatalog;

    @Override
    @Transactional
    public void run(String... args) {
        ProjectTypeDO general = ensureProjectType(
                "general", "项目管理", "适用于普通项目的默认流程类型", 0, true);
        ProjectTypeDO topic = ensureProjectType(
                "topic-management", "专题管理", "用于配置专题管理流程模板", 10, false);
        ProjectTypeDO story = ensureProjectType(
                "story-management", "故事管理", "用于配置故事管理流程模板", 20, false);
        ProjectTypeDO requirement = ensureProjectType(
                "requirement-management", "需求管理", "用于配置需求管理流程模板", 30, false);

        WorkflowDefaultTemplateFile projectDefault = defaultTemplateCatalog.find("general")
                .orElseGet(this::compatibilityDefault);
        WorkflowTemplateVersionDO projectPublished = ensureDefaultTemplate(general, projectDefault);
        projectMapper.bindMissingWorkflowConfiguration(general.getId(), projectPublished.getId());

        ensureSourceDefault(topic);
        ensureSourceDefault(story);
        ensureSourceDefault(requirement);
    }

    private void ensureSourceDefault(ProjectTypeDO type) {
        if (type == null) return;
        defaultTemplateCatalog.find(type.getCode())
                .ifPresent(file -> ensureDefaultTemplate(type, file));
    }

    private WorkflowTemplateVersionDO ensureDefaultTemplate(ProjectTypeDO type,
                                                             WorkflowDefaultTemplateFile file) {
        if (!type.getCode().equals(file.processTypeCode())) {
            throw new IllegalStateException("系统默认流程模板类型不匹配: " + file.processTypeCode());
        }
        WorkflowTemplateDefinition definition;
        try {
            definition = WorkflowTemplateDefinitionNormalizer.normalizeForProcessType(
                    type.getCode(), WorkflowTemplateDefinitionValidator.validate(file.definition()));
            definition = WorkflowTemplateDefinitionValidator.validateForPublish(type.getCode(), definition);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("系统默认流程模板定义无效: " + file.processTypeCode(), e);
        }

        WorkflowTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<WorkflowTemplateDO>()
                .eq(WorkflowTemplateDO::getCode, file.templateCode()));
        if (template == null) {
            template = new WorkflowTemplateDO();
            template.setCode(file.templateCode());
            template.setProjectTypeId(type.getId());
            template.setName(file.name());
            template.setDescription(file.description());
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
            published.setVersionNo(file.versionNo());
            published.setStatus("PUBLISHED");
            published.setDefinitionJson(serialize(definition));
            published.setPublishedAt(LocalDateTime.now());
            versionMapper.insert(published);
            template.setLatestVersionNo(Math.max(template.getLatestVersionNo() == null ? 0 : template.getLatestVersionNo(), file.versionNo()));
            templateMapper.updateById(template);
        }

        if (type.getDefaultTemplateVersionId() == null) {
            type.setDefaultTemplateVersionId(published.getId());
            projectTypeMapper.updateById(type);
        }
        return published;
    }

    private ProjectTypeDO ensureProjectType(String code, String name, String description,
                                            int sort, boolean projectCreationEnabled) {
        ProjectTypeDO type = projectTypeMapper.selectOne(new LambdaQueryWrapper<ProjectTypeDO>()
                .eq(ProjectTypeDO::getCode, code));
        if (type == null) {
            type = new ProjectTypeDO();
            type.setCode(code);
            type.setName(name);
            type.setDescription(description);
            type.setStatus(1);
            type.setProjectCreationEnabled(projectCreationEnabled);
            type.setSort(sort);
            type.setDeleted(false);
            projectTypeMapper.insert(type);
            return type;
        }

        boolean changed = false;
        if (type.getStatus() == null || !Integer.valueOf(1).equals(type.getStatus())) {
            type.setStatus(1);
            changed = true;
        }
        if (type.getProjectCreationEnabled() == null) {
            type.setProjectCreationEnabled(projectCreationEnabled);
            changed = true;
        }
        if ("general".equals(code) && "通用项目".equals(type.getName())) {
            type.setName(name);
            type.setDescription(description);
            changed = true;
        }
        if (changed) projectTypeMapper.updateById(type);
        return type;
    }

    private WorkflowDefaultTemplateFile compatibilityDefault() {
        return new WorkflowDefaultTemplateFile("general", "current-process", "当前项目流程",
                "兼容现有项目的九阶段顺序流程", 1, BuiltInWorkflowTemplate.compatibilityDefinition());
    }

    private String serialize(WorkflowTemplateDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("系统默认流程模板无法序列化", e);
        }
    }
}
