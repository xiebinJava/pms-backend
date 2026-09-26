package com.brad.pms.dto.response;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import lombok.Data;

@Data
public class SourceRequirementSummaryDTO {
    private Long id;
    private String title;
    private String status;
    private Long ownerId;
    private String ownerName;
    private RequirementExecutionTargetType targetType;
    private Long targetId;
}
