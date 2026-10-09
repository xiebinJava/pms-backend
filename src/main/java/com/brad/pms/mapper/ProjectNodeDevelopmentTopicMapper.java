package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectNodeDevelopmentTopicMapper extends BaseMapper<ProjectNodeDevelopmentTopicDO> {

    @Select("SELECT * FROM project_node_development_topic WHERE project_id = #{projectId} AND deleted = FALSE ORDER BY id ASC")
    java.util.List<ProjectNodeDevelopmentTopicDO> selectActiveByProjectId(@Param("projectId") Long projectId);

    @Select("SELECT * FROM project_node_development_topic WHERE project_id = #{projectId} AND deleted = FALSE ORDER BY id ASC FOR UPDATE")
    java.util.List<ProjectNodeDevelopmentTopicDO> selectByProjectIdForUpdate(@Param("projectId") Long projectId);

    @Select("SELECT * FROM project_node_development_topic WHERE project_id IS NULL AND deleted = #{deleted} ORDER BY sort ASC, id ASC")
    java.util.List<ProjectNodeDevelopmentTopicDO> selectUnbound(@Param("deleted") Boolean deleted);

    @Select("SELECT * FROM project_node_development_topic WHERE id = #{id} FOR UPDATE")
    ProjectNodeDevelopmentTopicDO selectByIdForUpdate(@Param("id") Long id);
}
