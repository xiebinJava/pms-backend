package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.entity.RequirementDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface RequirementMapper extends BaseMapper<RequirementDO> {

    @Select("SELECT * FROM pms_requirement WHERE id = #{id} FOR UPDATE")
    RequirementDO selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM pms_requirement WHERE execution_target_type = #{targetType} "
            + "AND execution_target_id = #{targetId} AND deleted = FALSE ORDER BY id DESC LIMIT 1")
    RequirementDO selectByExecutionTarget(@Param("targetType") RequirementExecutionTargetType targetType,
                                          @Param("targetId") Long targetId);

    @Select("<script>"
            + "SELECT * FROM pms_requirement "
            + "WHERE deleted = FALSE AND execution_target_type = #{targetType} "
            + "AND execution_target_id IN "
            + "<foreach collection='targetIds' item='targetId' open='(' separator=',' close=')'>"
            + "#{targetId}"
            + "</foreach>"
            + " ORDER BY id DESC"
            + "</script>")
    List<RequirementDO> selectByExecutionTargets(
            @Param("targetType") RequirementExecutionTargetType targetType,
            @Param("targetIds") List<Long> targetIds);
}
