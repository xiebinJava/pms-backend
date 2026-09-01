package com.brad.pms.security;

import com.brad.pms.dto.request.LoginRequest;
import com.brad.pms.dto.request.UserDisableCmd;
import com.brad.pms.dto.response.LoginResponse;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.service.AuthService;
import com.brad.pms.service.PersonnelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnterpriseRegressionTest {
    @Autowired AuthService authService;
    @Autowired PersonnelService personnelService;
    @Autowired UserMapper userMapper;
    @Autowired AuthSessionMapper authSessionMapper;
    @Autowired AuthorizationService authorizationService;
    @Autowired JwtTokenProvider tokenProvider;
    @Autowired OperationLogMapper operationLogMapper;

    @AfterEach
    void clearContext() { UserContext.clear(); }

    @Test
    void disabledProjectMemberCannotUseExistingSessionButHistoryRemainsVisibleToAdministrator() throws Exception {
        UserDO admin = userMapper.findByUsernameNormalized("admin");
        UserDO member = userMapper.findByUsernameNormalized("alex.zhang");
        assertThat(admin).isNotNull();
        assertThat(member).isNotNull();

        LoginResponse memberSession = authService.login(login("Alex.Zhang", "admin123"));
        UserContext.set(new LoginUser(admin.getId(), admin.getUsername(), admin.getNickname(), 1));
        UserDisableCmd disable = new UserDisableCmd();
        disable.setReason("离职回归测试");
        personnelService.disable(member.getId(), disable);
        UserContext.clear();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + memberSession.getAccessToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthInterceptor interceptor = new AuthInterceptor(tokenProvider, userMapper, authSessionMapper, authorizationService);
        HandlerMethod protectedHandler = new HandlerMethod(new Object(), Object.class.getMethod("toString"));
        assertThat(interceptor.preHandle(request, response, protectedHandler)).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);

        List<OperationLogDO> logs = operationLogMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OperationLogDO>()
                .eq("action", "USER_DISABLED").eq("resource_id", member.getId()));
        assertThat(logs).isNotEmpty();
    }

    private LoginRequest login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
