package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProjectActionItemDTO {

    private String type;
    private String severity;
    private Long projectId;
    private String projectName;
    private Long nodeId;
    private String nodeName;
    private Integer nodeSort;
    private Long taskId;
    private String taskName;
    private String title;
    private String description;
    private LocalDate dueDate;
    private int overdueDays;
    private String actionLabel;
    private String actionTarget;
    private boolean canAct;
}
