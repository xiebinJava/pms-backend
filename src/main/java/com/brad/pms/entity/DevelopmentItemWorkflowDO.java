package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_development_item_workflow")
public class DevelopmentItemWorkflowDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String itemType;
    private Long itemId;
    private Long projectId;
    private Long sourceNodeId;
    private Long templateVersionId;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
