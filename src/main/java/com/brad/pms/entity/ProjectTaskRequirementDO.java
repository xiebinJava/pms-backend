package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_task_requirement")
public class ProjectTaskRequirementDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;
    private Long nodeId;
    private Long taskId;
    private Long requirementId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
