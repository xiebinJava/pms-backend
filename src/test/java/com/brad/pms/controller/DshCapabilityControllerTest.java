package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshCapabilityDTO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DshCapabilityControllerTest {

    @Test
    void publishesReadAndWriteCapabilitiesRequiredByThePmsAgent() {
        ResponseResult<DshCapabilityDTO> response = new DshCapabilityController().capabilities();

        assertThat(response.getData().version()).isEqualTo("v1");
        assertThat(response.getData().tools())
                .containsExactly(
                        "pms_project_list",
                        "pms_project_get",
                        "pms_task_list",
                        "pms_query",
                        "pms_command_preview",
                        "pms_command_execute");
        assertThat(response.getData().scopes())
                .contains(
                        "pms:project:read",
                        "pms:task:read",
                        "pms:query:read",
                        "pms:task:write",
                        "pms:command:preview",
                        "pms:command:execute",
                        "pms:workflow:write",
                        "pms:project:write",
                        "pms:workspace:embed");
        assertThat(response.getData().pageTypes())
                .containsExactly("project-list", "project-detail", "project-dashboard", "workflow-template");
        assertThat(response.getData().queries())
                .extracting(query -> query.resource())
                .containsExactly("projects", "tasks");
        assertThat(response.getData().commands())
                .extracting(command -> command.name())
                .contains("project.create", "project.archive", "task.create", "node.complete");
        assertThat(response.getData().commands().stream()
                .filter(command -> command.name().equals("project.create"))
                .findFirst()
                .orElseThrow()
                .scopes())
                .containsExactly("pms:project:write", "pms:command:preview", "pms:command:execute");
    }

    @Test
    void hidesWriteCapabilitiesFromAReadOnlyDelegationToken() {
        LoginUser user = new LoginUser(1L, "dsh", "DSH");
        user.setDelegationScopes(List.of("pms:project:read", "pms:task:read", "pms:workspace:embed"));
        UserContext.set(user);
        try {
            DshCapabilityDTO capabilities = new DshCapabilityController().capabilities().getData();

            assertThat(capabilities.tools())
                    .containsExactly("pms_project_list", "pms_project_get", "pms_task_list");
            assertThat(capabilities.commands()).isEmpty();
            assertThat(capabilities.scopes())
                    .containsExactly("pms:project:read", "pms:task:read", "pms:workspace:embed");
        } finally {
            UserContext.clear();
        }
    }
}
