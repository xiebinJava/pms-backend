package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackPermissionAnnotationTest {

    @Test
    void feedbackEndpointsDeclareTheLeastPrivilegePermission() throws Exception {
        assertPermission("create", PermissionCode.FEEDBACK_WRITE);
        assertPermission("list", PermissionCode.FEEDBACK_READ);
        assertPermission("assignees", PermissionCode.FEEDBACK_MANAGE);
        assertPermission("detail", PermissionCode.FEEDBACK_READ);
        assertPermission("update", PermissionCode.FEEDBACK_MANAGE);
        assertPermission("reopen", PermissionCode.FEEDBACK_WRITE);
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = java.util.Arrays.stream(FeedbackController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
