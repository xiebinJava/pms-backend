package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.LoginLogDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface LoginLogMapper extends BaseMapper<LoginLogDO> {

    @Delete("DELETE FROM sys_login_log WHERE created_at < #{cutoff} AND result <> 'FAILURE'")
    int deleteExpiredSuccessful(@Param("cutoff") LocalDateTime cutoff);
}
