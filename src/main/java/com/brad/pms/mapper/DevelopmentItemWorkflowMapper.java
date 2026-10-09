package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

public interface DevelopmentItemWorkflowMapper extends BaseMapper<DevelopmentItemWorkflowDO> {
    @Select("SELECT * FROM pms_development_item_workflow WHERE item_type = #{itemType} AND item_id = #{itemId} LIMIT 1")
    DevelopmentItemWorkflowDO selectByItem(@Param("itemType") String itemType, @Param("itemId") Long itemId);

    @Select("SELECT * FROM pms_development_item_workflow WHERE item_type = #{itemType} AND item_id = #{itemId} FOR UPDATE")
    DevelopmentItemWorkflowDO selectForUpdate(@Param("itemType") String itemType, @Param("itemId") Long itemId);

    /** Rebinds a story's workflow scope to match its new topic, guarding against concurrent edits. */
    @Update("UPDATE pms_development_item_workflow SET project_id = #{projectId}, source_node_id = #{nodeId}, "
            + "updated_at = NOW(), version = version + 1 WHERE id = #{id} AND version = #{expectedVersion}")
    int updateScopeFromStory(@Param("id") Long id, @Param("projectId") Long projectId, @Param("nodeId") Long nodeId,
                             @Param("expectedVersion") Integer expectedVersion);

    @Select({
            "<script>",
            "SELECT * FROM pms_development_item_workflow WHERE item_type = #{itemType} AND item_id IN",
            "<foreach collection='itemIds' item='itemId' open='(' separator=',' close=')'>#{itemId}</foreach>",
            "</script>"
    })
    List<DevelopmentItemWorkflowDO> selectByItems(@Param("itemType") String itemType,
                                                  @Param("itemIds") Collection<Long> itemIds);
}
