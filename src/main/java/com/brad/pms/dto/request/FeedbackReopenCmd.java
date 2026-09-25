package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackReopenCmd {

    @NotNull(message = "版本号不能为空")
    private Integer version;

    @Size(max = 2000, message = "重开说明不能超过2000个字符")
    private String note;
}
