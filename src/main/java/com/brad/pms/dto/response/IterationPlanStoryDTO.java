package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class IterationPlanStoryDTO {

    private Long id;
    private Long projectId;
    private Long nodeId;
    private Long topicId;
    private String topicTitle;
    private String title;
    private Long ownerId;
    private String ownerName;
    private String status;
    private Integer progress;
    private Integer storyPoints;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String blocker;
}
