package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_requirement")
public class RequirementDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String description;
    private Integer priority;
    private Long ownerId;
    private RequirementExecutionTargetType executionTargetType;
    private Long executionTargetId;
    private String status;

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
