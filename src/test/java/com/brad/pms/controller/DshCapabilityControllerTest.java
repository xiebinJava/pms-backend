package com.brad.pms.controller;

import com.brad.pms.ai.contract.PmsAgentContract;
import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshCapabilityDTO;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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

    @Test
    void advertisesLoadedAgentContractsAsAnOptionalDiscoveryExtension() {
        PmsAgentContract contract = new PmsAgentContract(
                "pms-project-assistant/project-kickoff", "project_assistant", "project-kickoff",
                List.of("kickoff"), "1.0.0", true, "zh-CN", "PMS 项目经理", List.of(),
                List.of("项目上下文"), Map.of("project-context", "pms_project_get"),
                List.of("project.create"), Map.of("project.create", "preview-and-confirm"),
                List.of("首个节点"), List.of("项目基本信息"), List.of("不得猜测"),
                List.of("读取状态"), List.of("必填字段完整"), List.of("鉴权失败即停止"),
                List.of("用户未确认"));

        DshCapabilityDTO capabilities = new DshCapabilityController(
                new PmsAgentContractRegistry(List.of(contract))).capabilities().getData();

        assertThat(capabilities.agentContracts()).singleElement()
                .satisfies(descriptor -> {
                    assertThat(descriptor.agentId()).isEqualTo("project_assistant");
                    assertThat(descriptor.contractKey()).isEqualTo("project-kickoff");
                    assertThat(descriptor.workflowNodeKeys()).containsExactly("kickoff");
                    assertThat(descriptor.contractVersion()).isEqualTo("1.0.0");
                    assertThat(descriptor.endpoint()).isEqualTo(
                            "/integration/dsh/v1/agent-contracts/{agentId}/{contractKey}");
                    assertThat(descriptor.scope()).isEqualTo("pms:query:read");
                    assertThat(descriptor.required()).isTrue();
                });
    }
}
