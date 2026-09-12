package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class ProjectDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer status;

    private Integer priority;

    private Integer projectLevel;

    private Long projectTypeId;

    private Long workflowTemplateVersionId;

    private Long ownerId;

    private Long projectManagerId;

    /** Primary organization responsible for the project. */
    @TableField("org_unit_id")
    private Long orgUnitId;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer progress;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
