package com.brad.pms.dto.request;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequirementExecutionTargetCmd {
    @NotNull(message = "执行对象类型不能为空")
    private RequirementExecutionTargetType targetType;

    @NotNull(message = "执行对象不能为空")
    private Long targetId;

    @NotNull(message = "需求版本不能为空")
    private Integer requirementVersion;

    @Size(max = 500, message = "变更原因不能超过 500 个字符")
    private String reason;
}
