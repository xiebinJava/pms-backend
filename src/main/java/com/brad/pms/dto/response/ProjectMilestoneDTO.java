package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectMilestoneDTO {

    private Long id;
    private Long projectId;
    private String title;
    private String description;
    private LocalDate dueDate;
    private Integer status;
    private Integer taskCount;
    private Integer doneTaskCount;
    private LocalDateTime createdAt;
}
