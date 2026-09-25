package com.brad.pms.dto.request;

import com.brad.pms.common.page.BasePage;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FeedbackPageQry extends BasePage {
    private String keyword;
    private String feedbackType;
    private String priority;
    private String status;
}
