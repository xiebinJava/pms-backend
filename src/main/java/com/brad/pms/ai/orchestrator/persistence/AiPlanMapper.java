package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiPlanMapper extends BaseMapper<AiPlanDO> {

    @Select("SELECT p.* FROM pms_ai_plan p "
            + "JOIN pms_ai_conversation c ON c.id = p.conversation_id "
            + "WHERE p.id = #{planId} AND c.user_id = #{userId}")
    AiPlanDO selectByIdAndConversationUser(@Param("planId") Long planId,
                                           @Param("userId") Long userId);

    @Select("SELECT p.* FROM pms_ai_plan p "
            + "JOIN pms_ai_conversation c ON c.id = p.conversation_id "
            + "WHERE p.id = #{planId} AND c.user_id = #{userId} FOR UPDATE")
    AiPlanDO selectForUpdate(@Param("planId") Long planId, @Param("userId") Long userId);

    @Select("SELECT p.* FROM pms_ai_plan p "
            + "JOIN pms_ai_conversation c ON c.id = p.conversation_id "
            + "WHERE p.conversation_id = #{conversationId} AND c.user_id = #{userId} "
            + "ORDER BY p.id DESC LIMIT 1")
    AiPlanDO selectLatestByConversationUser(@Param("conversationId") Long conversationId,
                                            @Param("userId") Long userId);

    @Update("UPDATE pms_ai_plan p "
            + "JOIN pms_ai_conversation c ON c.id = p.conversation_id "
            + "SET p.status = #{status}, p.error_code = #{errorCode}, "
            + "p.error_message = #{errorMessage}, p.updated_at = #{updatedAt} "
            + "WHERE p.id = #{planId} AND c.user_id = #{userId}")
    int updateState(@Param("planId") Long planId,
                    @Param("userId") Long userId,
                    @Param("status") String status,
                    @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage,
                    @Param("updatedAt") LocalDateTime updatedAt);
}
