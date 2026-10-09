package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class RequirementListDTO {
    private Long id;
    private String title;
    private String description;
    private Integer priority;
    private Long ownerId;
    private String ownerName;
    private Long orgUnitId;
    private Long systemId;
    private String status;
    private Boolean deleted;
    private Integer version;
    private Boolean workflowConfigured;
    private String workflowStatus;
    private Integer workflowProgress;
    private String terminalStatus;
    private RequirementExecutionTargetDTO executionTarget;
}
