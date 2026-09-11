package com.brad.pms.service;

import com.brad.pms.dto.request.RoleSaveCmd;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RoleServiceTest {
    @Autowired RoleService roleService;
    @Autowired OperationLogMapper operationLogMapper;

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

    @Test
    void roleWithoutPermissionIsRejected() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        RoleSaveCmd cmd = new RoleSaveCmd();
        cmd.setCode("EMPTY_PERMISSION_TEST");
        cmd.setName("空权限测试");
        cmd.setDataScopeType("SELF");
        cmd.setPermissionCodes(List.of());

        assertThatThrownBy(() -> roleService.create(cmd))
                .hasMessage("角色至少需要绑定一个权限点");
    }

    @Test
    void roleAuditIncludesPermissionAndScopeBeforeAfterSnapshots() {
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        String code = "AUDIT_ROLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        RoleSaveCmd create = new RoleSaveCmd();
        create.setCode(code);
        create.setName("审计角色");
        create.setDataScopeType("SELF");
        create.setPermissionCodes(List.of("project:read"));
        var role = roleService.create(create);
        try {
            RoleSaveCmd update = new RoleSaveCmd();
            update.setCode(code);
            update.setName("审计角色更新");
            update.setDataScopeType("ORG");
            update.setPermissionCodes(List.of("project:read", "project:write"));
            roleService.update(role.getId(), update);

            List<OperationLogDO> logs = operationLogMapper.selectList(new LambdaQueryWrapper<OperationLogDO>()
                    .eq(OperationLogDO::getResourceType, "ROLE")
                    .eq(OperationLogDO::getResourceId, role.getId())
                    .orderByAsc(OperationLogDO::getId));
            assertThat(logs).anySatisfy(log -> {
                assertThat(log.getAction()).isEqualTo("ROLE_PERMISSION_CHANGED");
                assertThat(log.getBeforeJson()).contains("project:read");
                assertThat(log.getAfterJson()).contains("project:write");
            });
            assertThat(logs).anySatisfy(log -> {
                assertThat(log.getAction()).isEqualTo("ROLE_SCOPE_CHANGED");
                assertThat(log.getBeforeJson()).contains("SELF");
                assertThat(log.getAfterJson()).contains("ORG");
            });
        } finally {
            roleService.delete(role.getId());
        }
    }

}
