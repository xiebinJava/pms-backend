package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.PositionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface PositionMapper extends BaseMapper<PositionDO> {
    @Select("SELECT * FROM sys_position WHERE code = #{code} LIMIT 1")
    PositionDO findByCode(@Param("code") String code);
}
