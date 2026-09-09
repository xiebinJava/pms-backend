package com.brad.pms.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Data
public class ProjectUpdateCmd {

    @NotNull(message = "项目版本不能为空，请刷新后重试")
    private Integer version;

    @NotBlank(message = "项目名称不能为空")
    private String name;

    private String description;

    private Integer status;

    private Integer priority;

    private Integer projectLevel;

    private Long ownerId;

    private Long projectManagerId;

    private LocalDate startDate;

    private LocalDate endDate;

    private List<Long> memberIds;

    private List<Long> followerIds;

    private Long orgUnitId;
}
