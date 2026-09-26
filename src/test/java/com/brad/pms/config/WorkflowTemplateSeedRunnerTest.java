package com.brad.pms.config;

import com.brad.pms.entity.ProjectTypeDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectTypeMapper;
import com.brad.pms.mapper.WorkflowTemplateMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTemplateSeedRunnerTest {
    @Mock ProjectTypeMapper projectTypeMapper;
    @Mock WorkflowTemplateMapper templateMapper;
    @Mock WorkflowTemplateVersionMapper versionMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ObjectMapper objectMapper;
    @InjectMocks WorkflowTemplateSeedRunner runner;

    @Test
    void createsDevelopmentProcessTypesAsTemplateOnlyTypes() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        runner.run();

        ArgumentCaptor<ProjectTypeDO> captor = ArgumentCaptor.forClass(ProjectTypeDO.class);
        verify(projectTypeMapper, atLeastOnce()).insert(captor.capture());
        List<ProjectTypeDO> insertedTypes = captor.getAllValues();

        assertThat(insertedTypes).extracting(ProjectTypeDO::getCode)
                .contains("general", "topic-management", "story-management", "requirement-management");
        assertThat(insertedTypes.stream()
                .filter(type -> !"general".equals(type.getCode()))
                .map(this::creationEnabledUnchecked))
                .containsOnly(false);
    }

    @Test
    void preservesExistingNonGeneralTypesWhenInitializationRunsAgain() throws Exception {
        ProjectTypeDO general = type(1L, "general", true);
        ProjectTypeDO topic = type(2L, "topic-management", false);
        ProjectTypeDO story = type(3L, "story-management", false);
        ProjectTypeDO requirement = type(4L, "requirement-management", false);
        when(projectTypeMapper.selectOne(any())).thenReturn(general, topic, story, requirement);
        when(templateMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        runner.run();

        verify(projectTypeMapper, times(4)).selectOne(any());
        verify(projectTypeMapper, never()).insert(any(ProjectTypeDO.class));
    }

    private ProjectTypeDO type(Long id, String code, boolean creationEnabled) throws Exception {
        ProjectTypeDO type = new ProjectTypeDO();
        type.setId(id);
        type.setCode(code);
        setCreationEnabled(type, creationEnabled);
        type.setDefaultTemplateVersionId(99L);
        return type;
    }

    private boolean creationEnabled(ProjectTypeDO type) throws ReflectiveOperationException {
        Field field = ProjectTypeDO.class.getDeclaredField("projectCreationEnabled");
        field.setAccessible(true);
        return Boolean.TRUE.equals(field.get(type));
    }

    private boolean creationEnabledUnchecked(ProjectTypeDO type) {
        try {
            return creationEnabled(type);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void setCreationEnabled(ProjectTypeDO type, boolean enabled) throws ReflectiveOperationException {
        Field field;
        try {
            field = ProjectTypeDO.class.getDeclaredField("projectCreationEnabled");
        } catch (NoSuchFieldException ignored) {
            return;
        }
        field.setAccessible(true);
        field.set(type, enabled);
    }
}
