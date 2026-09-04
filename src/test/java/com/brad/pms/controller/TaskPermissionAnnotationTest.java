package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TaskPermissionAnnotationTest {

    @Test
    void taskResponsibleWritesUseProjectReadBaselineAndGovernanceWritesStayProtected() throws Exception {
        assertPermission("detail", PermissionCode.PROJECT_READ);
        assertPermission("list", PermissionCode.PROJECT_READ);
        assertPermission("create", PermissionCode.PROJECT_READ);
        assertPermission("update", PermissionCode.PROJECT_READ);
        assertPermission("uploadAttachment", PermissionCode.PROJECT_READ);
        assertPermission("downloadAttachment", PermissionCode.PROJECT_READ);
        assertPermission("deleteAttachment", PermissionCode.PROJECT_READ);
        assertPermission("move", PermissionCode.PROJECT_WRITE);
        assertPermission("delete", PermissionCode.PROJECT_WRITE);
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = java.util.Arrays.stream(TaskController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
