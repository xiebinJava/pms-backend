package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.PermissionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PermissionMapper extends BaseMapper<PermissionDO> {
    @Select("SELECT p.* FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id JOIN sys_role r ON r.id = rp.role_id AND r.enabled = TRUE AND r.deleted = FALSE JOIN sys_user_role ur ON ur.role_id = rp.role_id WHERE ur.user_id = #{userId} AND ur.status = 'ACTIVE' AND (ur.start_at IS NULL OR ur.start_at <= CURRENT_TIMESTAMP) AND (ur.end_at IS NULL OR ur.end_at > CURRENT_TIMESTAMP) ORDER BY p.sort, p.id")
    List<PermissionDO> findLiveByUserId(@Param("userId") Long userId);

    @Select("SELECT p.* FROM sys_permission p JOIN sys_role_permission rp ON rp.permission_id = p.id WHERE rp.role_id = #{roleId} ORDER BY p.sort, p.id")
    List<PermissionDO> findByRoleId(@Param("roleId") Long roleId);

    @Select("SELECT DISTINCT ur.role_id FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id AND r.enabled = TRUE AND r.deleted = FALSE JOIN sys_role_permission rp ON rp.role_id = ur.role_id JOIN sys_permission p ON p.id = rp.permission_id WHERE ur.user_id = #{userId} AND p.code = #{permissionCode} AND ur.status = 'ACTIVE' AND (ur.start_at IS NULL OR ur.start_at <= CURRENT_TIMESTAMP) AND (ur.end_at IS NULL OR ur.end_at > CURRENT_TIMESTAMP)")
    List<Long> findLiveRoleIdsByUserAndPermission(@Param("userId") Long userId, @Param("permissionCode") String permissionCode);
}
