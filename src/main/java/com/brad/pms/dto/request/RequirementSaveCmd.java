package com.brad.pms.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RequirementSaveCmd {
    @NotBlank(message = "需求名称不能为空")
    @Size(max = 200, message = "需求名称不能超过 200 个字符")
    private String title;

    @Size(max = 5000, message = "需求描述不能超过 5000 个字符")
    private String description;

    @Min(value = 0, message = "需求优先级不合法")
    @Max(value = 3, message = "需求优先级不合法")
    private Integer priority;

    private Long ownerId;
    private Long templateVersionId;
    private Integer version;
}
