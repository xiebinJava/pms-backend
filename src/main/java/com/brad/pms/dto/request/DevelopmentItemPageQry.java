package com.brad.pms.dto.request;

import com.brad.pms.common.page.BasePage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DevelopmentItemPageQry extends BasePage {

    private String keyword;

    private Long projectId;

    private Long nodeId;

    private Long ownerId;

    private String status;
}
