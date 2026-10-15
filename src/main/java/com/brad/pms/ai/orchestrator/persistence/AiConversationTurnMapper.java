package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AiConversationTurnMapper extends BaseMapper<AiConversationTurnDO> {

    @Select("SELECT * FROM pms_ai_conversation_turn "
            + "WHERE id = #{turnId} AND conversation_id = #{conversationId}")
    AiConversationTurnDO selectByIdAndConversationId(@Param("turnId") Long turnId,
                                                     @Param("conversationId") Long conversationId);
}
