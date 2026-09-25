package com.brad.pms.service;

import com.brad.pms.common.enums.MemberRole;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectMemberAutoManagedMapper;
import com.brad.pms.security.ProjectPermissionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock OperationLogService operationLogService;
    @Mock ProjectMemberAutoManagedMapper autoManagedMapper;

    @Test
    void assignmentEnsureKeepsExistingMemberRoleAndReportsNoInsert() {
        MemberService service = new MemberService(memberMapper, userService, permissionService,
                operationLogService, autoManagedMapper);
        ProjectMemberDO existing = new ProjectMemberDO();
        existing.setId(17L);
        existing.setProjectId(1L);
        existing.setUserId(88L);
        existing.setRole(MemberRole.ADMIN.getCode());
        when(memberMapper.selectIncludingDeleted(1L, 88L)).thenReturn(existing);

        MemberService.EnsureMemberResult result = service.ensureMemberForAssignment(1L, 88L);

        assertThat(result.member()).isSameAs(existing);
        assertThat(result.inserted()).isFalse();
        assertThat(existing.getRole()).isEqualTo(MemberRole.ADMIN.getCode());
        verify(userService).requireActiveUser(88L);
    }

    @Test
    void assignmentEnsureReportsReactivatedMemberAsInsertedForAutoManagement() {
        MemberService service = new MemberService(memberMapper, userService, permissionService,
                operationLogService, autoManagedMapper);
        ProjectMemberDO existing = new ProjectMemberDO();
        existing.setId(17L);
        existing.setProjectId(1L);
        existing.setUserId(88L);
        existing.setRole(MemberRole.MEMBER.getCode());
        existing.setDeleted(true);
        when(memberMapper.selectIncludingDeleted(1L, 88L)).thenReturn(existing);

        MemberService.EnsureMemberResult result = service.ensureMemberForAssignment(1L, 88L);

        assertThat(result.inserted()).isTrue();
        assertThat(existing.getDeleted()).isFalse();
        verify(memberMapper).updateById(existing);
    }
}
