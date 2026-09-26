package com.brad.pms.dto.response;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RequirementExecutionTargetHistoryDTO {
    private Long id;
    private Long requirementId;
    private String action;
    private RequirementExecutionTargetType targetType;
    private Long targetId;
    private RequirementExecutionTargetType previousTargetType;
    private Long previousTargetId;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
