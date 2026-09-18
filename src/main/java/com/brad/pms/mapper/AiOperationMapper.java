package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.AiOperationDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiOperationMapper extends BaseMapper<AiOperationDO> {

    @Select("SELECT * FROM pms_ai_operation WHERE id = #{id} FOR UPDATE")
    AiOperationDO selectByIdForUpdate(@Param("id") String id);
}
