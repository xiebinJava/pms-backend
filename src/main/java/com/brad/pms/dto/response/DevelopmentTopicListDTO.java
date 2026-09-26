package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class DevelopmentTopicListDTO {

    private Long id;
    private String title;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long nodeId;
    private String nodeKey;
    private String nodeName;
    private Long ownerId;
    private String ownerName;
    private String status;
    private Integer progress;
    private Integer developmentProgress;
    private Boolean workflowConfigured;
    private String workflowStatus;
    private Integer storyCount;
    private Integer completedStoryCount;
    private Integer blockedStoryCount;
    private String latestBuildVersion;
    private String testStatus;
    private String blocker;
    private SourceRequirementSummaryDTO sourceRequirement;
}
