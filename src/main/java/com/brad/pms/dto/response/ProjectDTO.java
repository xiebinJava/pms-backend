package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProjectDTO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer priority;
    private Long ownerId;
    private String ownerName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer progress;
    private Integer taskCount;
    private Integer doneTaskCount;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
