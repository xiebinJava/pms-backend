package com.brad.pms.dto.response;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import lombok.Data;

@Data
public class RequirementExecutionTargetOptionDTO {
    private RequirementExecutionTargetType targetType;
    private Long targetId;
    private String title;
    private String code;
    private String status;
    private Long ownerId;
    private String ownerName;
    private Integer progress;
    private String navigationType;
    private Long navigationId;
}
