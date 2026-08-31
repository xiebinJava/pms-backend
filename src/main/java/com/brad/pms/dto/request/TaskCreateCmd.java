package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Data
public class TaskCreateCmd {

    private Long projectId;

    private Long nodeId;

    private Long parentId;

    @NotBlank(message = "任务标题不能为空")
    private String title;

    private String description;

    private String deliverable;

    private Integer status = 0;

    private Integer priority = 1;

    private Long assigneeId;

    private Long milestoneId;

    private Integer sort = 0;

    private LocalDate dueDate;
}
