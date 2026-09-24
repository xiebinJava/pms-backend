package com.brad.pms.mapper;

import com.brad.pms.entity.ProjectMemberAutoManagedDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectMemberAutoManagedMapper {

    @Select("SELECT * FROM pms_project_member_auto_managed WHERE project_id = #{projectId} AND user_id = #{userId} FOR UPDATE")
    ProjectMemberAutoManagedDO selectForUpdate(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Insert("INSERT INTO pms_project_member_auto_managed(project_id, user_id) VALUES(#{projectId}, #{userId}) "
            + "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)")
    int insertIgnore(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Delete("DELETE FROM pms_project_member_auto_managed WHERE project_id = #{projectId} AND user_id = #{userId}")
    int delete(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
