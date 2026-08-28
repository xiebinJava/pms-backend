package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.OperationLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface OperationLogMapper extends BaseMapper<OperationLogDO> {

    @Select("SELECT COUNT(*) FROM sys_operation_log WHERE created_at < #{cutoff} "
            + "AND action NOT IN ('PERMISSION_DENIED', 'AUTHORIZATION_DENIED')")
    long countExpired(@Param("cutoff") LocalDateTime cutoff);

    @Delete("DELETE FROM sys_operation_log WHERE created_at < #{cutoff} "
            + "AND action NOT IN ('PERMISSION_DENIED', 'AUTHORIZATION_DENIED')")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
