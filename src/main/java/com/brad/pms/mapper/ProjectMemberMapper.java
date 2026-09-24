package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectMemberDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectMemberMapper extends BaseMapper<ProjectMemberDO> {

    @Select("SELECT * FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId} LIMIT 1")
    ProjectMemberDO selectIncludingDeleted(@Param("projectId") Long projectId, @Param("userId") Long userId);

    @Select("SELECT * FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId} FOR UPDATE")
    ProjectMemberDO selectForUpdateIncludingDeleted(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
