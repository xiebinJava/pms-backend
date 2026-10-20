package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_ai_plan")
public class AiPlanDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long turnId;
    private String intent;
    private String resourceType;
    private Long resourceId;
    private Long nodeId;
    private Long nodeVersion;
    private String componentKey;
    private Long contextVersion;
    private String contextSnapshotJson;
    private String draftJson;
    private String previewOperationId;
    private String idempotencyKey;
    private String status;
    private String verificationJson;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime executedAt;
}
