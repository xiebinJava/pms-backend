package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.PasswordResetTokenDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenDO> {
    @Select("SELECT * FROM sys_password_reset_token WHERE token_hash = #{hash} AND used_at IS NULL LIMIT 1")
    PasswordResetTokenDO findPending(@Param("hash") String hash);
    @Update("UPDATE sys_password_reset_token SET used_at = CURRENT_TIMESTAMP WHERE id = #{id} AND used_at IS NULL")
    int markUsed(@Param("id") Long id);
}
