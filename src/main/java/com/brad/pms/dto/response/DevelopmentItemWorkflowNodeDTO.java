package com.brad.pms.dto.response;

import com.brad.pms.workflow.WorkflowFieldDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class DevelopmentItemWorkflowNodeDTO {
    private Long id;
    private String nodeKey;
    private String name;
    private String description;
    private String deliverable;
    private Integer sort;
    /** 0 locked, 1 active, 2 completed. */
    private Integer status;
    private Long ownerId;
    private String ownerName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer version;
    private List<WorkflowFieldDefinition> fields;
    private Map<String, JsonNode> fieldValues;
    /** Runtime components after applying the item's pinned workflow bindings. */
    private List<String> runtimeComponents;
    private List<DevelopmentItemTaskDTO> tasks;
}
