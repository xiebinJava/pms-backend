package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class IterationPlanTaskDTO {

    private Long id;
    private Integer version;
    private Long projectId;
    private Long nodeId;
    private String nodeName;
    private Long parentId;
    private String title;
    private String description;
    private String deliverable;
    private Integer status;
    private Integer priority;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate dueDate;
    private Integer sort;
}
