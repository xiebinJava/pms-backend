package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_ai_operation_event")
public class AiOperationEventDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private Long planId;
    private String operationId;
    private Long sequenceNo;
    private String eventType;
    private String detailJson;
    private LocalDateTime createdAt;
}
