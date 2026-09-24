package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DevelopmentTopicUpdateCmd {
    @NotBlank(message = "专题名称不能为空")
    @Size(max = 200, message = "专题名称不能超过 200 个字符")
    private String title;
    private Long ownerId;
    private Long projectId;
}
