package com.brad.pms.service;

import com.brad.pms.dto.request.UserInviteCmd;
import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.*;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        when(userMapper.findByUsernameNormalized("brad.xie")).thenReturn(null);
        when(orgUnitMapper.selectById(10L)).thenReturn(org);
        when(notifierProvider.getIfAvailable()).thenReturn(notifier);
        doAnswer(invocation -> { invocation.<UserDO>getArgument(0).setId(22L); return 1; }).when(userMapper).insert(any(UserDO.class));

        InvitationService service = new InvitationService(userMapper, invitationMapper, orgUnitMapper,
                positionMapper, roleMapper, userRoleMapper, userPositionMapper, authService,
                operationLogService, notifierProvider);
        UserInviteCmd command = new UserInviteCmd();
        command.setNameZh("谢斌"); command.setUsername("Brad.Xie"); command.setEmail("brad@example.com"); command.setOrgUnitId(10L);

        var response = service.invite(command);

        assertThat(response.getActivationUrl()).isEmpty();
        verify(notifier).send(any(UserDO.class), contains("/auth/activate?token="), any());
    }
}
