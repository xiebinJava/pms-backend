package com.brad.pms.dto.request;

import com.brad.pms.common.page.BasePage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectPageQry extends BasePage {

    private String keyword;

    private Integer status;

    /** MINE：我负责的；PORTFOLIO / ALL：当前可见范围内的全部项目。 */
    private String view;

    private Long orgUnitId;

    private Long projectManagerId;

    private Integer projectLevel;

    /** OVERDUE / NO_MANAGER / ACTIVE / STALE_NODE */
    private String attention;

    private String currentNodeKey;
}
