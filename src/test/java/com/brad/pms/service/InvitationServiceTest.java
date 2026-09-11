package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.UserInviteCmd;
import com.brad.pms.entity.InvitationDO;
import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.RoleDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.*;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {
    @Mock UserMapper userMapper;
    @Mock InvitationMapper invitationMapper;
    @Mock OrgUnitMapper orgUnitMapper;
    @Mock PositionMapper positionMapper;
    @Mock RoleMapper roleMapper;
    @Mock UserRoleMapper userRoleMapper;
    @Mock UserPositionMapper userPositionMapper;
    @Mock AuthService authService;
    @Mock OperationLogService operationLogService;
    @Mock InvitationNotifier notifier;
    @Mock ObjectProvider<InvitationNotifier> notifierProvider;

    @AfterEach
    void clearContext() { UserContext.clear(); }

    @Test
    void sendsActivationLinkWithoutReturningRawTokenInProductionMode() {
        UserContext.set(new LoginUser(1L, "admin", "管理员"));
        OrgUnitDO org = new OrgUnitDO(); org.setId(10L); org.setStatus("ACTIVE");
        when(userMapper.findByUsernameNormalized("alex.zhang")).thenReturn(null);
        when(orgUnitMapper.selectById(10L)).thenReturn(org);
        when(notifierProvider.getIfAvailable()).thenReturn(notifier);
        doAnswer(invocation -> { invocation.<UserDO>getArgument(0).setId(22L); return 1; }).when(userMapper).insert(any(UserDO.class));

        InvitationService service = new InvitationService(userMapper, invitationMapper, orgUnitMapper,
                positionMapper, roleMapper, userRoleMapper, userPositionMapper, authService,
                operationLogService, notifierProvider);
        UserInviteCmd command = new UserInviteCmd();
        command.setNameZh("张伟"); command.setUsername("Alex.Zhang"); command.setEmail("alex.zhang@example.com"); command.setOrgUnitId(10L);

        var response = service.invite(command);

        assertThat(response.getActivationUrl()).isEmpty();
        verify(notifier).send(any(UserDO.class), contains("/auth/activate?token="), any());
    }

    @Test
    void invitationAllowsMissingDisplayNamesWhenEmailIsProvided() {
        UserContext.set(new LoginUser(1L, "admin", "管理员"));
        OrgUnitDO org = new OrgUnitDO(); org.setId(10L); org.setStatus("ACTIVE");
        when(userMapper.findByEmailNormalized("email.only@example.com")).thenReturn(null);
        when(orgUnitMapper.selectById(10L)).thenReturn(org);
        when(notifierProvider.getIfAvailable()).thenReturn(notifier);
        doAnswer(invocation -> { invocation.<UserDO>getArgument(0).setId(23L); return 1; }).when(userMapper).insert(any(UserDO.class));

        InvitationService service = new InvitationService(userMapper, invitationMapper, orgUnitMapper,
                positionMapper, roleMapper, userRoleMapper, userPositionMapper, authService,
                operationLogService, notifierProvider);
        UserInviteCmd command = new UserInviteCmd();
        command.setEmail("Email.Only@Example.com"); command.setOrgUnitId(10L);

        service.invite(command);

        verify(userMapper).insert(ArgumentMatchers.<UserDO>argThat(user ->
                "Email.Only@Example.com".equals(user.getEmail())
                        && "email.only@example.com".equals(user.getEmailNormalized())
                        && user.getNameZh() == null));
    }

    @Test
    void reinviteReplacesThePendingLinkForAnUnactivatedAccount() {
        UserContext.set(new LoginUser(1L, "admin", "管理员"));
        UserDO pending = new UserDO();
        pending.setId(22L);
        pending.setEmail("alex.zhang@example.com");
        pending.setStatus("PENDING_ACTIVATION");
        when(userMapper.selectById(22L)).thenReturn(pending);
        when(notifierProvider.getIfAvailable()).thenReturn(notifier);

        InvitationService service = new InvitationService(userMapper, invitationMapper, orgUnitMapper,
                positionMapper, roleMapper, userRoleMapper, userPositionMapper, authService,
                operationLogService, notifierProvider);

        var response = service.reinvite(22L);

        assertThat(response.getUserId()).isEqualTo(22L);
        verify(invitationMapper).expirePendingByUserId(22L);
        verify(invitationMapper).insert(any(InvitationDO.class));
        verify(notifier).send(any(UserDO.class), contains("/auth/activate?token="), any());
    }

    @Test
    void inviteRejectsADisabledRoleBeforeCreatingTheAccount() {
        UserContext.set(new LoginUser(1L, "admin", "管理员"));
        OrgUnitDO org = new OrgUnitDO(); org.setId(10L); org.setStatus("ACTIVE");
        RoleDO role = new RoleDO(); role.setId(8L); role.setCode("CUSTOM"); role.setEnabled(false);
        when(userMapper.findByEmailNormalized("alex.zhang@example.com")).thenReturn(null);
        when(orgUnitMapper.selectById(10L)).thenReturn(org);
        when(roleMapper.findByCode("CUSTOM")).thenReturn(role);

        InvitationService service = new InvitationService(userMapper, invitationMapper, orgUnitMapper,
                positionMapper, roleMapper, userRoleMapper, userPositionMapper, authService,
                operationLogService, notifierProvider);
        UserInviteCmd command = new UserInviteCmd();
        command.setEmail("alex.zhang@example.com");
        command.setOrgUnitId(10L);
        command.setRoleCode("CUSTOM");

        assertThatThrownBy(() -> service.invite(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("停用");
        verify(userMapper, never()).insert(any(UserDO.class));
    }
}
