package com.brad.pms.ai.connector.context;

import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowNodeDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.workflow.DevelopmentItemType;
import com.brad.pms.workflow.StoryNodeWorkbenchPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final com.brad.pms.service.DevelopmentStoryManagementService storyService;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    public List<AiWorkflowContextDTO.Component> read(DevelopmentItemType itemType,
                                                     DevelopmentItemWorkflowDetailDTO item,
                                                     DevelopmentItemWorkflowNodeDTO node,
                                                     Integer entityVersion,
                                                     String componentKey,
                                                     List<String> unknownComponents) {
        return read(itemType, item, node, entityVersion, componentKey, unknownComponents,
                referenceProjector, null);
    }

    public List<AiWorkflowContextDTO.Component> read(DevelopmentItemType itemType,
                                                     DevelopmentItemWorkflowDetailDTO item,
                                                     DevelopmentItemWorkflowNodeDTO node,
                                                     Integer entityVersion,
                                                     String componentKey,
                                                     List<String> unknownComponents,
                                                     AiContextReferenceProjector projector,
                                                     ReadAuthorizationSnapshot authorizationSnapshot) {
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
            components.add(toComponent(itemType, item, node, entityVersion, key, fields, unknownComponents,
                    projector, authorizationSnapshot));
        }
        return components;
    }

    private AiWorkflowContextDTO.Component toComponent(DevelopmentItemType itemType,
            DevelopmentItemWorkflowDetailDTO item, DevelopmentItemWorkflowNodeDTO node, Integer entityVersion,
            String key, List<AiWorkflowContextDTO.Field> fields, List<String> unknownComponents,
            AiContextReferenceProjector projector, ReadAuthorizationSnapshot authorizationSnapshot) {
        var config = node.getComponentConfigs() == null ? null : node.getComponentConfigs().get(key);
        if (REQUIREMENT_EXECUTION.equals(key)) {
            return itemType == DevelopmentItemType.REQUIREMENT
                    ? readExecutionTarget(item, entityVersion, fields, authorizationSnapshot)
                    : unsupported(key, config, fields, unknownComponents);
        }
        if ("story-list".equals(key)) {
            if (itemType != DevelopmentItemType.TOPIC) {
                return unsupported(key, config, fields, unknownComponents);
            }
            return readStoryList(item, node, fields, unknownComponents, authorizationSnapshot);
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
                    null, "EMPTY", versions, saveContract(itemType, item, node, key, config));
        }
        com.fasterxml.jackson.databind.JsonNode content =
                projector.projectComponentContent(itemType.name().toLowerCase(java.util.Locale.ROOT),
                        item.getId(), key, state);
        return new AiWorkflowContextDTO.Component(key, key, fields, List.of(), config,
                content, "LOADED", versions, saveContract(itemType, item, node, key, config));
    }

    /**
     * Describes the narrow server command an AI writer may use for JSON-backed story workbench
     * content. The read context remains pure; this is only a capability declaration.
     */
    private Map<String, Object> saveContract(DevelopmentItemType itemType,
                                             DevelopmentItemWorkflowDetailDTO item,
                                             DevelopmentItemWorkflowNodeDTO node,
                                             String componentKey,
                                             com.fasterxml.jackson.databind.JsonNode config) {
        if (itemType != DevelopmentItemType.STORY
                || !"story-node-workbench".equals(componentKey)) return null;
        String variant = config == null ? null : config.path("variant").asText(null);
        if (!StoryNodeWorkbenchPolicy.supports(variant)) return null;
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("command", "development-item.node.field.update");
        contract.put("itemType", itemType.name());
        contract.put("itemId", item == null ? null : item.getId());
        contract.put("nodeId", node.getId());
        contract.put("version", node.getVersion());
        contract.put("componentKey", componentKey);
        contract.put("variant", variant);
        contract.put("fieldValuesPath", "__components." + componentKey);
        contract.put("writableFields", StoryNodeWorkbenchPolicy.writableFields(variant));
        return contract;
    }

    private AiWorkflowContextDTO.Component readExecutionTarget(DevelopmentItemWorkflowDetailDTO item,
                                                               Integer entityVersion,
                                                               List<AiWorkflowContextDTO.Field> fields,
                                                               ReadAuthorizationSnapshot authorizationSnapshot) {
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
        boolean readable = canReadReference(authorizationSnapshot, targetType, target.getTargetId());
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



    /**
     * Story facts come from the real story rows (never the parent node's version):
     * versions.node covers only the saved testing JSON; stories carry no entity version.
     */
    private AiWorkflowContextDTO.Component readStoryList(DevelopmentItemWorkflowDetailDTO item,
            DevelopmentItemWorkflowNodeDTO node, List<AiWorkflowContextDTO.Field> fields,
            List<String> unknownComponents, ReadAuthorizationSnapshot authorizationSnapshot) {
        Long topicId = item.getTopicId();
        Long mountNode = item.getTopicWorkflowNodeId();
        if (topicId == null || mountNode == null || !Objects.equals(mountNode, node.getId())) {
            unknownComponents.add("story-list");
            return new AiWorkflowContextDTO.Component("story-list", "story-list", fields, List.of(),
                    null, null, "UNSUPPORTED", null, null);
        }
        List<Map<String, Object>> stories = new ArrayList<>();
        for (var story : storyService.listByTopic(topicId)) {
            if (story == null || story.getId() == null) continue;
            if (!Objects.equals(story.getTopicWorkflowNodeId(), node.getId())) continue;
            if (!canReadReference(authorizationSnapshot, "story", story.getId())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            put(row, "id", story.getId());
            put(row, "title", story.getTitle());
            put(row, "status", story.getStatus());
            put(row, "ownerName", story.getOwnerName());
            put(row, "progress", story.getProgress());
            stories.add(row);
        }
        com.fasterxml.jackson.databind.JsonNode testingState = node.getFieldValues() == null ? null
                : node.getFieldValues().get("__components");
        testingState = testingState == null ? null : testingState.get(
                com.brad.pms.workflow.WorkflowComponentKey.STORY_TESTING);
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("stories", stories);
        attachTestingContent(content, testingState);
        java.util.Map<String, Integer> versions = new java.util.HashMap<>();
        versions.put("node", node.getVersion());
        versions.put("stories", null);
        String contentState = stories.isEmpty() && testingState == null ? "EMPTY" : "LOADED";
        return new AiWorkflowContextDTO.Component("story-list", "story-list", fields, List.of(),
                null, mapper.valueToTree(content), contentState, versions, null);
    }

    private void attachTestingContent(Map<String, Object> content,
                                      com.fasterxml.jackson.databind.JsonNode testingState) {
        if (testingState == null || testingState.isNull()) {
            content.put("testingResults", null);
        } else {
            content.put("testingResults", mapper.valueToTree(testingState));
        }
    }

    private boolean canReadReference(ReadAuthorizationSnapshot authorizationSnapshot,
                                     String resourceType, Long resourceId) {
        return authorizationSnapshot == null
                ? targetReferencePolicy.canReadReference(resourceType, resourceId)
                : authorizationSnapshot.canRead(resourceType, resourceId);
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
