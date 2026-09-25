package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.FeedbackTicketDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface FeedbackTicketMapper extends BaseMapper<FeedbackTicketDO> {

    @Select("SELECT * FROM feedback_ticket WHERE reporter_id = #{reporterId} "
            + "AND client_request_id = #{clientRequestId} AND deleted = FALSE LIMIT 1")
    FeedbackTicketDO findByReporterAndClientRequestId(@Param("reporterId") Long reporterId,
                                                       @Param("clientRequestId") String clientRequestId);

    @Select("SELECT * FROM feedback_ticket WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    FeedbackTicketDO selectForUpdate(@Param("id") Long id);
}
