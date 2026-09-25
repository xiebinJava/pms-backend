package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditPermissionAnnotationTest {
    @Test
    void listAndDetailRequireTheIndependentAuditPermission() {
        assertPermission("list");
        assertPermission("detail");
    }

    private void assertPermission(String methodName) {
        Method method = java.util.Arrays.stream(AdminAuditController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(PermissionCode.AUDIT_READ);
    }
}
