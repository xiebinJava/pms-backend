package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class NodeDevelopmentStoryDTO {

    private Long id;
    private String title;
    private Long ownerId;
    private String ownerName;
    private Long iterationPlanId;
    private String iterationPlanName;
    private String status;
    private Integer progress;
    private Integer storyPoints;
    private LocalDate startDate;
    private LocalDate dueDate;
    private String blocker;
    private Integer sort;
}
