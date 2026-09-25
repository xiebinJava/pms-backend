package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.WorkflowTemplateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowTemplateMapper extends BaseMapper<WorkflowTemplateDO> {
    @Select("SELECT * FROM pms_workflow_template WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    WorkflowTemplateDO selectActiveByIdForUpdate(@Param("id") Long id);
}
