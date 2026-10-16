package com.brad.pms.ai.orchestrator.contract;

import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.workflow.StoryNodeWorkbenchPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AiAgentDraftValidator {

    public static final String CONTRACT_VERSION = "requirement-analysis/v1";
    public static final String STORY_CONTRACT_VERSION = "story-workbench/v1";

    private static final Set<String> PROTECTED_FIELDS = Set.of(
            "systemId", "targetType", "targetId", "ownerId", "status", "startDate", "endDate");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public DraftValidationResult validate(AgentDraft agentDraft, AiWorkflowContextDTO context) {
        List<DraftValidationResult.ValidationError> errors = new ArrayList<>();
        if (agentDraft == null || !CONTRACT_VERSION.equals(agentDraft.contractVersion())) {
            errors.add(error("CONTRACT_VERSION_UNSUPPORTED", "contractVersion",
                    "只支持 " + CONTRACT_VERSION));
        }
        if (agentDraft == null || agentDraft.draft() == null) {
            errors.add(error("MISSING_FIELD", "draft", "draft 不能为空"));
            return new DraftValidationResult(errors);
        }
        if (context == null) {
            errors.add(error("MISSING_FIELD", "context", "上下文不能为空"));
            return new DraftValidationResult(errors);
        }

        AgentDraft.DraftPayload draft = agentDraft.draft();
        validateItems(draft.facts(), "facts", errors);
        validateItems(draft.inferences(), "inferences", errors);
        validateItems(draft.assumptions(), "assumptions", errors);
        validateItems(draft.unresolvedDecisions(), "unresolvedDecisions", errors);

        if (draft.patch() == null || draft.patch().isEmpty()) {
            errors.add(error("EMPTY_PATCH", "draft.patch", "patch 不能为空"));
        } else {
            for (String field : draft.patch().keySet()) {
                if (PROTECTED_FIELDS.contains(field)) {
                    errors.add(error("PROTECTED_FIELD", "draft.patch." + field,
                            "分析草稿不能覆盖受保护字段"));
                }
            }
        }

        Set<String> readableReferences = readableReferences(context);
        validateReferences(draft.facts(), "facts", readableReferences, errors);
        validateReferences(draft.inferences(), "inferences", readableReferences, errors);
        validateReferences(draft.assumptions(), "assumptions", readableReferences, errors);
        validateReferences(draft.unresolvedDecisions(), "unresolvedDecisions", readableReferences, errors);
        return new DraftValidationResult(errors);
    }

    /** Validates the story-node draft against the server-projected node contract. */
    public DraftValidationResult validateStory(AgentDraft agentDraft,
                                               AiWorkflowContextDTO context,
                                               StoryNodeContract contract) {
        List<DraftValidationResult.ValidationError> errors = new ArrayList<>();
        if (agentDraft == null || !STORY_CONTRACT_VERSION.equals(agentDraft.contractVersion())) {
            errors.add(error("CONTRACT_VERSION_UNSUPPORTED", "contractVersion",
                    "只支持 " + STORY_CONTRACT_VERSION));
        }
        if (agentDraft == null || agentDraft.draft() == null) {
            errors.add(error("MISSING_FIELD", "draft", "draft 不能为空"));
            return new DraftValidationResult(errors);
        }
        if (context == null || contract == null) {
            errors.add(error("MISSING_FIELD", "context", "故事节点上下文和契约不能为空"));
            return new DraftValidationResult(errors);
        }

        AgentDraft.DraftPayload draft = agentDraft.draft();
        validateItems(draft.facts(), "facts", errors);
        validateItems(draft.inferences(), "inferences", errors);
        validateItems(draft.assumptions(), "assumptions", errors);
        validateItems(draft.unresolvedDecisions(), "unresolvedDecisions", errors);
        if (draft.patch() == null || draft.patch().isEmpty()) {
            errors.add(error("EMPTY_PATCH", "draft.patch", "patch 不能为空"));
        } else {
            Set<String> writable = Set.copyOf(contract.writableFields());
            Set<String> protectedFields = Set.copyOf(contract.protectedFields());
            for (String field : draft.patch().keySet()) {
                if (Set.of("nodeId", "contextVersion", "resourceId", "resourceType", "version",
                        "__components").contains(field)) {
                    errors.add(error("NODE_FIELD_IN_DRAFT", "draft.patch." + field,
                            "节点定位字段必须放在请求外层"));
                } else if (protectedFields.contains(field)) {
                    errors.add(error("PROTECTED_FIELD", "draft.patch." + field,
                            "故事节点草稿不能覆盖受保护字段"));
                } else if (!writable.contains(field)) {
                    errors.add(error("UNKNOWN_FIELD", "draft.patch." + field,
                            "字段不在当前节点可写契约中"));
                }
            }
            if (StoryNodeWorkbenchPolicy.supports(contract.variant())) {
                try {
                    StoryNodeWorkbenchPolicy.validate(
                            OBJECT_MAPPER.valueToTree(draft.patch()), contract.variant());
                } catch (IllegalArgumentException exception) {
                    errors.add(error("FIELD_VALUE_INVALID", "draft.patch", exception.getMessage()));
                }
            }
        }

        Set<String> readableReferences = readableReferences(context);
        readableReferences.add("story-node:" + contract.nodeId());
        readableReferences.add("story-node:" + contract.nodeId() + "#component:story-node-workbench");
        validateReferences(draft.facts(), "facts", readableReferences, errors);
        validateReferences(draft.inferences(), "inferences", readableReferences, errors);
        validateReferences(draft.assumptions(), "assumptions", readableReferences, errors);
        validateReferences(draft.unresolvedDecisions(), "unresolvedDecisions", readableReferences, errors);
        return new DraftValidationResult(errors);
    }

    private void validateItems(List<AgentDraftItem> items, String field,
                               List<DraftValidationResult.ValidationError> errors) {
        if (items == null) {
            errors.add(error("MISSING_FIELD", "draft." + field, field + " 不能为空"));
            return;
        }
        for (int index = 0; index < items.size(); index++) {
            AgentDraftItem item = items.get(index);
            if (item == null || item.text() == null || item.text().isBlank()) {
                errors.add(error("MISSING_FIELD", "draft." + field + "[" + index + "].text",
                        "分析条目文本不能为空"));
            }
            if (item == null || item.sourceRefs() == null) {
                errors.add(error("MISSING_FIELD", "draft." + field + "[" + index + "].sourceRefs",
                        "来源引用不能为空"));
            }
        }
    }

    private void validateReferences(List<AgentDraftItem> items, String field, Set<String> readableReferences,
                                    List<DraftValidationResult.ValidationError> errors) {
        if (items == null) return;
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            AgentDraftItem item = items.get(itemIndex);
            if (item == null || item.sourceRefs() == null) continue;
            for (int refIndex = 0; refIndex < item.sourceRefs().size(); refIndex++) {
                String sourceRef = item.sourceRefs().get(refIndex);
                if (sourceRef == null || !readableReferences.contains(sourceRef.trim())) {
                    errors.add(error("UNKNOWN_SOURCE_REF",
                            "draft." + field + "[" + itemIndex + "].sourceRefs[" + refIndex + "]",
                            "来源不存在或不可读: " + sourceRef));
                }
            }
        }
    }

    private Set<String> readableReferences(AiWorkflowContextDTO context) {
        Set<String> references = new LinkedHashSet<>();
        if (context.resourceType() != null && context.resourceId() != null) {
            references.add(context.resourceType() + ":" + context.resourceId());
        }
        for (AiWorkflowContextDTO.Relation relation : context.relations()) {
            if (relation != null && relation.readable()
                    && relation.resourceType() != null && relation.resourceId() != null) {
                references.add(relation.resourceType() + ":" + relation.resourceId());
            }
        }
        return references;
    }

    private DraftValidationResult.ValidationError error(String code, String path, String message) {
        return new DraftValidationResult.ValidationError(code, path, message);
    }
}
