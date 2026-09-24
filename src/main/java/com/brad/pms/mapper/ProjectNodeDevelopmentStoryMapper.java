package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectNodeDevelopmentStoryMapper extends BaseMapper<ProjectNodeDevelopmentStoryDO> {

    @Select("SELECT * FROM project_node_development_story WHERE topic_id IS NULL ORDER BY sort ASC, id ASC")
    java.util.List<ProjectNodeDevelopmentStoryDO> selectIndependent();

    @Select("SELECT * FROM project_node_development_story WHERE topic_id = #{topicId} ORDER BY sort ASC, id ASC")
    java.util.List<ProjectNodeDevelopmentStoryDO> selectByTopicId(@Param("topicId") Long topicId);

    @Select("SELECT * FROM project_node_development_story WHERE id = #{id} FOR UPDATE")
    ProjectNodeDevelopmentStoryDO selectByIdForUpdate(@Param("id") Long id);
}
