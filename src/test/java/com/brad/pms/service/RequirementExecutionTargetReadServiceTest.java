package com.brad.pms.service;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.RequirementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementExecutionTargetReadServiceTest {
    @Mock RequirementMapper requirementMapper;
    @Mock UserService userService;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @InjectMocks RequirementExecutionTargetReadService service;

    @Test
    void findsOnlyTheDirectRequirementSourceForATopic() {
        RequirementDO requirement = new RequirementDO();
        requirement.setId(31L);
        requirement.setTitle("直接需求");
        requirement.setStatus("ACTIVE");
        requirement.setExecutionTargetType(RequirementExecutionTargetType.TOPIC);
        requirement.setExecutionTargetId(7L);
        when(requirementMapper.selectByExecutionTarget(RequirementExecutionTargetType.TOPIC, 7L))
                .thenReturn(requirement);

        var summary = service.findDirectSourceForTarget(RequirementExecutionTargetType.TOPIC, 7L);

        assertThat(summary.getId()).isEqualTo(31L);
        assertThat(summary.getTitle()).isEqualTo("直接需求");
        assertThat(summary.getTargetType()).isEqualTo(RequirementExecutionTargetType.TOPIC);
        assertThat(summary.getTargetId()).isEqualTo(7L);
    }

    @Test
    void batchLookupDoesNotTraverseDescendantTargets() {
        when(requirementMapper.selectByExecutionTargets(any(), any())).thenReturn(java.util.List.of());

        assertThat(service.findDirectSourcesForTargets(RequirementExecutionTargetType.PROJECT,
                java.util.List.of(78L))).isEmpty();
    }

    @Test
    void readsCurrentProjectTargetDetailsEvenWhenItsStatusChanged() {
        RequirementDO requirement = new RequirementDO();
        requirement.setExecutionTargetType(RequirementExecutionTargetType.PROJECT);
        requirement.setExecutionTargetId(78L);
        ProjectDO project = new ProjectDO();
        project.setId(78L);
        project.setName("旧项目");
        project.setCode("PRJ-000078");
        project.setStatus(2);
        when(projectMapper.selectIncludingDeleted(78L)).thenReturn(project);

        var target = service.findCurrentTarget(requirement);

        assertThat(target.getTitle()).isEqualTo("旧项目");
        assertThat(target.getCode()).isEqualTo("PRJ-000078");
        assertThat(target.getStatus()).isEqualTo("2");
        assertThat(target.getNavigationType()).isEqualTo("project");
    }
}
