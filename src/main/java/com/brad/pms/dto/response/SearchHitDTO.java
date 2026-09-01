package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class SearchHitDTO {

    private Long id;
    private Long projectId;
    private String projectName;
    private String title;
    private String snippet;
    private Long taskId;
    private Long milestoneId;
}
