package com.brad.pms.dto.request;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.common.page.BasePage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementPageQry extends BasePage {
    private String keyword;
    private String status;
    private RequirementExecutionTargetType targetType;
    private Long ownerId;
    private Boolean deleted;
}
