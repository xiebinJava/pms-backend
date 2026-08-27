package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.AuthSessionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AuthSessionMapper extends BaseMapper<AuthSessionDO> {
    @Select("SELECT * FROM sys_auth_session WHERE refresh_token_hash = #{hash} LIMIT 1")
    AuthSessionDO findByRefreshTokenHash(@Param("hash") String hash);

    @Select("SELECT COUNT(*) FROM sys_auth_session WHERE user_id = #{userId} AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP")
    int countLiveByUserId(@Param("userId") Long userId);

    @Update("UPDATE sys_auth_session SET revoked_at = CURRENT_TIMESTAMP, revoke_reason = #{reason} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("reason") String reason);
}
