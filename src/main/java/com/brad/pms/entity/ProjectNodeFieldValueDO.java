package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_node_field_value")
public class ProjectNodeFieldValueDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long nodeId;
    private String fieldKey;
    private String valueJson;
    @Version
    private Integer version;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
