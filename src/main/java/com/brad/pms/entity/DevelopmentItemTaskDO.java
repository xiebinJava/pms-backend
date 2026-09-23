package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pms_development_item_task")
public class DevelopmentItemTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private Long nodeId;
    private Long parentId;
    private String title;
    private String description;
    private Integer status;
    private Integer priority;
    private Long assigneeId;
    private LocalDate dueDate;
    private Integer sort;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
    @Version
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
