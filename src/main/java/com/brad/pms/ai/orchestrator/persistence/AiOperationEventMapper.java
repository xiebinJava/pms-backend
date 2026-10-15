package com.brad.pms.ai.orchestrator.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiOperationEventMapper extends BaseMapper<AiOperationEventDO> {

    default int appendNext(Long planId, Long conversationId, String operationId,
                           String eventType, String detailJson) {
        long nextSequence = selectNextSequenceForUpdate(conversationId) + 1L;
        incrementSequence(conversationId);
        return insertEvent(planId, conversationId, operationId, nextSequence, eventType, detailJson);
    }

    @Select("SELECT event_sequence FROM pms_ai_conversation "
            + "WHERE id = #{conversationId} FOR UPDATE")
    Long selectNextSequenceForUpdate(@Param("conversationId") Long conversationId);

    @org.apache.ibatis.annotations.Update("UPDATE pms_ai_conversation "
            + "SET event_sequence = event_sequence + 1 WHERE id = #{conversationId}")
    int incrementSequence(@Param("conversationId") Long conversationId);

    @Insert("INSERT INTO pms_ai_operation_event "
            + "(conversation_id, plan_id, operation_id, sequence_no, event_type, detail_json) "
            + "VALUES (#{conversationId}, #{planId}, #{operationId}, #{sequenceNo}, "
            + "#{eventType}, #{detailJson})")
    int insertEvent(@Param("planId") Long planId,
                    @Param("conversationId") Long conversationId,
                    @Param("operationId") String operationId,
                    @Param("sequenceNo") Long sequenceNo,
                    @Param("eventType") String eventType,
                    @Param("detailJson") String detailJson);

    @Select("SELECT e.* FROM pms_ai_operation_event e "
            + "JOIN pms_ai_conversation c ON c.id = e.conversation_id "
            + "WHERE e.conversation_id = #{conversationId} AND c.user_id = #{userId} "
            + "AND e.sequence_no > #{afterSequence} "
            + "ORDER BY e.sequence_no ASC LIMIT #{limit}")
    List<AiOperationEventDO> selectAfterSequence(@Param("conversationId") Long conversationId,
                                                 @Param("userId") Long userId,
                                                 @Param("afterSequence") Long afterSequence,
                                                 @Param("limit") int limit);
}
