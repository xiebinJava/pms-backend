package com.brad.pms.service;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.entity.SystemDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.mapper.SystemMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class RequirementSystemReferenceServiceTest {

    @Mock RequirementMapper requirementMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock SystemMapper systemMapper;
    @Mock ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock ProjectTaskMapper taskMapper;
    @InjectMocks RequirementSystemReferenceService service;

    @BeforeEach void initializeLambdaMapping() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
                new org.apache.ibatis.session.Configuration(), "requirement-system-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProjectNodeIterationPlanDO.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, ProjectTaskDO.class);
    }

    @Test
    void resolvesOneSystemAcrossProjectTopicAndStoryTargets() {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(11L);
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(12L);

        when(topicMapper.selectActiveByProjectId(9L)).thenReturn(List.of(topic));
        when(storyMapper.selectActiveByProjectId(9L)).thenReturn(List.of(story));
        when(requirementMapper.selectByExecutionTargets(RequirementExecutionTargetType.PROJECT, List.of(9L)))
                .thenReturn(List.of(requirement(1L, 101L)));
        when(requirementMapper.selectByExecutionTargets(RequirementExecutionTargetType.TOPIC, List.of(11L)))
                .thenReturn(List.of(requirement(2L, 101L)));
        when(requirementMapper.selectByExecutionTargets(RequirementExecutionTargetType.STORY, List.of(12L)))
                .thenReturn(List.of(requirement(3L, 101L)));

        assertThat(service.resolveForProject(9L)).isEqualTo(101L);
    }

    @Test
    void returnsNullWhenTheProjectHasNoSystemYetSoIterationCreationRemainsAvailable() {
        when(topicMapper.selectActiveByProjectId(9L)).thenReturn(List.of());
        when(storyMapper.selectActiveByProjectId(9L)).thenReturn(List.of());
        when(requirementMapper.selectByExecutionTargets(RequirementExecutionTargetType.PROJECT, List.of(9L)))
                .thenReturn(List.of());

        assertThat(service.resolveForProject(9L)).isNull();
    }

    @Test
    void rejectsDifferentSystemsInOneProject() {
        when(topicMapper.selectActiveByProjectId(9L)).thenReturn(List.of());
        when(storyMapper.selectActiveByProjectId(9L)).thenReturn(List.of());
        when(requirementMapper.selectByExecutionTargets(RequirementExecutionTargetType.PROJECT, List.of(9L)))
                .thenReturn(List.of(requirement(1L, 101L), requirement(2L, 202L)));

        assertThatThrownBy(() -> service.resolveForProject(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多个不同系统");
    }

    @Test
    void rejectsDifferentSystemsOnAnIndependentTopic() {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(11L);
        when(topicMapper.selectById(11L)).thenReturn(topic);
        when(requirementMapper.selectByExecutionTargets(RequirementExecutionTargetType.TOPIC, List.of(11L)))
                .thenReturn(List.of(requirement(1L, 101L)));
        when(systemMapper.selectById(202L)).thenReturn(activeSystem(202L));
        RequirementDO changed = requirement(2L, 202L);
        changed.setExecutionTargetType(RequirementExecutionTargetType.TOPIC);
        changed.setExecutionTargetId(11L);

        assertThatThrownBy(() -> service.validateRequirementSystem(changed))
                .isInstanceOf(BusinessException.class).hasMessageContaining("系统");
    }

    @Test
    void requirementSystemCannotConflictWithAnIndependentIterationLinkedOnlyByTasks() {
        RequirementDO changed = requirement(1L, 202L);
        changed.setExecutionTargetType(RequirementExecutionTargetType.PROJECT);
        changed.setExecutionTargetId(9L);
        when(systemMapper.selectById(202L)).thenReturn(activeSystem(202L));
        ProjectTaskDO task = new ProjectTaskDO();
        task.setProjectId(9L);
        task.setIterationPlanId(21L);
        when(taskMapper.selectList(any())).thenReturn(List.of(task));
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(21L);
        plan.setSystemId(101L);
        when(iterationPlanMapper.selectList(any())).thenAnswer(invocation -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) invocation.getArgument(0);
            query.getSqlSegment();
            // A project-bound query alone must not accidentally include this independent plan.
            return query.getParamNameValuePairs().containsValue(21L) ? List.of(plan) : List.of();
        });

        assertThatThrownBy(() -> service.validateRequirementSystem(changed))
                .isInstanceOf(BusinessException.class).hasMessageContaining("迭代");
    }

    private static RequirementDO requirement(Long id, Long systemId) {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(id);
        requirement.setSystemId(systemId);
        requirement.setStatus("ACTIVE");
        requirement.setDeleted(false);
        return requirement;
    }

    private static SystemDO activeSystem(Long id) {
        SystemDO system = new SystemDO();
        system.setId(id);
        system.setStatus("ACTIVE");
        return system;
    }
}
