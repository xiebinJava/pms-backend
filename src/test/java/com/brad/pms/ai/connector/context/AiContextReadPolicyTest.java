package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.RequirementDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Module READ authority for the four AI context resource types. */
@ExtendWith(MockitoExtension.class)
class AiContextReadPolicyTest {

    @Mock ProjectMapper projectMapper;
    @Mock RequirementMapper requirementMapper;
    @Mock com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock com.brad.pms.mapper.ProjectNodeIterationPlanMapper iterationPlanMapper;
    @Mock AuthorizationService authorizationService;
    @Mock com.brad.pms.service.ProjectPermissionService projectPermissionService;

    @InjectMocks AiContextReadPolicy policy;

    @AfterEach
    void clearIdentity() {
        UserContext.clear();
    }

    @Test
    void requireReadRejectsMissingIdentity() {
        assertThatThrownBy(() -> policy.requireRead("requirement"))
                .isInstanceOfSatisfying(BusinessException.class, thrown ->
                        assertThat(thrown.getCode()).isEqualTo(403));
    }

    @Test
    void requireReadRejectsUnknownResourceTypes() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        assertThatThrownBy(() -> policy.requireRead("iteration_plan"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不支持的流程上下文资源类型");
    }

    @Test
    void requirementReadRequiresRequirementAuthorityNotProjectAuthority() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.REQUIREMENT_READ)).thenReturn(false);

        assertThatThrownBy(() -> policy.requireRead("requirement"))
                .isInstanceOfSatisfying(BusinessException.class, thrown ->
                        assertThat(thrown.getCode()).isEqualTo(403));
        verify(projectPermissionService, never()).requireProjectReadable(7L);
    }

    @Test
    void writeAuthorityDoesNotImplyRead() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.REQUIREMENT_READ)).thenReturn(false);

        assertThatThrownBy(() -> policy.requireRead("requirement"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void projectTopicAndStoryShareProjectReadAuthority() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.PROJECT_READ)).thenReturn(true);

        policy.requireRead("project");
        policy.requireRead("topic");
        policy.requireRead("story");
    }

    @Test
    void canReadReferenceChecksModuleAuthorityBeforeExistence() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.REQUIREMENT_READ)).thenReturn(false);

        assertThat(policy.canReadReference("requirement", 23L)).isFalse();
        verify(requirementMapper, never()).selectById(23L);
    }

    @Test
    void anUnreadableRequirementReferenceIsDenied() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.REQUIREMENT_READ)).thenReturn(true);
        RequirementDO requirement = new RequirementDO();
        requirement.setId(23L);
        requirement.setDeleted(Boolean.TRUE);
        when(requirementMapper.selectById(23L)).thenReturn(requirement);

        assertThat(policy.canReadReference("requirement", 23L)).isFalse();
    }

    @Test
    void aDeletedProjectReferenceIsDenied() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.PROJECT_READ)).thenReturn(true);
        ProjectDO project = new ProjectDO();
        project.setId(7L);
        project.setDeleted(Boolean.TRUE);
        when(projectMapper.selectIncludingDeleted(7L)).thenReturn(project);

        assertThat(policy.canReadReference("project", 7L)).isFalse();
    }

    @Test
    void anUnknownReferenceTypeIsDeniedWithoutGuessing() {
        UserContext.set(new LoginUser(11L, "u", "U"));

        assertThat(policy.canReadReference("note", 9L)).isFalse();
        verify(projectMapper, never()).selectIncludingDeleted(9L);
    }

    @Test
    void aProjectThatDoesNotExistIsDenied() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.PROJECT_READ)).thenReturn(true);
        when(projectMapper.selectIncludingDeleted(404L)).thenReturn(null);

        assertThat(policy.canReadReference("project", 404L)).isFalse();
    }

    @Test
    void canReadReferenceRejectsMissingIdentityWithoutQueriying() {
        assertThat(policy.canReadReference("project", 7L)).isFalse();
        verify(projectMapper, never()).selectIncludingDeleted(7L);
    }

    @Test
    void databaseFailuresPropagateInsteadOfSilentlyOmitting() {
        UserContext.set(new LoginUser(11L, "u", "U"));
        when(authorizationService.has(com.brad.pms.security.PermissionCode.REQUIREMENT_READ)).thenReturn(true);
        when(requirementMapper.selectById(23L)).thenThrow(new IllegalStateException("数据库连接失败"));

        assertThatThrownBy(() -> policy.canReadReference("requirement", 23L))
                .isInstanceOf(IllegalStateException.class);
    }
}
