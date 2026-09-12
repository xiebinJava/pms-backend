package com.brad.pms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProjectTypeSaveCmd {
    @NotBlank(message = "项目类型标识不能为空")
    @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_-]{0,63}", message = "项目类型标识格式无效")
    private String code;
    @NotBlank(message = "项目类型名称不能为空")
    @Size(max = 120, message = "项目类型名称不能超过120个字符")
    private String name;
    @Size(max = 500, message = "项目类型说明不能超过500个字符")
    private String description;
    private Integer sort = 0;
}
