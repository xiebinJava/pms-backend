package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequirementReceivingAnalysisActionCmd {
    @NotBlank(message = "操作原因不能为空")
    @Size(max = 500, message = "操作原因不能超过500个字符")
    private String reason;
}
