package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProjectNodeIterationPlanMapper extends BaseMapper<ProjectNodeIterationPlanDO> {

    @Select("SELECT * FROM project_node_iteration_plan WHERE id = #{id} FOR UPDATE")
    ProjectNodeIterationPlanDO selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE project_node_iteration_plan SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE project_node_iteration_plan SET system_id = #{systemId}, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND system_id IS NULL")
    int fillSystem(@Param("id") Long id, @Param("systemId") Long systemId);
}
