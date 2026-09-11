package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class WorkbenchPermissionAnnotationTest {

    @Test
    void workbenchRequiresProjectRead() throws Exception {
        Method method = WorkbenchController.class.getDeclaredMethod("current");
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(PermissionCode.PROJECT_READ);
    }
}
