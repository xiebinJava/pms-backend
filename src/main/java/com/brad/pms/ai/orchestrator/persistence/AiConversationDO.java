package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_ai_conversation")
public class AiConversationDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String resourceType;
    private Long resourceId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
