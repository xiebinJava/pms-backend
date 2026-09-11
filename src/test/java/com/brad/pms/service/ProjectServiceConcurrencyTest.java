package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.ProjectUpdateCmd;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.mapper.*;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceConcurrencyTest {

    @Mock ProjectMapper projectMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectMilestoneMapper milestoneMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock MemberService memberService;
    @Mock NodeService nodeService;
    @Mock UserService userService;
    @Mock FollowerService followerService;
    @Mock ProjectPermissionService permissionService;
    @Mock ProjectLifecycleLogMapper lifecycleLogMapper;
    @Mock UserPositionMapper userPositionMapper;
    @Mock com.brad.pms.security.DataScopeResolver dataScopeResolver;
    @Mock com.brad.pms.mapper.OrgUnitMapper orgUnitMapper;
    @Mock OperationLogService operationLogService;

    @InjectMocks ProjectService projectService;

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void rejectsAStaleVersionBeforeMutatingTheProject() {
        ProjectDO project = new ProjectDO();
        project.setId(9L);
        project.setVersion(2);
        when(permissionService.requireProjectWritable(9L, "编辑项目")).thenReturn(project);

        ProjectUpdateCmd cmd = new ProjectUpdateCmd();
        cmd.setVersion(1);
        cmd.setName("过期编辑");

        assertThatThrownBy(() -> projectService.update(9L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目已被其他人修改")
                .extracting(error -> ((BusinessException) error).getCode())
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);
        verify(projectMapper, never()).updateById(any(ProjectDO.class));
    }
}
