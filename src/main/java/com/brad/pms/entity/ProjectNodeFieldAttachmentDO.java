package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_node_field_attachment")
public class ProjectNodeFieldAttachmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long nodeId;
    private String fieldKey;
    private String fileKey;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private Long createdBy;
    private LocalDateTime createdAt;
}
