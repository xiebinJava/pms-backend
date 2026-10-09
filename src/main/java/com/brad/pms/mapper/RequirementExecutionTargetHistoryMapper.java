package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.RequirementExecutionTargetHistoryDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RequirementExecutionTargetHistoryMapper extends BaseMapper<RequirementExecutionTargetHistoryDO> {

    @Select("SELECT * FROM pms_requirement_execution_target_history "
            + "WHERE requirement_id = #{requirementId} ORDER BY created_at DESC, id DESC")
    List<RequirementExecutionTargetHistoryDO> selectByRequirementId(@Param("requirementId") Long requirementId);
}
