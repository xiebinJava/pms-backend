package com.brad.pms.ai.node;

import com.brad.pms.dto.request.WorkflowNodeFieldValuesCmd;
import com.brad.pms.dto.response.WorkflowNodeFieldValuesDTO;
import com.brad.pms.service.NodeCustomFieldService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter for workflow custom fields. Field keys are configured per node, so the
 * key doubles as its label and the per-field version map keeps concurrent edits
 * safe the same way the PMS page does.
 */
public final class CustomFieldNodeWorkbench implements NodeWorkbench {

    public static final String KEY = "custom-fields";

    private final NodeCustomFieldService service;
    private final ObjectMapper mapper;

    public CustomFieldNodeWorkbench(NodeCustomFieldService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String label() {
        return "节点自定义字段";
    }

    @Override
    public boolean dynamicFields() {
        return true;
    }

    @Override
    public WorkbenchSnapshot read(Long projectId, Long nodeId) {
        WorkflowNodeFieldValuesDTO document = service.get(projectId, nodeId);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, JsonNode> stored = document == null || document.getValues() == null
                ? Map.of() : document.getValues();
        stored.forEach((key, value) -> {
            values.put(key, mapper.convertValue(value, Object.class));
            labels.put(key, key);
        });
        Map<String, Integer> versions = document == null || document.getVersions() == null
                ? Map.of() : document.getVersions();
        return new WorkbenchSnapshot(values, labels, null, versions);
    }

    @Override
    public Object write(Long projectId, Long nodeId, Map<String, Object> patch) {
        WorkflowNodeFieldValuesDTO current = service.get(projectId, nodeId);
        Map<String, Integer> versions = current == null || current.getVersions() == null
                ? Map.of() : current.getVersions();
        Map<String, JsonNode> values = new LinkedHashMap<>();
        Map<String, Integer> expected = new LinkedHashMap<>();
        patch.forEach((key, value) -> {
            values.put(key, mapper.valueToTree(value));
            // A field that does not exist yet is created without a version check,
            // exactly like the PMS page: only existing values carry a version.
            if (versions.containsKey(key)) expected.put(key, versions.get(key));
        });
        WorkflowNodeFieldValuesCmd command = new WorkflowNodeFieldValuesCmd();
        command.setValues(values);
        command.setVersions(expected);
        return service.save(projectId, nodeId, command);
    }
}
