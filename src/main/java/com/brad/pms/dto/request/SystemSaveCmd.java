package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SystemSaveCmd {
    @NotBlank(message = "系统名称不能为空")
    @Size(max = 200, message = "系统名称不能超过 200 个字符")
    private String name;

    @Size(max = 5000, message = "系统描述不能超过 5000 个字符")
    private String description;

    private Long ownerId;
    private Integer version;
}
