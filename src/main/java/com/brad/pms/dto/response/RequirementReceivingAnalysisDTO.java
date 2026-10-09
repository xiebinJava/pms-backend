package com.brad.pms.dto.response;

import com.brad.pms.workflow.RequirementReceivingAnalysisConfig;
import com.brad.pms.workflow.RequirementReceivingAnalysisState;
import lombok.Data;

@Data
public class RequirementReceivingAnalysisDTO {
    private Long requirementId;
    private Long nodeId;
    private Integer nodeVersion;
    private Integer nodeStatus;
    private String terminalStatus;
    private RequirementReceivingAnalysisState state;
    private RequirementReceivingAnalysisConfig config;
    private Double averageScore;
    private RequirementReceivingAnalysisState.ValueConclusion valueConclusion;
}
