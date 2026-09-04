package com.brad.pms.dto.response;

import lombok.Data;

@Data
public class NodeRequirementBaselineSummaryDTO {
    private boolean available;
    private boolean confirmed;
    private Integer version;
    private int inScopeCount;
    private int requirementCount;
}
