package com.brad.pms.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

public interface RolePermissionMapper {
    @Select("SELECT COUNT(*) FROM sys_role_permission WHERE role_id = #{roleId} AND permission_id = #{permissionId}")
    int count(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Insert("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId})")
    int insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
