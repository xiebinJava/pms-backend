package com.brad.pms.ai.orchestrator.contract;

import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure projection of a focused story context into the story-node write contract. */
@Component
public class StoryNodeContractResolver {

    private static final String COMPONENT_KEY = "story-node-workbench";
    private static final Map<String, String> LABELS = Map.of(
            "implementationNote", "实现说明",
            "selfTestResult", "自测结果",
            "codeLink", "代码链接",
            "mergeStatus", "合并状态",
            "deployEnv", "部署环境",
            "testCases", "测试用例");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Resolution resolve(AiWorkflowContextDTO context) {
        if (context == null || !"story".equalsIgnoreCase(context.resourceType())) {
            throw new IllegalArgumentException("故事节点上下文无效");
        }
        AiWorkflowContextDTO.Node node = selectedNode(context);
        AiWorkflowContextDTO.Component component = node.components().stream()
                .filter(value -> value != null && COMPONENT_KEY.equals(value.key()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("故事节点缺少 story-node-workbench 组件"));
        Map<String, Object> saveContract = component.saveContract();
        if (saveContract == null) throw new IllegalArgumentException("故事节点没有可写 saveContract");

        String variant = text(saveContract.get("variant"));
        List<String> writable = strings(saveContract.get("writableFields"));
        List<String> protectedFields = strings(saveContract.get("protectedFields"));
        List<StoryNodeContract.Field> allFields = fieldContracts(saveContract.get("fieldContracts"));
        List<StoryNodeContract.Field> required = allFields.stream()
                .filter(StoryNodeContract.Field::required).toList();
        if (required.isEmpty()) {
            List<String> requiredKeys = strings(saveContract.get("requiredFields"));
            required = requiredKeys.stream()
                    .map(key -> new StoryNodeContract.Field(key, LABELS.getOrDefault(key, key), "string",
                            true, false, false))
                    .toList();
        }
        String command = text(saveContract.get("command"));
        StoryNodeContract contract = new StoryNodeContract(
                node.id(), node.key(), node.label(), node.version(), variant, required, writable,
                protectedFields, command == null ? List.of() : List.of(command), List.of(),
                List.of("必填字段不能为空或只包含空白字符"), !writable.isEmpty() && command != null);
        return new Resolution(contract, missingFields(component.content(), variant, required));
    }

    public List<String> missingFields(AiWorkflowContextDTO context,
                                      StoryNodeContract contract,
                                      Map<String, Object> patch) {
        AiWorkflowContextDTO.Component component = component(context, contract.nodeId());
        JsonNode incoming = objectMapper.valueToTree(patch == null ? Map.of() : patch);
        return com.brad.pms.workflow.StoryNodeWorkbenchPolicy.missingRequiredFields(
                component.content(), incoming, contract.variant());
    }

    private AiWorkflowContextDTO.Node selectedNode(AiWorkflowContextDTO context) {
        Long nodeId = context.currentNode() == null ? null : context.currentNode().id();
        if (nodeId == null || context.workflow() == null) {
            throw new IllegalArgumentException("故事上下文没有选中的流程节点");
        }
        return context.workflow().nodes().stream()
                .filter(node -> node != null && Objects.equals(node.id(), nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("选中的流程节点不存在"));
    }

    private AiWorkflowContextDTO.Component component(AiWorkflowContextDTO context, Long nodeId) {
        if (context == null || context.workflow() == null) {
            throw new IllegalArgumentException("故事上下文没有流程节点");
        }
        AiWorkflowContextDTO.Node node = context.workflow().nodes().stream()
                .filter(value -> value != null && Objects.equals(value.id(), nodeId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("故事流程节点不存在"));
        return node.components().stream().filter(value -> value != null && COMPONENT_KEY.equals(value.key()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("故事节点缺少工作台组件"));
    }

    private List<StoryNodeContract.Field> fieldContracts(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<StoryNodeContract.Field> result = new ArrayList<>();
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> map)) continue;
            String key = text(map.get("key"));
            if (key == null || key.isBlank()) continue;
            result.add(new StoryNodeContract.Field(
                    key,
                    textOrDefault(map.get("label"), LABELS.getOrDefault(key, key)),
                    textOrDefault(map.get("type"), "string"),
                    bool(map.get("required")), bool(map.get("nullable")), bool(map.get("collection"))));
        }
        return List.copyOf(result);
    }

    private List<String> missingFields(JsonNode content, String variant,
                                       List<StoryNodeContract.Field> fields) {
        if (variant == null) return fields.stream().filter(StoryNodeContract.Field::required)
                .map(StoryNodeContract.Field::key).toList();
        try {
            return StoryNodeWorkbenchPolicyAdapter.missingRequiredFields(content, variant);
        } catch (RuntimeException ignored) {
            return fields.stream().filter(StoryNodeContract.Field::required)
                    .filter(field -> missing(content, field.key())).map(StoryNodeContract.Field::key).toList();
        }
    }

    private boolean missing(JsonNode content, String key) {
        JsonNode value = content == null ? null : content.get(key);
        return value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())
                || ((value.isArray() || value.isObject()) && value.isEmpty());
    }

    private String text(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private String textOrDefault(Object value, String fallback) {
        String result = text(value);
        return result == null ? fallback : result;
    }

    private boolean bool(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast)
                .filter(valueItem -> !valueItem.isBlank()).toList();
    }

    /** Keeps the resolver independent from the JSON representation used by the context DTO. */
    private static final class StoryNodeWorkbenchPolicyAdapter {
        private static List<String> missingRequiredFields(JsonNode content, String variant) {
            return com.brad.pms.workflow.StoryNodeWorkbenchPolicy.missingRequiredFields(
                    content, JsonNodeFactory.instance.objectNode(), variant);
        }
    }

    public record Resolution(StoryNodeContract contract, List<String> missingFields) {
        public Resolution {
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        }
    }
}
