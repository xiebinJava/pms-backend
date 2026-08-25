package com.brad.pms.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Data
public class MilestoneCreateCmd {

    @NotBlank(message = "里程碑名称不能为空")
    private String title;

    private String description;

    private LocalDate dueDate;

    private Integer status = 0;
}
