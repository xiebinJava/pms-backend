package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class TaskUpdateCmd {

    @NotNull(message = "任务版本不能为空，请刷新后重试")
    private Integer version;

    private String title;

    private String description;

    private String deliverable;

    private Integer status;

    private Integer priority;

    private Long assigneeId;

    private Long requirementId;

    private Boolean clearRequirement = false;

    private Integer sort;

    private LocalDate dueDate;

    private Boolean clearDueDate = false;
}
