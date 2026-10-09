package com.brad.pms.service;

import com.brad.pms.entity.ProjectFollowerDO;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.mapper.ProjectFollowerMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class FollowerServiceTest {

    @Mock ProjectFollowerMapper followerMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock OperationLogService operationLogService;

    @InjectMocks FollowerService followerService;

    @Test
    void replaceKeepsExistingFollowersWithoutDeletingAndReinsertingThem() {
        when(followerMapper.selectList(any())).thenReturn(List.of(follower(101L, 10L, 2L), follower(102L, 10L, 5L)));

        followerService.replace(10L, List.of(2L, 5L));

        verify(followerMapper, never()).delete(any());
        verify(followerMapper, never()).insert(any(ProjectFollowerDO.class));
    }

    @Test
    void replaceRestoresPreviouslyRemovedFollowersInsteadOfInsertingDuplicateRelations() {
        when(followerMapper.selectList(any())).thenReturn(List.of());
        when(followerMapper.restoreDeleted(10L, 5L)).thenReturn(1);

        followerService.replace(10L, List.of(5L));

        verify(followerMapper).restoreDeleted(10L, 5L);
        verify(followerMapper, never()).insert(any(ProjectFollowerDO.class));
    }

    @Test
    void replaceRejectsUnknownFollowerBeforeTouchingTheRelationTable() {
        when(userService.requireActiveUser(99L))
                .thenThrow(BusinessException.notFound("账号不存在"));

        assertThrows(BusinessException.class, () -> followerService.replace(10L, List.of(99L)));
        verify(followerMapper, never()).insert(any(ProjectFollowerDO.class));
    }

    private static ProjectFollowerDO follower(Long id, Long projectId, Long userId) {
        ProjectFollowerDO follower = new ProjectFollowerDO();
        follower.setId(id);
        follower.setProjectId(projectId);
        follower.setUserId(userId);
        follower.setDeleted(false);
        return follower;
    }
}
