package com.brad.pms.integration.ai.api;

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
        List<String> allowedActions) {

    public AiWorkflowContextDTO {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
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
            Map<String, Object> fieldValues) {
        public Node {
            components = components == null ? List.of() : List.copyOf(components);
            fields = fields == null ? List.of() : List.copyOf(fields);
            fieldValues = fieldValues == null ? Map.of() : new LinkedHashMap<>(fieldValues);
        }
    }

    public record Component(String key, String label, List<Field> fields, List<String> actions) {
        public Component {
            fields = fields == null ? List.of() : List.copyOf(fields);
            actions = actions == null ? List.of() : List.copyOf(actions);
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
}
