package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class ProjectAttentionSummaryDTO {

    private int criticalCount;
    private int warningCount;
    private int overdueTaskCount;
    private int currentNodeIssueCount;
}
