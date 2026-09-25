package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface WorkflowTemplateVersionMapper extends BaseMapper<WorkflowTemplateVersionDO> {
    @Select("SELECT * FROM pms_workflow_template_version WHERE id = #{id} FOR UPDATE")
    WorkflowTemplateVersionDO selectByIdForUpdate(@Param("id") Long id);
}
