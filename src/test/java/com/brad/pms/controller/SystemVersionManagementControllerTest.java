package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SystemVersionManagementControllerTest {
    @Test
    void declaresReadWriteAndManagePermissionsByOperation() throws Exception {
        assertPermission("pageSystems", PermissionCode.SYSTEM_VERSION_READ);
        assertPermission("createSystem", PermissionCode.SYSTEM_VERSION_WRITE);
        assertPermission("updateSystem", PermissionCode.SYSTEM_VERSION_WRITE);
        assertPermission("changeSystemStatus", PermissionCode.SYSTEM_VERSION_MANAGE);
        assertPermission("pageVersions", PermissionCode.SYSTEM_VERSION_READ);
        assertPermission("detail", PermissionCode.SYSTEM_VERSION_READ);
        assertPermission("createVersion", PermissionCode.SYSTEM_VERSION_WRITE);
        assertPermission("updateVersion", PermissionCode.SYSTEM_VERSION_WRITE);
        assertPermission("deleteVersion", PermissionCode.SYSTEM_VERSION_WRITE);
        assertPermission("changeVersionStatus", PermissionCode.SYSTEM_VERSION_MANAGE);
        assertPermission("history", PermissionCode.SYSTEM_VERSION_READ);
    }

    private void assertPermission(String methodName, String expected) {
        Method method = java.util.Arrays.stream(SystemVersionManagementController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission permission = method.getAnnotation(RequirePermission.class);
        assertThat(permission).as(methodName).isNotNull();
        assertThat(permission.value()).isEqualTo(expected);
    }
}
