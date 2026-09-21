package com.brad.pms.controller;

import com.brad.pms.ai.contract.PmsAgentContract;
import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshAgentContractDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DshAgentContractControllerTest {

    @Test
    void returnsTheVersionedContractWithoutLocalImplementationSecrets() {
        PmsAgentContract contract = contract();
        DshAgentContractController controller = new DshAgentContractController(
                new PmsAgentContractRegistry(List.of(contract)));

        ResponseResult<DshAgentContractDTO> response = controller.get("project_assistant", "project-kickoff");

        assertThat(response.getData().contractId()).isEqualTo("pms-project-assistant/project-kickoff");
        assertThat(response.getData().workflowNodeKeys()).containsExactly("kickoff");
        assertThat(response.getData().contractVersion()).isEqualTo("1.0.0");
        assertThat(response.getData().contentSha256()).matches("[a-f0-9]{64}");
        assertThat(response.getData().writeCommands()).containsExactly("project.create");
        assertThat(response.getData().readToolBindings()).containsEntry("project-context", "pms_project_get");
        assertThat(response.getData().toString()).doesNotContain("secret", "password", "jwt");
    }

    @Test
    void rejectsAnUnknownContractInsteadOfReturningAFallbackPrompt() {
        DshAgentContractController controller = new DshAgentContractController(
                new PmsAgentContractRegistry(List.of(contract())));

        assertThatThrownBy(() -> controller.get("project_assistant", "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到 Agent 契约");
    }

    private static PmsAgentContract contract() {
        return new PmsAgentContract(
                "pms-project-assistant/project-kickoff", "project_assistant", "project-kickoff",
                List.of("kickoff"), "1.0.0", true, "zh-CN", "PMS 项目经理", List.of(),
                List.of("项目上下文"), Map.of("project-context", "pms_project_get"),
                List.of("project.create"), Map.of("project.create", "preview-and-confirm"),
                List.of("首个节点"), List.of("项目基本信息"), List.of("不得猜测"),
                List.of("读取状态"), List.of("必填字段完整"), List.of("鉴权失败即停止"),
                List.of("用户未确认"));
    }
}
