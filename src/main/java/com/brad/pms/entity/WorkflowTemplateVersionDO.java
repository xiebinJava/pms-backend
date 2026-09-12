package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_workflow_template_version")
public class WorkflowTemplateVersionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Integer versionNo;
    private String status;
    private String definitionJson;
    private LocalDateTime publishedAt;
    private Long createdBy;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
