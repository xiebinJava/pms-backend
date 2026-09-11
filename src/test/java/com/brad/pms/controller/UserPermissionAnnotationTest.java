package com.brad.pms.controller;

import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserPermissionAnnotationTest {
    @Test
    void userSearchRequiresProjectReadPermission() throws Exception {
        Method method = UserController.class.getDeclaredMethod("search", String.class);
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("project:read");
    }
}
