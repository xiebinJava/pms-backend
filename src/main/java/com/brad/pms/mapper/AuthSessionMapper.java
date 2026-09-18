package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.AuthSessionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AuthSessionMapper extends BaseMapper<AuthSessionDO> {
    @Select("SELECT * FROM sys_auth_session WHERE refresh_token_hash = #{hash} LIMIT 1")
    AuthSessionDO findByRefreshTokenHash(@Param("hash") String hash);

    @Update("UPDATE sys_auth_session SET refresh_token_hash = #{newHash}, ip = #{ip}, user_agent = #{userAgent} "
            + "WHERE id = #{id} AND user_id = #{userId} AND refresh_token_hash = #{oldHash} "
            + "AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP")
    int rotateRefreshToken(@Param("id") Long id, @Param("userId") Long userId,
                           @Param("oldHash") String oldHash, @Param("newHash") String newHash,
                           @Param("ip") String ip, @Param("userAgent") String userAgent);

    @Select("SELECT COUNT(*) FROM sys_auth_session WHERE user_id = #{userId} AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP")
    int countLiveByUserId(@Param("userId") Long userId);

    @Update("UPDATE sys_auth_session SET revoked_at = CURRENT_TIMESTAMP, revoke_reason = #{reason} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("reason") String reason);

    @Update("UPDATE sys_auth_session SET revoked_at = CURRENT_TIMESTAMP, revoke_reason = #{reason} "
            + "WHERE id = #{id} AND user_id = #{userId} AND revoked_at IS NULL")
    int revokeById(@Param("id") Long id, @Param("userId") Long userId, @Param("reason") String reason);
}
