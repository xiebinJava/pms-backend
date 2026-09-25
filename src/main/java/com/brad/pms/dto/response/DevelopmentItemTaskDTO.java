package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class DevelopmentItemTaskDTO {
    private Long id;
    private Long nodeId;
    private Long parentId;
    private String title;
    private String description;
    private Integer status;
    private Integer priority;
    private Long assigneeId;
    private String assigneeName;
    private LocalDate dueDate;
    private Integer sort;
    private Integer version;
    private List<DevelopmentItemTaskDTO> children = new ArrayList<>();
}
