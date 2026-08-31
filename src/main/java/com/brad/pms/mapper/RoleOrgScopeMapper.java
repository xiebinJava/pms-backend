package com.brad.pms.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

public interface RoleOrgScopeMapper {
    @Select("SELECT org_unit_id FROM sys_role_org_scope WHERE role_id = #{roleId}")
    List<Long> findOrgUnitIds(@Param("roleId") Long roleId);

    @Select("SELECT COUNT(*) FROM sys_role_org_scope WHERE role_id = #{roleId} AND org_unit_id = #{orgUnitId}")
    int count(@Param("roleId") Long roleId, @Param("orgUnitId") Long orgUnitId);

    @Insert("INSERT INTO sys_role_org_scope (role_id, org_unit_id) VALUES (#{roleId}, #{orgUnitId})")
    int insert(@Param("roleId") Long roleId, @Param("orgUnitId") Long orgUnitId);

    @Delete("DELETE FROM sys_role_org_scope WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
