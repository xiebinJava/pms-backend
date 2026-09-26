package com.brad.pms.dto.request;

import com.brad.pms.common.page.BasePage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IterationPlanPageQry extends BasePage {

    private String keyword;

    private Long projectId;

    private String status;
}
