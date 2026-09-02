package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_import_job")
public class ImportJobDO {
    @TableId
    private String id;
    private String importType;
    private String filename;
    private String status;
    private Integer rowCount;
    private Integer errorCount;
    private String previewJson;
    private String errorJson;
    private Long createdBy;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime committedAt;
    private String failureReason;
    private LocalDateTime failedAt;
}
