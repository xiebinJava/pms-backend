package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class ProjectCreateCmd {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "状态不能为空")
    private Integer status = 1;

    private Integer priority = 1;

    private Long ownerId;

    private LocalDate startDate;

    private LocalDate endDate;
}
