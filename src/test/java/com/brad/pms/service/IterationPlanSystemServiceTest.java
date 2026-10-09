package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IterationPlanSystemServiceTest {
    @Mock RequirementSystemReferenceService systems;
    @Mock SystemVersionReferenceService versions;
    @Mock ProjectNodeIterationPlanMapper plans;
    @InjectMocks IterationPlanSystemService service;

    @Test void requiresSystemForANewIterationWithoutAnySource() {
        assertThatThrownBy(() -> service.resolveForSave(new ProjectNodeIterationPlanDO(), new NodeIterationPlanCmd(), true))
                .isInstanceOf(BusinessException.class).hasMessageContaining("选择所属系统");
    }

    @Test void inheritsSystemAndRejectsAConflictingManualSelection() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setProjectId(9L);
        when(systems.resolveForProject(9L)).thenReturn(88L);
        assertThat(service.resolveForSave(plan, new NodeIterationPlanCmd(), true)).isEqualTo(88L);
        NodeIterationPlanCmd cmd = new NodeIterationPlanCmd();
        cmd.setSystemId(99L);
        assertThatThrownBy(() -> service.resolveForSave(plan, cmd, true))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不一致");
    }

    @Test void preservesManualSystemWhenAnUpdateOmitsTheField() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(21L);
        plan.setSystemId(88L);
        assertThat(service.resolveForSave(plan, new NodeIterationPlanCmd(), false)).isEqualTo(88L);
    }

    @Test void fillsALegacyIterationFromAnIndependentStory() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(21L);
        ProjectNodeDevelopmentStoryDO story = story(30L, null);
        when(systems.resolveForStory(story)).thenReturn(88L);
        when(plans.fillSystem(21L, 88L)).thenReturn(1);
        service.bindStory(plan, story);
        assertThat(plan.getSystemId()).isEqualTo(88L);
        assertThat(plan.getProjectId()).isNull();
    }

    @Test void rejectsCrossSystemAssociationWithoutChangingThePlan() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(21L);
        plan.setSystemId(88L);
        ProjectNodeDevelopmentStoryDO story = story(30L, null);
        when(systems.resolveForStory(story)).thenReturn(99L);
        assertThatThrownBy(() -> service.bindStory(plan, story))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不一致");
        assertThat(plan.getSystemId()).isEqualTo(88L);
        verify(plans, never()).fillSystem(any(), any());
    }

    @Test void rejectsChangingTheSystemOfAnIterationWithLinkedStories() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setId(21L);
        plan.setSystemId(88L);
        doThrow(BusinessException.conflict("已有故事系统不一致"))
                .when(systems).validatePlanSources(21L, 99L, null);
        NodeIterationPlanCmd cmd = new NodeIterationPlanCmd();
        cmd.setSystemId(99L);
        assertThatThrownBy(() -> service.resolveForSave(plan, cmd, false))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不一致");
    }

    @Test void boundIterationStillRequiresTheSameProject() {
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setProjectId(9L);
        assertThatThrownBy(() -> service.bindStory(plan, story(30L, 10L)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("同一个项目");
    }

    private ProjectNodeDevelopmentStoryDO story(Long id, Long projectId) {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(id);
        story.setProjectId(projectId);
        return story;
    }
}
