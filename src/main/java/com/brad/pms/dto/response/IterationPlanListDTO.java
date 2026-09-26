package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class IterationPlanListDTO {

    private Long id;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long nodeId;
    private String nodeName;
    private String name;
    private Long ownerId;
    private String ownerName;
    private String goal;
    private String status;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer sort;
    private Integer storyCount;
    private Integer completedStoryCount;
    private Integer taskCount;
    private Integer completedTaskCount;
    private Integer progress;
}
