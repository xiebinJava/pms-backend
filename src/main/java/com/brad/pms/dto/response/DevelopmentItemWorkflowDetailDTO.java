package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class DevelopmentItemWorkflowDetailDTO {
    private String itemType;
    private Long id;
    private String title;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long sourceNodeId;
    private String sourceNodeName;
    private Long topicId;
    private String topicTitle;
    private Long ownerId;
    private String ownerName;
    private String developmentStatus;
    private Integer developmentProgress;
    private Integer storyPoints;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String blocker;
    private String latestBuildVersion;
    private String testStatus;
    private String iterationPlanName;

    private Boolean workflowConfigured;
    private String workflowStatus;
    private Integer workflowProgress;
    private Long workflowId;
    private Long templateVersionId;
    private Integer templateVersionNo;
    private Integer completedNodeCount;
    private Integer totalNodeCount;
    private List<DevelopmentItemWorkflowNodeDTO> nodes;
}
