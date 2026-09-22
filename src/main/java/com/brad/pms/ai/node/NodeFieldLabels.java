package com.brad.pms.ai.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chinese labels for workbench fields, loaded from
 * {@code agent-labels/pms/node-field-labels.yaml}. Labels are data so the
 * deployment can rename a field for users without touching Java.
 */
@Component
public class NodeFieldLabels {

    private static final String RESOURCE = "agent-labels/pms/node-field-labels.yaml";

    private final Map<String, String> workbenchLabels;
    private final Map<String, Map<String, String>> fieldLabels;

    public NodeFieldLabels() {
        this(read(LabelsFile.class));
    }

    NodeFieldLabels(LabelsFile file) {
        Map<String, String> workbenches = new LinkedHashMap<>();
        Map<String, Map<String, String>> fields = new LinkedHashMap<>();
        if (file != null && file.workbenches() != null) {
            file.workbenches().forEach((key, value) -> {
                if (key == null || value == null) return;
                workbenches.put(key, value.label() == null ? key : value.label());
                fields.put(key, value.fields() == null ? Map.of() : Map.copyOf(value.fields()));
            });
        }
        this.workbenchLabels = Map.copyOf(workbenches);
        this.fieldLabels = Map.copyOf(fields);
    }

    public String workbench(String key) {
        return workbenchLabels.getOrDefault(key, key);
    }

    /** Falls back to the raw field key so a missing label never hides a change. */
    public String field(String workbench, String field) {
        Map<String, String> labels = fieldLabels.get(workbench);
        if (labels == null) return field;
        return labels.getOrDefault(field, field);
    }

    public boolean knows(String workbench, String field) {
        Map<String, String> labels = fieldLabels.get(workbench);
        return labels != null && labels.containsKey(field);
    }

    private static LabelsFile read(Class<?> type) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            return new ObjectMapper(new YAMLFactory()).readValue(input, LabelsFile.class);
        } catch (IOException ex) {
            // Labels are presentation metadata: a missing file degrades the
            // preview wording, it must not disable the write path.
            return new LabelsFile(Map.of());
        }
    }

    record LabelsFile(Map<String, WorkbenchLabels> workbenches) {
    }

    record WorkbenchLabels(String label, Map<String, String> fields) {
    }
}
