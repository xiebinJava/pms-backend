package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_requirement_execution_target_history")
public class RequirementExecutionTargetHistoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requirementId;
    private String action;
    private RequirementExecutionTargetType targetType;
    private Long targetId;
    private RequirementExecutionTargetType previousTargetType;
    private Long previousTargetId;
    private String reason;
    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
