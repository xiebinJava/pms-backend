package com.brad.pms.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DevelopmentItemTaskSaveCmd {
    private Long parentId;
    private String title;
    private String description;
    /** 0 todo, 1 in progress, 2 done. */
    private Integer status;
    /** 0 low, 1 normal, 2 high. */
    private Integer priority;
    private Long assigneeId;
    private LocalDate dueDate;
    private Integer sort;
    private Integer version;
}
