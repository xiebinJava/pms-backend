package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pms_ai_conversation_turn")
public class AiConversationTurnDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;
    private String messageText;
    private String draftJson;
    private String requestId;
    private LocalDateTime createdAt;
}
