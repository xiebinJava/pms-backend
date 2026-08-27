package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.UserRoleDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserRoleMapper extends BaseMapper<UserRoleDO> {
    @Select("SELECT ur.* FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE ur.user_id = #{userId} AND r.code = #{roleCode} AND ur.status = 'ACTIVE' AND (ur.start_at IS NULL OR ur.start_at <= CURRENT_TIMESTAMP) AND (ur.end_at IS NULL OR ur.end_at > CURRENT_TIMESTAMP)")
    List<UserRoleDO> findLiveByUserAndRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    @Select("SELECT ur.* FROM sys_user_role ur WHERE ur.user_id = #{userId} AND ur.status = 'ACTIVE' AND (ur.start_at IS NULL OR ur.start_at <= CURRENT_TIMESTAMP) AND (ur.end_at IS NULL OR ur.end_at > CURRENT_TIMESTAMP)")
    List<UserRoleDO> findLiveByUserId(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE r.code = #{roleCode} AND ur.status = 'ACTIVE' AND (ur.start_at IS NULL OR ur.start_at <= CURRENT_TIMESTAMP) AND (ur.end_at IS NULL OR ur.end_at > CURRENT_TIMESTAMP)")
    int countLiveByRoleCode(@Param("roleCode") String roleCode);
}
