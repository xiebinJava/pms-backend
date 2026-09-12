package com.brad.pms.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

@Data
public class WorkflowNodeFieldValuesCmd {
    private Map<String, JsonNode> values;
    private Map<String, Integer> versions;
}
