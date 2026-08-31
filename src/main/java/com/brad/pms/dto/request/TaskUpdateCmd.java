package com.brad.pms.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TaskUpdateCmd {

    private String title;

    private String description;

    private String deliverable;

    private Integer status;

    private Integer priority;

    private Long assigneeId;

    private Long milestoneId;

    private Integer sort;

    private LocalDate dueDate;
}
