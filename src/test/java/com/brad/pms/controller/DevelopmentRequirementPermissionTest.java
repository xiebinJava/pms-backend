package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentRequirementPermissionTest {
    @Test
    void separatesReadWriteAndManageRequirementEndpoints() throws Exception {
        assertPermission("page", GetMapping.class, PermissionCode.REQUIREMENT_READ);
        assertPermission("create", PostMapping.class, PermissionCode.REQUIREMENT_WRITE);
        assertPermission("update", PutMapping.class, PermissionCode.REQUIREMENT_WRITE);
        assertPermission("link", PostMapping.class, PermissionCode.REQUIREMENT_WRITE);
        assertPermission("changeTarget", PostMapping.class, PermissionCode.REQUIREMENT_MANAGE);
        assertPermission("delete", DeleteMapping.class, PermissionCode.REQUIREMENT_MANAGE);
    }

    @Test
    void genericWorkflowMutationEndpointsDoNotCarryTheProjectPermission() {
        for (String methodName : new String[] {"updateNode", "completeNode", "createTask", "updateTask", "deleteTask"}) {
            var method = java.util.Arrays.stream(DevelopmentItemWorkflowController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            assertThat(method.getAnnotation(RequirePermission.class)).isNull();
        }
    }

    private void assertPermission(String methodName, Class<?> mappingType, String permission) throws Exception {
        var method = java.util.Arrays.stream(DevelopmentRequirementController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        assertThat(method.getAnnotation((Class) mappingType)).isNotNull();
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo(permission);
    }
}
