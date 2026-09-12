package com.brad.pms.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkflowNodeFieldValuesDTO {
    private Map<String, JsonNode> values;
    private Map<String, Integer> versions;
    private Map<String, List<WorkflowFieldAttachmentDTO>> attachments;
}
