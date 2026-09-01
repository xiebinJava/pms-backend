package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPermissionAnnotationTest {

    @Test
    void inboxEndpointsRequireProjectRead() throws Exception {
        assertPermission("list", PermissionCode.PROJECT_READ);
        assertPermission("unreadCount", PermissionCode.PROJECT_READ);
        assertPermission("markRead", PermissionCode.PROJECT_READ);
        assertPermission("markAllRead", PermissionCode.PROJECT_READ);
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = java.util.Arrays.stream(NotificationController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as(methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
