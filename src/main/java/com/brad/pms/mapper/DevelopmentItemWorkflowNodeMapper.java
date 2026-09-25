package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface DevelopmentItemWorkflowNodeMapper extends BaseMapper<DevelopmentItemWorkflowNodeDO> {

    @Select({
            "<script>",
            "SELECT * FROM pms_development_item_workflow_node WHERE workflow_id IN",
            "<foreach collection='workflowIds' item='workflowId' open='(' separator=',' close=')'>#{workflowId}</foreach>",
            "ORDER BY workflow_id, sort, id FOR UPDATE",
            "</script>"
    })
    List<DevelopmentItemWorkflowNodeDO> selectByWorkflowIdsForUpdate(
            @Param("workflowIds") Collection<Long> workflowIds);

    @Select("SELECT * FROM pms_development_item_workflow_node WHERE workflow_id = #{workflowId} AND node_key = #{nodeKey} LIMIT 2")
    List<DevelopmentItemWorkflowNodeDO> selectByWorkflowAndNodeKey(
            @Param("workflowId") Long workflowId,
            @Param("nodeKey") String nodeKey);
}
