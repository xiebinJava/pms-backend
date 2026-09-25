package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project_task")
public class ProjectTaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long nodeId;

    private Long parentId;

    private String title;

    private String description;

    private String deliverable;

    private Integer status;

    private Integer priority;

    private Long assigneeId;

    private Integer sort;

    private LocalDate dueDate;

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
