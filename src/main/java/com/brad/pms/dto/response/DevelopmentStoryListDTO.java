package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DevelopmentStoryListDTO {

    private Long id;
    private String title;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long nodeId;
    private String nodeKey;
    private String nodeName;
    private Long topicId;
    private String topicTitle;
    private Long topicWorkflowNodeId;
    private String topicWorkflowNodeName;
    private Long ownerId;
    private String ownerName;
    private String iterationPlanName;
    private String status;
    private Integer progress;
    private Integer developmentProgress;
    private Boolean workflowConfigured;
    private String workflowStatus;
    private Integer storyPoints;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String blocker;
    /** All direct requirements that execute through this story. */
    private List<SourceRequirementSummaryDTO> sourceRequirements;
    /** @deprecated Use sourceRequirements; retained for older clients. */
    private SourceRequirementSummaryDTO sourceRequirement;
}
