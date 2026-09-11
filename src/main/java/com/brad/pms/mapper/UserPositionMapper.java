package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.UserPositionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserPositionMapper extends BaseMapper<UserPositionDO> {
    @Select("SELECT * FROM sys_user_position WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY is_primary DESC, start_date DESC, id DESC")
    List<UserPositionDO> findActiveByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM sys_user_position WHERE manager_user_id = #{managerUserId} AND status = 'ACTIVE'")
    List<UserPositionDO> findActiveByManagerUserId(@Param("managerUserId") Long managerUserId);

    @Select("SELECT * FROM sys_user_position WHERE user_id = #{userId} AND is_primary = TRUE AND status = 'ACTIVE' LIMIT 1")
    UserPositionDO findActivePrimary(@Param("userId") Long userId);

    @Select("SELECT * FROM sys_user_position WHERE user_id = #{userId} AND org_unit_id = #{orgUnitId} AND status = 'ACTIVE' LIMIT 1")
    UserPositionDO findActiveByUserAndOrg(@Param("userId") Long userId, @Param("orgUnitId") Long orgUnitId);

    @Select("SELECT COUNT(*) FROM sys_user_position WHERE user_id = #{userId} AND is_primary = TRUE AND status = 'ACTIVE'")
    int countActivePrimary(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM sys_user_position WHERE org_unit_id = #{orgUnitId} AND status = 'ACTIVE'")
    int countActiveByOrgUnitId(@Param("orgUnitId") Long orgUnitId);
}
