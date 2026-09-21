package com.brad.pms.ai.contract;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PmsAgentContractRegistryTest {

    @Test
    void marksTheProductionConstructorForSpringInjection() throws NoSuchMethodException {
        assertThat(PmsAgentContractRegistry.class
                .getConstructor(com.brad.pms.ai.command.PmsCommandRegistry.class)
                .isAnnotationPresent(Autowired.class)).isTrue();
    }

    private static final Set<String> WRITE_COMMANDS = Set.of(
            "project.create", "member.add", "node.owner.update", "node.schedule.update",
            "task.create", "task.assign", "task.update", "node.complete", "project.update");
    private static final Set<String> READ_TOOLS = Set.of(
            "pms_project_list", "pms_project_get", "pms_task_list", "pms_query");

    @Test
    void loadsTheKickoffContractWithTheRealWorkflowNodeAndExecutableBindings() {
        PmsAgentContractLoader loader = new PmsAgentContractLoader(WRITE_COMMANDS, READ_TOOLS);

        PmsAgentContract contract = loader.load(new ClassPathResource(
                "agent-contracts/pms/project-kickoff.yaml"));

        assertThat(contract.contractId()).isEqualTo("pms-project-assistant/project-kickoff");
        assertThat(contract.agentId()).isEqualTo("project_assistant");
        assertThat(contract.contractKey()).isEqualTo("project-kickoff");
        assertThat(contract.workflowNodeKeys()).containsExactly("kickoff");
        assertThat(contract.required()).isTrue();
        assertThat(contract.specializedAgents()).isEmpty();
        assertThat(contract.writeCommands()).containsExactlyElementsOf(List.of(
                "project.create", "project.update", "member.add", "node.owner.update", "node.schedule.update",
                "task.create", "task.assign", "task.update", "node.complete"));
        assertThat(contract.readToolBindings()).containsEntry("project-context", "pms_project_get");
        assertThat(contract.completionCriteria()).isNotEmpty();
        assertThat(contract.failureStrategies()).isNotEmpty();
    }

    @Test
    void rejectsAWriteCommandThatIsNotRegisteredByPms() {
        PmsAgentContractLoader loader = new PmsAgentContractLoader(WRITE_COMMANDS, READ_TOOLS);
        String yaml = validYaml("project.delete", "pms_project_get");

        assertThatThrownBy(() -> loader.load(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未注册的写入命令: project.delete");
    }

    @Test
    void rejectsAReadToolThatIsNotPublishedByDsh() {
        PmsAgentContractLoader loader = new PmsAgentContractLoader(WRITE_COMMANDS, READ_TOOLS);
        String yaml = validYaml("project.create", "pms_people_list");

        assertThatThrownBy(() -> loader.load(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未发布的读取工具: pms_people_list");
    }

    @Test
    void rejectsAnUnsupportedConfirmationPolicy() {
        PmsAgentContractLoader loader = new PmsAgentContractLoader(WRITE_COMMANDS, READ_TOOLS);
        String yaml = validYaml("project.create", "pms_project_get")
                .replace("preview-and-confirm", "auto-execute");

        assertThatThrownBy(() -> loader.load(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("确认策略无效: auto-execute");
    }

    @Test
    void rejectsAWriteCommandWithoutAConfirmationPolicy() {
        PmsAgentContractLoader loader = new PmsAgentContractLoader(WRITE_COMMANDS, READ_TOOLS);
        String yaml = validYaml("task.create", "pms_project_get");

        assertThatThrownBy(() -> loader.load(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("写入命令缺少确认策略: task.create");
    }

    @Test
    void rejectsDuplicateContractIdentityAndKeepsLoadedCollectionsImmutable() {
        PmsAgentContractLoader loader = new PmsAgentContractLoader(WRITE_COMMANDS, READ_TOOLS);
        PmsAgentContract first = loader.load(new ClassPathResource(
                "agent-contracts/pms/project-kickoff.yaml"));
        PmsAgentContractRegistry registry = new PmsAgentContractRegistry(List.of(first));

        assertThatThrownBy(() -> registry.load(List.of(first, first)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("契约标识重复");
        assertThatThrownBy(() -> first.writeCommands().add("project.delete"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(first.readToolBindings()).isEqualTo(Map.of(
                "project-context", "pms_project_get",
                "task-context", "pms_task_list",
                "project-list", "pms_project_list",
                "bounded-query", "pms_query"));
    }

    private static String validYaml(String writeCommand, String readTool) {
        return """
                contractId: pms-project-assistant/project-kickoff
                agentId: project_assistant
                contractKey: project-kickoff
                workflowNodeKeys: [kickoff]
                contractVersion: 1.0.0
                required: true
                locale: zh-CN
                principalRole: PMS 项目经理
                specializedAgents: []
                readCapabilities: [项目上下文]
                readToolBindings:
                  project-context: %s
                writeCommands: [%s]
                confirmationPolicies:
                  project.create: preview-and-confirm
                entryConditions: [首个节点]
                inputs: [项目基本信息]
                missingInputRules: [不得猜测]
                executionSteps: [读取状态]
                completionCriteria: [必填字段完整]
                failureStrategies: [鉴权失败即停止]
                terminationConditions: [用户未确认]
                """.formatted(readTool, writeCommand);
    }
}
