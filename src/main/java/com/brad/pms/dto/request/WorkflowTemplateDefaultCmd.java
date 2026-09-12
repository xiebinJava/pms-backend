package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowTemplateDefaultCmd {
    @NotNull(message = "默认流程版本不能为空")
    private Long templateVersionId;
}
