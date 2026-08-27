package com.brad.pms.service;

import com.brad.pms.dto.request.RoleSaveCmd;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RoleServiceTest {
    @Autowired RoleService roleService;

    @AfterEach
    void clearContext() { UserContext.clear(); }

    @Test
    void unknownPermissionCodeIsRejectedInsteadOfSilentlyDropped() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        RoleSaveCmd cmd = new RoleSaveCmd();
        cmd.setCode("INVALID_PERMISSION_TEST");
        cmd.setName("非法权限测试");
        cmd.setDataScopeType("SELF");
        cmd.setPermissionCodes(List.of("admin:not-a-real-permission"));

        assertThatThrownBy(() -> roleService.create(cmd))
                .hasMessage("权限点不存在: admin:not-a-real-permission");
    }
}
