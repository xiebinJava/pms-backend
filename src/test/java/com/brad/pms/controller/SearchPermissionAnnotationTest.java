package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SearchPermissionAnnotationTest {

    @Test
    void searchRequiresProjectRead() throws Exception {
        Method method = SearchController.class.getDeclaredMethod("search", String.class, int.class);
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(PermissionCode.PROJECT_READ);
    }
}
