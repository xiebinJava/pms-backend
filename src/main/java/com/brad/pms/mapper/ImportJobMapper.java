package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ImportJobDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ImportJobMapper extends BaseMapper<ImportJobDO> {
    @Select("SELECT * FROM sys_import_job WHERE id = #{id} FOR UPDATE")
    ImportJobDO selectByIdForUpdate(@Param("id") String id);
}
