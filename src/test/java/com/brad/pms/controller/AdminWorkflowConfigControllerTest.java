package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AdminWorkflowConfigControllerTest {

    @Test
    void exposesProjectNodeOptionsAsWorkflowReadEndpoint() {
        Optional<Method> method = Arrays.stream(AdminWorkflowConfigController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("projectNodeOptions"))
                .findFirst();
        assertThat(method).as("GET project workflow-node candidate endpoint").isPresent();

        RequestMapping controllerRoute = AdminWorkflowConfigController.class.getAnnotation(RequestMapping.class);
        GetMapping endpointRoute = method.orElseThrow().getAnnotation(GetMapping.class);
        RequirePermission permission = method.orElseThrow().getAnnotation(RequirePermission.class);

        assertThat(controllerRoute.value()).containsExactly("/admin/workflow-config");
        assertThat(endpointRoute.value()).containsExactly("/project-node-options");
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(PermissionCode.WORKFLOW_READ);
    }
}
