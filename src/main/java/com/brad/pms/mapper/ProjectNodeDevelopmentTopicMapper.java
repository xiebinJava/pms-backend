package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectNodeDevelopmentTopicMapper extends BaseMapper<ProjectNodeDevelopmentTopicDO> {

    @Select("SELECT * FROM project_node_development_topic WHERE id = #{id} FOR UPDATE")
    ProjectNodeDevelopmentTopicDO selectByIdForUpdate(@Param("id") Long id);
}
