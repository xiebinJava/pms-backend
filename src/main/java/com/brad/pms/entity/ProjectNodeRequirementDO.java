package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_node_requirement")
public class ProjectNodeRequirementDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long nodeId;
    private String code;
    private String name;
    private String description;
    private String type;
    private Integer priority;
    private String acceptanceCriteria;
    private Integer status;
    private Integer sort;
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
