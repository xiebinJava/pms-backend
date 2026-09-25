package com.brad.pms.dto.response;

import com.brad.pms.common.enums.TaskScheduleState;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectTaskDTO {

    private Long id;
    private Integer version;
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
    private Long requirementId;
    private String requirementCode;
    private Integer sort;
    private LocalDate dueDate;
    private TaskScheduleState scheduleState;
    private int overdueDays;
    private boolean rescheduled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer subtaskCount;
    private TaskPermissionsDTO permissions;
}
