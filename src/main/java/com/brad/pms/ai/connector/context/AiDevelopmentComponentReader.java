package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.workflow.DevelopmentItemType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Splits development-item components by their true sources: persisted workbench JSON, the
 * requirement execution binding, and the fact-backed story rows (Task 8). No display defaults
 * are reported as saved content.
 */
@Component
@RequiredArgsConstructor
public class AiDevelopmentComponentReader {

    /** Research components whose state lives directly in the node's __components JSON. */
    private static final List<String> JSON_COMPONENTS = List.of(
            "requirement-receiving-analysis", "requirement-node-workbench", "topic-research",
            "topic-design-review", "story-node-workbench", "story-testing");
    private static final String REQUIREMENT_EXECUTION = "requirement-execution";

    private final AiContextReferenceProjector referenceProjector;
    private final AiContextReadPolicy targetReferencePolicy;

    public List<AiWorkflowContextDTO.Component> read(DevelopmentItemType itemType,
                                                     DevelopmentItemWorkflowDetailDTO item,
                                                     DevelopmentItemWorkflowNodeDTO node,
                                                     Integer entityVersion,
                                                     String componentKey,
                                                     List<String> unknownComponents) {
        List<String> attached = node.getRuntimeComponents() == null ? List.of() : node.getRuntimeComponents();
        List<String> requested;
        if (componentKey == null || componentKey.isBlank()) {
            requested = attached;
        } else if (attached.contains(componentKey)) {
            requested = List.of(componentKey);
        } else {
            throw com.brad.pms.common.exception.BusinessException.notFound("组件不存在或未附着于所选节点");
        }
        List<AiWorkflowContextDTO.Component> components = new ArrayList<>(requested.size());
        List<AiWorkflowContextDTO.Field> fields = contractFields(node.getFields());
        for (String key : requested) {
            components.add(toComponent(itemType, item, node, entityVersion, key, fields, unknownComponents));
        }
        return components;
    }

    private AiWorkflowContextDTO.Component toComponent(DevelopmentItemType itemType,
            DevelopmentItemWorkflowDetailDTO item, DevelopmentItemWorkflowNodeDTO node, Integer entityVersion,
            String key, List<AiWorkflowContextDTO.Field> fields, List<String> unknownComponents) {
        var config = node.getComponentConfigs() == null ? null : node.getComponentConfigs().get(key);
        if (REQUIREMENT_EXECUTION.equals(key)) {
            return itemType == DevelopmentItemType.REQUIREMENT
                    ? readExecutionTarget(item, entityVersion, fields)
                    : unsupported(key, config, fields, unknownComponents);
        }
        if (!JSON_COMPONENTS.contains(key)) {
            return unsupported(key, config, fields, unknownComponents);
        }
        com.fasterxml.jackson.databind.JsonNode raw = node.getFieldValues() == null ? null
                : node.getFieldValues().get("__components");
        com.fasterxml.jackson.databind.JsonNode state = raw == null ? null : raw.get(key);
        Map<String, Integer> versions = new LinkedHashMap<>();
        versions.put("node", node.getVersion());
        if (state == null || state.isNull()) {
            return new AiWorkflowContextDTO.Component(key, key, fields, List.of(), config,
                    null, "EMPTY", versions, null);
        }
        com.fasterxml.jackson.databind.JsonNode content =
                referenceProjector.projectComponentContent(itemType.name().toLowerCase(java.util.Locale.ROOT),
                        item.getId(), key, state);
        return new AiWorkflowContextDTO.Component(key, key, fields, List.of(), config,
                content, "LOADED", versions, null);
    }

    private AiWorkflowContextDTO.Component readExecutionTarget(DevelopmentItemWorkflowDetailDTO item,
                                                               Integer entityVersion,
                                                               List<AiWorkflowContextDTO.Field> fields) {
        Map<String, Integer> versions = new LinkedHashMap<>();
        versions.put("entity", entityVersion);
        var target = item.getExecutionTarget();
        if (target == null || target.getTargetType() == null || target.getTargetId() == null) {
            Map<String, Integer> emptyVersions = new LinkedHashMap<>();
            emptyVersions.put("entity", entityVersion);
            return new AiWorkflowContextDTO.Component(REQUIREMENT_EXECUTION, REQUIREMENT_EXECUTION,
                    fields, List.of(), null, null, "EMPTY", emptyVersions, null);
        }
        String targetType = target.getTargetType().name().toLowerCase(java.util.Locale.ROOT);
        boolean readable = targetReferencePolicy.canReadReference(targetType, target.getTargetId());
        Map<String, Object> content = new LinkedHashMap<>();
        if (readable) {
            put(content, "title", target.getTitle());
            put(content, "code", target.getCode());
            put(content, "status", target.getStatus());
            put(content, "ownerId", target.getOwnerId());
            put(content, "ownerName", target.getOwnerName());
            put(content, "progress", target.getProgress());
        }
        // A bound but unreadable target must never look unbound: LOADED with an empty projection,
        // and no target id/type/name may leak (referencesScope remains readable-only).
        return new AiWorkflowContextDTO.Component(REQUIREMENT_EXECUTION, REQUIREMENT_EXECUTION,
                fields, List.of(), null,
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(content),
                "LOADED", versions, null);
    }


    private AiWorkflowContextDTO.Component unsupported(String key, com.fasterxml.jackson.databind.JsonNode config,
                                                       List<AiWorkflowContextDTO.Field> fields,
                                                       List<String> unknownComponents) {
        unknownComponents.add(key);
        return new AiWorkflowContextDTO.Component(key, key, fields, List.of(), config,
                null, "UNSUPPORTED", null, null);
    }

    private List<AiWorkflowContextDTO.Field> contractFields(
            List<com.brad.pms.workflow.WorkflowFieldDefinition> definitions) {
        if (definitions == null) return List.of();
        return definitions.stream().filter(Objects::nonNull).map(definition -> new AiWorkflowContextDTO.Field(
                definition.key(), definition.label(),
                definition.type() == null ? null : definition.type().name(),
                definition.required(), definition.options(), definition.visible(),
                definition.binding(), definition.fullWidth())).toList();
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

}
