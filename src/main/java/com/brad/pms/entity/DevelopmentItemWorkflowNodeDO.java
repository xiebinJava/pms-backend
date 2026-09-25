package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pms_development_item_workflow_node")
public class DevelopmentItemWorkflowNodeDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private String nodeKey;
    private String name;
    private String description;
    private String deliverable;
    private Integer sort;
    private Integer status;
    private Long ownerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String fieldValuesJson;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
