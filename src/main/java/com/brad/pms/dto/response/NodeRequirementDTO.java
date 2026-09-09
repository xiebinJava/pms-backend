package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeRequirementDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String type;
    private Integer priority;
    private String acceptanceCriteria;
    private Integer status;
    private Integer sort;
    private Integer taskCount;
    private Integer completedTaskCount;
}
