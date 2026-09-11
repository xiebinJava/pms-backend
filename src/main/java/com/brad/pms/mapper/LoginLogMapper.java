package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.LoginLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface LoginLogMapper extends BaseMapper<LoginLogDO> {

    @Select("SELECT COUNT(*) FROM sys_login_log WHERE created_at < #{cutoff} "
            + "AND result <> 'FAILURE'")
    long countExpiredSuccessful(@Param("cutoff") LocalDateTime cutoff);

    @Delete("DELETE FROM sys_login_log WHERE created_at < #{cutoff} "
            + "AND result <> 'FAILURE' LIMIT #{batchSize}")
    int deleteExpiredSuccessfulBatch(@Param("cutoff") LocalDateTime cutoff,
                                     @Param("batchSize") int batchSize);
}
