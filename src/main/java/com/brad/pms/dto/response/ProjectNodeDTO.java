package com.brad.pms.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectNodeDTO {

    private Long id;
    private Long projectId;
    private String nodeKey;
    private String name;
    private String description;
    private String deliverable;
    private String roles;
    private Integer status;
    private Integer sort;
    private LocalDateTime createdAt;
}
