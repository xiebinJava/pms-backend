package com.brad.pms.ai.orchestrator.adapter;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Shared binding resolution for project and topic resource-node adapters. The
 * component selection rule mirrors the frozen CLI resolver: writable
 * saveContracts are counted, the first array element is never selected
 * implicitly, and project entity versions are never used as node versions.
 */
public final class ResourceNodeBindingResolver {

    private ResourceNodeBindingResolver() {
    }

    static ResourceNodeBinding resolve(AiWorkflowContextDTO context, Long requestedNodeId,
                                       String requestedComponentKey, Long suppliedNodeVersion) {
        if (context == null || context.resourceType() == null || context.resourceId() == null) {
            throw BusinessException.error("CAPABILITY_UNAVAILABLE: 资源上下文不可用");
        }
        AiWorkflowContextDTO.CurrentNode currentNode = context.currentNode();
        if (currentNode == null || currentNode.id() == null) {
            throw BusinessException.error("CAPABILITY_UNAVAILABLE: 上下文没有当前节点");
        }
        Long nodeId = requestedNodeId == null ? currentNode.id() : requestedNodeId;
        if (!Objects.equals(nodeId, currentNode.id())) {
            throw BusinessException.conflict("当前节点不一致: 指定节点 " + nodeId
                    + " 不是当前可写节点 " + currentNode.id());
        }
        AiWorkflowContextDTO.Node node = findNode(context, nodeId);
        if (node == null) {
            throw BusinessException.conflict("当前节点不一致: 上下文没有目标节点 " + nodeId);
        }

        List<AiWorkflowContextDTO.Component> components = node.components() == null
                ? List.of() : node.components();
        if (components.isEmpty()) {
            throw BusinessException.error("CAPABILITY_UNAVAILABLE: 当前节点没有后端声明的组件或 saveContract");
        }
        List<AiWorkflowContextDTO.Component> writable = components.stream()
                .filter(component -> !writableFields(component).isEmpty())
                .toList();
        String requestedKey = normalize(requestedComponentKey);
        AiWorkflowContextDTO.Component selected;
        if (requestedKey == null) {
            if (writable.size() == 1) {
                selected = writable.get(0);
            } else if (writable.size() > 1) {
                throw BusinessException.error(
                        "CAPABILITY_UNAVAILABLE: 当前节点存在多个可写组件，请显式指定 componentKey");
            } else {
                throw BusinessException.error("CAPABILITY_UNAVAILABLE: 当前节点没有可写 saveContract");
            }
        } else {
            selected = findComponent(components, requestedKey);
            if (selected == null) {
                throw BusinessException.error("CAPABILITY_UNAVAILABLE: 未找到组件 " + requestedKey);
            }
            if (writableFields(selected).isEmpty()) {
                throw BusinessException.error(
                        "CAPABILITY_UNAVAILABLE: 组件 " + requestedKey + " 没有可写 saveContract");
            }
        }

        Map<String, Object> saveContract = selected.saveContract() == null ? Map.of() : selected.saveContract();
        Long resolvedNodeVersion = nodeVersion(saveContract, node);
        if (resolvedNodeVersion == null) {
            throw BusinessException.error("CAPABILITY_UNAVAILABLE: 组件没有声明节点版本");
        }
        if (suppliedNodeVersion != null && !Objects.equals(suppliedNodeVersion, resolvedNodeVersion)) {
            throw BusinessException.conflict("节点版本不一致: 请求 " + suppliedNodeVersion
                    + "，上下文 " + resolvedNodeVersion);
        }

        List<String> writableFields = writableFields(selected);
        List<String> protectedFields = strings(saveContract.get("protectedFields"));
        Long entityVersion = context.entityVersion() == null ? null : context.entityVersion().longValue();
        Integer workflowInstanceVersion = context.meta() == null ? null : context.meta().workflowInstanceVersion();
        Integer componentVersion = selected.versions() == null ? null : selected.versions().get("content");
        Long contextVersion = context.version() == null ? null : context.version().longValue();
        String label = selected.label() == null || selected.label().isBlank()
                ? node.label() : selected.label();
        return new ResourceNodeBinding(context.resourceType(), context.resourceId(), nodeId,
                resolvedNodeVersion, selected.key(), label, saveContract, writableFields, protectedFields,
                true, entityVersion, workflowInstanceVersion, componentVersion, contextVersion);
    }

    private static AiWorkflowContextDTO.Node findNode(AiWorkflowContextDTO context, Long nodeId) {
        if (context.workflow() == null || context.workflow().nodes() == null) return null;
        return context.workflow().nodes().stream()
                .filter(candidate -> candidate != null && Objects.equals(candidate.id(), nodeId))
                .findFirst()
                .orElse(null);
    }

    private static AiWorkflowContextDTO.Component findComponent(
            List<AiWorkflowContextDTO.Component> components, String requestedKey) {
        return components.stream().filter(candidate -> candidate != null
                && (Objects.equals(candidate.key(), requestedKey)
                || matchesContractKey(candidate.saveContract(), "componentKey", requestedKey)
                || matchesContractKey(candidate.saveContract(), "workbench", requestedKey)))
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesContractKey(Map<String, Object> saveContract, String key,
                                              String requestedKey) {
        if (saveContract == null) return false;
        Object value = saveContract.get(key);
        return value instanceof String text && Objects.equals(text, requestedKey);
    }

    /**
     * Project declares nodeVersion on the saveContract; topic declares version
     * on the saveContract and the workflow node. The project entity version is
     * deliberately never used here.
     */
    private static Long nodeVersion(Map<String, Object> saveContract, AiWorkflowContextDTO.Node node) {
        Long fromContract = number(saveContract.get("nodeVersion"));
        if (fromContract != null) return fromContract;
        Long fromVersion = number(saveContract.get("version"));
        if (fromVersion != null) return fromVersion;
        return number(node.version());
    }

    static List<String> writableFields(AiWorkflowContextDTO.Component component) {
        if (component == null || component.saveContract() == null) return List.of();
        Map<String, Object> contract = component.saveContract();
        List<String> declared = strings(contract.get("effectiveWritableFields"));
        if (declared.isEmpty()) declared = strings(contract.get("writableFields"));
        Set<String> unique = new LinkedHashSet<>();
        for (String field : declared) {
            if (field != null && !field.isBlank()) unique.add(field);
        }
        return List.copyOf(unique);
    }

    public static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String text && !text.isBlank()) result.add(text);
        }
        return result;
    }

    private static Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        String normalized = value == null ? null : value.trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
    }
}
