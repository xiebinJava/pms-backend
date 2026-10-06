package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectNodeIterationPlanMapper extends BaseMapper<ProjectNodeIterationPlanDO> {

    @Select("SELECT * FROM project_node_iteration_plan WHERE id = #{id} FOR UPDATE")
    ProjectNodeIterationPlanDO selectByIdForUpdate(@Param("id") Long id);
}
