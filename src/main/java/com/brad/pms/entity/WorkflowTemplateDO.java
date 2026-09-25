package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_workflow_template")
public class WorkflowTemplateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long projectTypeId;
    private String name;
    private String description;
    private Integer latestVersionNo;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
