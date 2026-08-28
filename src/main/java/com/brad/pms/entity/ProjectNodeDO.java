package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project_node")
public class ProjectNodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private String nodeKey;

    private String name;

    private String description;

    private String deliverable;

    private String roles;

    private Long ownerId;

    private Integer status;

    private Integer sort;

    private LocalDate startDate;

    private LocalDate endDate;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
