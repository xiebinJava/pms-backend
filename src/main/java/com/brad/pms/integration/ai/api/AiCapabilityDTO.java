package com.brad.pms.integration.ai.api;

import java.util.List;
import java.util.Map;

/** Dynamic, server-owned capability catalog for MCP and OpenCLI clients. */
public record AiCapabilityDTO(
        String version,
        List<ResourceCapability> resources,
        List<String> scopes,
        List<WorkflowTypeCapability> workflowTypes,
        Viewer viewer,
        String today) {

    public AiCapabilityDTO {
        resources = resources == null ? List.of() : List.copyOf(resources);
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        workflowTypes = workflowTypes == null ? List.of() : List.copyOf(workflowTypes);
    }

    public record ResourceCapability(String type, List<ActionCapability> actions) {
        public ResourceCapability {
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }

    public record ActionCapability(
            String name,
            String label,
            String description,
            String mode,
            String risk,
            List<String> scopes,
            Map<String, Object> inputSchema,
            boolean requiresContext,
            List<String> refreshScopes) {
        public ActionCapability {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
            inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
            refreshScopes = refreshScopes == null ? List.of() : List.copyOf(refreshScopes);
        }
    }

    public record WorkflowTypeCapability(
            String processType,
            String label,
            List<WorkflowTemplateCapability> templates) {
        public WorkflowTypeCapability {
            templates = templates == null ? List.of() : List.copyOf(templates);
        }
    }

    public record WorkflowTemplateCapability(
            Long templateVersionId,
            Integer versionNo,
            String name,
            boolean defaultTemplate,
            List<WorkflowNodeCapability> nodes) {
        public WorkflowTemplateCapability {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record WorkflowNodeCapability(
            String key,
            String label,
            int position,
            List<WorkflowComponentCapability> components) {
        public WorkflowNodeCapability {
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    public record WorkflowComponentCapability(
            String key,
            String label,
            List<FieldCapability> fields) {
        public WorkflowComponentCapability {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    public record FieldCapability(
            String key,
            String label,
            String type,
            boolean required,
            List<String> options,
            Boolean visible,
            String binding,
            Boolean fullWidth) {
        public FieldCapability {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Viewer(Long id, String displayName, String username, String email) {
    }
}
