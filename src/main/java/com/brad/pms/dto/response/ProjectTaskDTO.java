package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectTaskDTO {

    private Long id;
    private Long projectId;
    private Long nodeId;
    private Long parentId;
    private String title;
    private String description;
    private String deliverable;
    private Integer status;
    private Integer priority;
    private Long assigneeId;
    private String assigneeName;
    private Long milestoneId;
    private Integer sort;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer subtaskCount;
    private TaskPermissionsDTO permissions;
}
