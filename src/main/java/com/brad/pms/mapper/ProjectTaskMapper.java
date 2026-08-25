package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectTaskDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ProjectTaskMapper extends BaseMapper<ProjectTaskDO> {

    @Select({
            "<script>",
            "SELECT project_id, status, COUNT(*) AS cnt",
            "FROM project_task",
            "WHERE project_id IN",
            "<foreach collection='projectIds' item='pid' open='(' separator=',' close=')'>",
            "#{pid}",
            "</foreach>",
            "GROUP BY project_id, status",
            "</script>"
    })
    List<Map<String, Object>> countByProjectIds(@Param("projectIds") List<Long> projectIds);
}
