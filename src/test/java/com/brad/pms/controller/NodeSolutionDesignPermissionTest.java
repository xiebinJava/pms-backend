package com.brad.pms.controller;

import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class NodeSolutionDesignPermissionTest {

    @Test
    void protectsEverySolutionDesignEndpointWithProjectRead() {
        for (String methodName : new String[]{"get", "save", "submit", "saveDecision", "completeReview", "assignReviewer", "updateReviewSuggestion", "confirmDecision"}) {
            var method = Arrays.stream(NodeSolutionDesignController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            var permission = method.getAnnotation(RequirePermission.class);
            assertThat(permission).as(methodName).isNotNull();
            assertThat(permission.value()).isEqualTo(PermissionCode.PROJECT_READ);
        }
    }
}
