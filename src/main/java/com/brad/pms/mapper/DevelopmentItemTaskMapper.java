package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface DevelopmentItemTaskMapper extends BaseMapper<DevelopmentItemTaskDO> {

    @Select({
            "<script>",
            "SELECT * FROM pms_development_item_task WHERE workflow_id IN",
            "<foreach collection='workflowIds' item='workflowId' open='(' separator=',' close=')'>#{workflowId}</foreach>",
            "ORDER BY id FOR UPDATE",
            "</script>"
    })
    List<DevelopmentItemTaskDO> selectByWorkflowIdsForUpdate(@Param("workflowIds") Collection<Long> workflowIds);
}
