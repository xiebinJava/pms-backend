package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.annotation.Annotation;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentStoryManagementControllerTest {

    @Test
    void exposesIndependentAndTopicBoundStoryCrudRoutes() {
        assertThat(DevelopmentStoryManagementController.class.getAnnotation(RequestMapping.class).value())
                .containsExactly("/development/stories");
        assertRoute("create", PostMapping.class, "", PermissionCode.PROJECT_WRITE);
        assertRoute("update", PutMapping.class, "/{id}", PermissionCode.PROJECT_WRITE);
        assertRoute("listByTopic", GetMapping.class, "/by-topic/{topicId}", PermissionCode.PROJECT_READ);
    }

    private void assertRoute(String methodName, Class<? extends Annotation> mappingType,
                             String path, String permission) {
        Method method = Arrays.stream(DevelopmentStoryManagementController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.isAnnotationPresent(mappingType)).isTrue();
        String[] paths = mappingType == PostMapping.class ? method.getAnnotation(PostMapping.class).value()
                : mappingType == PutMapping.class ? method.getAnnotation(PutMapping.class).value()
                : method.getAnnotation(GetMapping.class).value();
        if (path.isEmpty()) assertThat(paths).isEmpty();
        else assertThat(paths).containsExactly(path);
        assertThat(method.getAnnotation(RequirePermission.class).value()).isEqualTo(permission);
    }
}
