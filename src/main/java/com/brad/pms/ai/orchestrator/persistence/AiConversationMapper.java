package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversationDO> {

    @Select("SELECT * FROM pms_ai_conversation "
            + "WHERE id = #{conversationId} AND user_id = #{userId}")
    AiConversationDO selectByIdAndUserId(@Param("conversationId") Long conversationId,
                                         @Param("userId") Long userId);

    @Update("UPDATE pms_ai_conversation SET status = #{status}, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{conversationId} AND user_id = #{userId}")
    int updateStatus(@Param("conversationId") Long conversationId,
                     @Param("userId") Long userId,
                     @Param("status") String status);
}
