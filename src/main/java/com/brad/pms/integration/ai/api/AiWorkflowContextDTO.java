package com.brad.pms.integration.ai.api;

import com.brad.pms.dto.response.NodePermissionsDTO;
import com.brad.pms.dto.response.NodeTaskSummaryDTO;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** One development item's pinned workflow snapshot and editable runtime state. */
public record AiWorkflowContextDTO(
        String resourceType,
        Long resourceId,
        Integer version,
        CurrentNode currentNode,
        Workflow workflow,
        List<String> allowedActions,
        Integer entityVersion,
        Map<String, Object> resourceDetails,
        List<Relation> relations,
        ContextMeta meta) {

    public AiWorkflowContextDTO {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        relations = relations == null ? List.of() : List.copyOf(relations);
    }

    /** Legacy constructor retained for older service/CLI callers; new projection fields stay null. */
    public AiWorkflowContextDTO(
            String resourceType,
            Long resourceId,
            Integer version,
            CurrentNode currentNode,
            Workflow workflow,
            List<String> allowedActions) {
        this(resourceType, resourceId, version, currentNode, workflow, allowedActions, null, null, null, null);
    }

    public record CurrentNode(Long id, String key, String label) {
    }

    public record Workflow(
            Long templateVersionId,
            Integer templateVersionNo,
            List<Node> nodes) {
        public Workflow {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record Node(
            Long id,
            String key,
            String label,
            int position,
            Integer status,
            List<Component> components,
            List<Field> fields,
            Map<String, Object> fieldValues,
            Integer version,
            String description,
            String deliverable,
            Long ownerId,
            String ownerName,
            LocalDate startDate,
            LocalDate endDate,
            Map<String, Integer> fieldValueVersions,
            String fieldValuesState,
            NodePermissionsDTO permissions,
            NodeTaskSummaryDTO taskSummary) {
        public Node {
            components = components == null ? List.of() : List.copyOf(components);
            fields = fields == null ? List.of() : List.copyOf(fields);
            fieldValues = fieldValues == null ? Map.of() : new LinkedHashMap<>(fieldValues);
        }

        /** Legacy constructor retained for older context readers; new node facts stay null. */
        public Node(
                Long id, String key, String label, int position, Integer status,
                List<Component> components, List<Field> fields, Map<String, Object> fieldValues) {
            this(id, key, label, position, status, components, fields, fieldValues,
                    null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    public record Component(String key, String label, List<Field> fields, List<String> actions,
                            JsonNode config, JsonNode content, String contentState,
                            Map<String, Integer> versions,
                            Map<String, Object> saveContract) {
        public Component {
            fields = fields == null ? List.of() : List.copyOf(fields);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        /** Legacy constructor retained for older context readers; new fields stay null. */
        public Component(String key, String label, List<Field> fields, List<String> actions) {
            this(key, label, fields, actions, null, null, null, null, null);
        }
    }

    public record Field(
            String key,
            String label,
            String type,
            boolean required,
            List<String> options,
            Boolean visible,
            String binding,
            Boolean fullWidth) {
        public Field {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    /** One readable system-generated reference after permission projection; readable is always true. */
    public record Relation(String resourceType, Long resourceId, String label, boolean readable) {
    }

    public record Warning(String code, String message) {
    }

    public record ContextMeta(
            int formatVersion,
            boolean pureRead,
            Instant loadedAt,
            String businessTimezone,
            String view,
            String fieldValuesScope,
            String componentContentScope,
            Long selectedNodeId,
            String selectedComponentKey,
            boolean workflowConfigured,
            String workflowStatus,
            String terminalStatus,
            Integer workflowInstanceVersion,
            String permissionsScope,
            String referencesScope,
            String contextVersion,
            List<Warning> warnings) {
        public ContextMeta {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        /** Source-compatible constructor for older context readers and fixtures. */
        public ContextMeta(
                int formatVersion,
                boolean pureRead,
                Instant loadedAt,
                String businessTimezone,
                String view,
                String fieldValuesScope,
                String componentContentScope,
                Long selectedNodeId,
                String selectedComponentKey,
                boolean workflowConfigured,
                String workflowStatus,
                String terminalStatus,
                Integer workflowInstanceVersion,
                String permissionsScope,
                String referencesScope,
                List<Warning> warnings) {
            this(formatVersion, pureRead, loadedAt, businessTimezone, view, fieldValuesScope,
                    componentContentScope, selectedNodeId, selectedComponentKey, workflowConfigured,
                    workflowStatus, terminalStatus, workflowInstanceVersion, permissionsScope,
                    referencesScope, null, warnings);
        }
    }
}
