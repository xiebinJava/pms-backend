package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackUpdateCmd {

    private String status;
    private String priority;
    private Long assigneeId;

    @Size(max = 2000, message = "处理说明不能超过2000个字符")
    private String resolutionNote;

    @NotNull(message = "版本号不能为空")
    private Integer version;
}
