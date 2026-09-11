package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.enums.MemberRole;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock ProjectMemberMapper memberMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock OperationLogService operationLogService;

    @InjectMocks MemberService memberService;

    @BeforeEach
    void initTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectMemberDO.class);
    }

    @Test
    void ensureMemberAddsAnActiveAccountOnce() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(userService.listByIds(List.of(8L))).thenReturn(List.of(activeUser(8L)));

        memberService.ensureMember(1L, 8L);

        ArgumentCaptor<ProjectMemberDO> captor = ArgumentCaptor.forClass(ProjectMemberDO.class);
        verify(memberMapper).insert(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(8L);
        assertThat(captor.getValue().getRole()).isEqualTo(MemberRole.MEMBER.getCode());
    }

    @Test
    void ensureMemberDoesNotDuplicateAnExistingMember() {
        ProjectMemberDO existing = new ProjectMemberDO();
        existing.setId(3L);
        existing.setProjectId(1L);
        existing.setUserId(8L);
        when(memberMapper.selectOne(any())).thenReturn(existing);

        assertThat(memberService.ensureMember(1L, 8L)).isSameAs(existing);
        verify(memberMapper, never()).insert(any(ProjectMemberDO.class));
    }

    @Test
    void ensureMemberRejectsAnInactiveAccount() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        UserDO pending = activeUser(8L);
        pending.setStatus(UserStatus.PENDING_ACTIVATION.name());
        when(userService.listByIds(List.of(8L))).thenReturn(List.of(pending));

        assertThatThrownBy(() -> memberService.ensureMember(1L, 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已激活");
        verify(memberMapper, never()).insert(any(ProjectMemberDO.class));
    }

    @Test
    void replaceRejectsAnInactiveAccount() {
        when(memberMapper.selectList(any())).thenReturn(List.of());
        UserDO pending = activeUser(8L);
        pending.setStatus(UserStatus.PENDING_ACTIVATION.name());
        when(userService.listByIds(List.of(8L))).thenReturn(List.of(pending));

        assertThatThrownBy(() -> memberService.replace(1L, 1L, List.of(8L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已激活");
        verify(memberMapper, never()).insert(any(ProjectMemberDO.class));
    }

    @Test
    void replaceKeepsMembersOutsideTheClientBaseline() {
        ProjectMemberDO known = member(11L, 1L, 8L);
        ProjectMemberDO autoJoined = member(12L, 1L, 99L);
        when(memberMapper.selectList(any())).thenReturn(List.of(known, autoJoined));

        memberService.replace(1L, 8L, List.of(8L), List.of(8L));

        verify(memberMapper, never()).deleteById(eq(12L));
        verify(memberMapper, never()).deleteById(eq(11L));
        verify(memberMapper, never()).insert(any(ProjectMemberDO.class));
    }

    private static ProjectMemberDO member(Long id, Long projectId, Long userId) {
        ProjectMemberDO member = new ProjectMemberDO();
        member.setId(id);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRole(MemberRole.MEMBER.getCode());
        return member;
    }

    private static UserDO activeUser(Long id) {
        UserDO user = new UserDO();
        user.setId(id);
        user.setStatus(UserStatus.ACTIVE.name());
        return user;
    }
}
