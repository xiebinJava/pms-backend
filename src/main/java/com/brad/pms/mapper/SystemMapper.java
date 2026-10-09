package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.SystemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SystemMapper extends BaseMapper<SystemDO> {
    @Select("SELECT * FROM pms_system WHERE id = #{id} FOR UPDATE")
    SystemDO selectByIdForUpdate(@Param("id") Long id);

}
