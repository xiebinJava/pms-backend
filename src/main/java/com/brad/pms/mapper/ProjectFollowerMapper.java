package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectFollowerDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProjectFollowerMapper extends BaseMapper<ProjectFollowerDO> {

    @Update("UPDATE project_follower SET deleted = FALSE, version = version + 1 "
            + "WHERE project_id = #{projectId} AND user_id = #{userId} AND deleted = TRUE")
    int restoreDeleted(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
