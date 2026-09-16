package com.brad.pms.dto.response;

import com.brad.pms.common.enums.TaskScheduleChangeType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TaskScheduleHistoryDTO {

    private Long id;
    private Long taskId;
    private LocalDate previousDueDate;
    private LocalDate nextDueDate;
    private TaskScheduleChangeType changeType;
    private String operatorName;
    private LocalDateTime createdAt;
}
