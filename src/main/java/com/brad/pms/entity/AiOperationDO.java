package com.brad.pms.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Durable preview/execute lifecycle for an AI-proposed PMS operation. */
@Data
@TableName("pms_ai_operation")
public class AiOperationDO {

    @TableId
    private String id;

    private String commandName;
    private Long userId;
    private String contextId;
    private String contextVersion;
    private String contractId;
    private String contractVersion;
    private String argumentsJson;
    private String previewJson;
    private String expectedVersionsJson;
    private String status;
    private String idempotencyKey;
    private String resultJson;
    private LocalDateTime expiresAt;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
