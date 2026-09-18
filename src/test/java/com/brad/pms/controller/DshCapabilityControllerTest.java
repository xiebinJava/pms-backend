package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshCapabilityDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DshCapabilityControllerTest {

    @Test
    void publishesStableReadOnlyCapabilitiesAndDshScopes() {
        ResponseResult<DshCapabilityDTO> response = new DshCapabilityController().capabilities();

        assertThat(response.getData().version()).isEqualTo("v1");
        assertThat(response.getData().tools())
                .containsExactly("pms_project_list", "pms_project_get", "pms_task_list");
        assertThat(response.getData().scopes())
                .contains("pms:project:read", "pms:task:read", "pms:workspace:embed")
                .doesNotContain("pms:command:execute");
        assertThat(response.getData().pageTypes())
                .containsExactly("project-list", "project-detail", "project-dashboard", "workflow-template");
    }
}
