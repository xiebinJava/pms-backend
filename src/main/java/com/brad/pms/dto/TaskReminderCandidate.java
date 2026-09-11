package com.brad.pms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskReminderCandidate {

    private Long taskId;
    private Long projectId;
    private Long assigneeId;
    private String taskTitle;
    private String projectName;
    private LocalDate dueDate;
}
