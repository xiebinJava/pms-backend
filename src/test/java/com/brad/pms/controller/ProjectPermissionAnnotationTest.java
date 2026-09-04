package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectPermissionAnnotationTest {
    @Test
    void projectEndpointsUseCreateForCreationAndReadAsResourceBaseline() throws Exception {
        assertPermission("create", PermissionCode.PROJECT_CREATE);
        assertPermission("update", PermissionCode.PROJECT_READ);
        assertPermission("delete", PermissionCode.PROJECT_READ);
        assertPermission("terminate", PermissionCode.PROJECT_READ);
        assertPermission("restore", PermissionCode.PROJECT_READ);
        assertPermission("stats", PermissionCode.PROJECT_READ);
        assertPermission("page", PermissionCode.PROJECT_READ);
        assertPermission("detail", PermissionCode.PROJECT_READ);
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = java.util.Arrays.stream(ProjectController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
