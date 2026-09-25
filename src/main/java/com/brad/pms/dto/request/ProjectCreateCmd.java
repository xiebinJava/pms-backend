package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class ProjectCreateCmd {

    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "状态不能为空")
    private Integer status = 1;

    private Integer priority = 1;

    private Integer projectLevel = 0;

    private Long projectTypeId;

    private Long workflowTemplateVersionId;

    private Long ownerId;

    private LocalDate startDate;

    private LocalDate endDate;

    private Long orgUnitId;
}
