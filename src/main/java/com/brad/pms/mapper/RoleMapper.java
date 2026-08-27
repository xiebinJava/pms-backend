package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.RoleDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RoleMapper extends BaseMapper<RoleDO> {
    @Select("SELECT * FROM sys_role WHERE code = #{code} LIMIT 1")
    RoleDO findByCode(@Param("code") String code);
}
