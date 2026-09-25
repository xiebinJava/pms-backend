package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class NodeRequirementScopePermissionTest {

    @Test
    void protectsEveryRequirementScopeEndpointWithProjectRead() {
        for (String methodName : new String[]{"get", "save", "confirm"}) {
            var method = Arrays.stream(NodeRequirementScopeController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            var permission = method.getAnnotation(RequirePermission.class);
            assertThat(permission).as(methodName).isNotNull();
            assertThat(permission.value()).isEqualTo(PermissionCode.PROJECT_READ);
        }
    }
}
