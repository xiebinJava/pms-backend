package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface DevelopmentItemWorkflowMapper extends BaseMapper<DevelopmentItemWorkflowDO> {
    @Select("SELECT * FROM pms_development_item_workflow WHERE item_type = #{itemType} AND item_id = #{itemId} LIMIT 1")
    DevelopmentItemWorkflowDO selectByItem(@Param("itemType") String itemType, @Param("itemId") Long itemId);

    @Select("SELECT * FROM pms_development_item_workflow WHERE item_type = #{itemType} AND item_id = #{itemId} FOR UPDATE")
    DevelopmentItemWorkflowDO selectForUpdate(@Param("itemType") String itemType, @Param("itemId") Long itemId);

    @Select({
            "<script>",
            "SELECT * FROM pms_development_item_workflow WHERE item_type = #{itemType} AND item_id IN",
            "<foreach collection='itemIds' item='itemId' open='(' separator=',' close=')'>#{itemId}</foreach>",
            "</script>"
    })
    List<DevelopmentItemWorkflowDO> selectByItems(@Param("itemType") String itemType,
                                                  @Param("itemIds") Collection<Long> itemIds);
}
