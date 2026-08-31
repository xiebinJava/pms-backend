package com.brad.pms.controller;

import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectPermissionAnnotationTest {
    @Test
    void projectEndpointsDeclareReadOrWritePermission() throws Exception {
        assertPermission("create", "project:write");
        assertPermission("update", "project:write");
        assertPermission("delete", "project:write");
        assertPermission("terminate", "project:write");
        assertPermission("restore", "project:write");
        assertPermission("stats", "project:read");
        assertPermission("page", "project:read");
        assertPermission("detail", "project:read");
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = java.util.Arrays.stream(ProjectController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
