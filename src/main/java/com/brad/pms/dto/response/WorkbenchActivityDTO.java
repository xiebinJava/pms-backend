package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkbenchActivityDTO {

    private Long id;
    private Long projectId;
    private String projectName;
    private String actorName;
    private String content;
    private LocalDateTime createdAt;
}
