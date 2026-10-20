package com.brad.pms.ai.orchestrator;

import com.brad.pms.ai.orchestrator.adapter.ProjectOrchestratorAdapter;
import com.brad.pms.ai.orchestrator.adapter.ResourceNodeBinding;
import com.brad.pms.ai.orchestrator.adapter.ResourceOrchestratorAdapterRegistry;
import com.brad.pms.ai.orchestrator.adapter.TopicOrchestratorAdapter;
import com.brad.pms.ai.orchestrator.contract.AgentDraft;
import com.brad.pms.ai.orchestrator.contract.AgentDraftItem;
import com.brad.pms.ai.orchestrator.contract.AiAgentDraftValidator;
import com.brad.pms.ai.orchestrator.contract.DraftValidationResult;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceOrchestratorAdapterTest {

    private ProjectOrchestratorAdapter projectAdapter;
    private TopicOrchestratorAdapter topicAdapter;
    private ResourceOrchestratorAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        AiAgentDraftValidator validator = new AiAgentDraftValidator();
        projectAdapter = new ProjectOrchestratorAdapter(validator);
        topicAdapter = new TopicOrchestratorAdapter(validator);
        registry = new ResourceOrchestratorAdapterRegistry(List.of(projectAdapter, topicAdapter));
    }

    @Test
    void selectsTheSingleWritableComponentImplicitly() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", Map.of(
                        "command", "node.field.update",
                        "nodeVersion", 6,
                        "writableFields", List.of("testResult"),
                        "protectedFields", List.of("projectId", "nodeId", "workbench"),
                        "versions", Map.of("content", 4)))));

        ResourceNodeBinding binding = projectAdapter.resolveBinding(context, 301L, null, null);

        assertThat(binding.nodeId()).isEqualTo(301L);
        assertThat(binding.nodeVersion()).isEqualTo(6L);
        assertThat(binding.componentKey()).isEqualTo("solution-design");
        assertThat(binding.writableFields()).containsExactly("testResult");
        assertThat(binding.writeCapable()).isTrue();
    }

    @Test
    void rejectsMultipleWritableComponentsWithoutAnExplicitComponentKey() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", writableContract("solution-design", 6)),
                component("plan-resource-risk", writableContract("plan-resource-risk", 6))));

        assertThatThrownBy(() -> projectAdapter.resolveBinding(context, 301L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CAPABILITY_UNAVAILABLE")
                .hasMessageContaining("多个可写组件");
    }

    @Test
    void resolvesAnExplicitComponentAmongMultipleWritableOnes() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", writableContract("solution-design", 6)),
                component("plan-resource-risk", writableContract("plan-resource-risk", 6))));

        ResourceNodeBinding binding = projectAdapter.resolveBinding(context, 301L, "plan-resource-risk", null);

        assertThat(binding.componentKey()).isEqualTo("plan-resource-risk");
    }

    @Test
    void rejectsAnExplicitComponentWithoutAWritableSaveContract() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", writableContract("solution-design", 6)),
                component("project-basic-info", null)));

        assertThatThrownBy(() -> projectAdapter.resolveBinding(context, 301L, "project-basic-info", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CAPABILITY_UNAVAILABLE")
                .hasMessageContaining("project-basic-info");
    }

    @Test
    void rejectsNodesWithoutAnyWritableSaveContract() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("project-basic-info", null)));

        assertThatThrownBy(() -> projectAdapter.resolveBinding(context, 301L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CAPABILITY_UNAVAILABLE")
                .hasMessageContaining("没有可写 saveContract");
    }

    @Test
    void rejectsAWrongCurrentNodeAsAConflict() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", writableContract("solution-design", 6))));

        assertThatThrownBy(() -> projectAdapter.resolveBinding(context, 302L, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前节点不一致");
    }

    @Test
    void neverUsesTheProjectEntityVersionAsTheNodeVersion() {
        AiWorkflowContextDTO context = projectContext(301L, 99, List.of(
                component("solution-design", writableContract("solution-design", 6))));

        ResourceNodeBinding binding = projectAdapter.resolveBinding(context, 301L, null, null);

        assertThat(binding.nodeVersion()).isEqualTo(6L);
        assertThat(binding.contextVersion()).isEqualTo(99L);
    }

    @Test
    void rejectsAStaleSuppliedNodeVersionAsAConflict() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", writableContract("solution-design", 6))));

        assertThatThrownBy(() -> projectAdapter.resolveBinding(context, 301L, null, 5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点版本不一致");
    }

    @Test
    void resolvesTopicNodeVersionsFromTheSelectedNodeContract() {
        AiWorkflowContextDTO context = topicContext(511L, List.of(
                component("topic-design-review", Map.of(
                        "command", "development-item.node.field.update",
                        "version", 4,
                        "writableFields", List.of("conclusion"),
                        "protectedFields", List.of("status")))));

        ResourceNodeBinding binding = topicAdapter.resolveBinding(context, 511L, "topic-design-review", null);

        assertThat(binding.nodeVersion()).isEqualTo(4L);
        assertThat(binding.componentKey()).isEqualTo("topic-design-review");
        assertThat(binding.entityVersion()).isNull();
    }

    @Test
    void registersProjectAndTopicAdaptersOnly() {
        assertThat(registry.find("project")).contains(projectAdapter);
        assertThat(registry.find("topic")).contains(topicAdapter);
        assertThat(registry.find("requirement")).isEmpty();
        assertThatThrownBy(() -> registry.require("story"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CAPABILITY_UNAVAILABLE");
    }

    @Test
    void validatesResourceNodeDraftsAgainstTheResolvedBinding() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", Map.of(
                        "command", "node.field.update",
                        "nodeVersion", 6,
                        "writableFields", List.of("testResult"),
                        "protectedFields", List.of("projectId", "nodeId", "workbench")))));
        ResourceNodeBinding binding = projectAdapter.resolveBinding(context, 301L, null, null);
        AgentDraft draft = new AgentDraft("resource-node/v1", new AgentDraft.DraftPayload(
                List.of(new AgentDraftItem("测试结果为通过",
                        List.of("project-node:301#component:solution-design"), "high")),
                List.of(), List.of(), List.of(), Map.of("testResult", "PASS")));

        DraftValidationResult result = projectAdapter.validateDraft(draft, context, binding);

        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsUnknownAndProtectedFieldsInResourceNodeDrafts() {
        AiWorkflowContextDTO context = projectContext(301L, 3, List.of(
                component("solution-design", Map.of(
                        "command", "node.field.update",
                        "nodeVersion", 6,
                        "writableFields", List.of("testResult"),
                        "protectedFields", List.of("projectId", "nodeId", "workbench")))));
        ResourceNodeBinding binding = projectAdapter.resolveBinding(context, 301L, null, null);
        AgentDraft draft = new AgentDraft("resource-node/v1", new AgentDraft.DraftPayload(
                List.of(), List.of(), List.of(), List.of(),
                Map.of("testResult", "PASS", "workbench", "other", "notWritable", "x")));

        DraftValidationResult result = projectAdapter.validateDraft(draft, context, binding);

        assertThat(result.errors()).extracting("code")
                .contains("PROTECTED_FIELD", "UNKNOWN_FIELD");
    }

    private AiWorkflowContextDTO projectContext(Long nodeId, Integer entityVersion,
                                                List<AiWorkflowContextDTO.Component> components) {
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(nodeId, "solution", "方案", 0, 1,
                components, List.of(), Map.of(), 6, null, null, null, null, null,
                null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("project", 42L, entityVersion,
                new AiWorkflowContextDTO.CurrentNode(nodeId, "solution", "方案"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("node.field.update"), entityVersion, Map.of(), List.of(),
                new AiWorkflowContextDTO.ContextMeta(1, true, null, null, "focused", null,
                        "component", nodeId, null, true, "ACTIVE", null, 2, null, null, null, List.of()));
    }

    private AiWorkflowContextDTO topicContext(Long nodeId,
                                              List<AiWorkflowContextDTO.Component> components) {
        AiWorkflowContextDTO.Node node = new AiWorkflowContextDTO.Node(nodeId, "design", "设计", 0, 1,
                components, List.of(), Map.of(), 4, null, null, null, null, null,
                null, null, "LOADED", null, null);
        return new AiWorkflowContextDTO("topic", 43L, 4,
                new AiWorkflowContextDTO.CurrentNode(nodeId, "design", "设计"),
                new AiWorkflowContextDTO.Workflow(1L, 1, List.of(node)),
                List.of("development-item.node.field.update"), null, Map.of(), List.of(),
                new AiWorkflowContextDTO.ContextMeta(1, true, null, null, "focused", null,
                        "component", nodeId, null, true, "ACTIVE", null, null, null, null, null, List.of()));
    }

    private AiWorkflowContextDTO.Component component(String key, Map<String, Object> saveContract) {
        return new AiWorkflowContextDTO.Component(key, key, List.of(), List.of(), null,
                new ObjectMapper().valueToTree(Map.of()), "LOADED",
                saveContract == null ? null : Map.of("content", 4), saveContract);
    }

    private Map<String, Object> writableContract(String key, Integer nodeVersion) {
        return Map.of(
                "command", "node.field.update",
                "nodeVersion", nodeVersion,
                "componentKey", key,
                "writableFields", List.of("testResult"),
                "protectedFields", List.of("projectId", "nodeId", "workbench"));
    }
}
