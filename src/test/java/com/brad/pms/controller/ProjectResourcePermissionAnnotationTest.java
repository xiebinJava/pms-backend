package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectResourcePermissionAnnotationTest {
    @Test
    void responsibleWritesUseReadAndCommentsUseTheirOwnPermissionFamily() throws Exception {
        assertPermission(TaskController.class, "create", PermissionCode.PROJECT_READ);
        assertPermission(TaskController.class, "update", PermissionCode.PROJECT_READ);
        assertPermission(TaskController.class, "uploadAttachment", PermissionCode.PROJECT_READ);
        assertPermission(NodeController.class, "complete", PermissionCode.PROJECT_READ);
        assertPermission(NodeController.class, "updateSchedule", PermissionCode.PROJECT_READ);
        assertPermission(CommentController.class, "add", PermissionCode.PROJECT_COMMENT_WRITE);
        assertPermission(CommentController.class, "delete", PermissionCode.PROJECT_READ);
        assertPermission(MemberController.class, "add", PermissionCode.PROJECT_READ);
        assertPermission(MemberController.class, "remove", PermissionCode.PROJECT_READ);
    }

    private void assertPermission(Class<?> controller, String methodName, String expected) {
        Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as(controller.getSimpleName() + "." + methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
