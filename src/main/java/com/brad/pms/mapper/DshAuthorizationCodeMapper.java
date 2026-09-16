package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.DshAuthorizationCodeDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface DshAuthorizationCodeMapper extends BaseMapper<DshAuthorizationCodeDO> {

    @Select("SELECT * FROM pms_dsh_authorization_codes WHERE code_hash = #{codeHash} FOR UPDATE")
    DshAuthorizationCodeDO selectByCodeHashForUpdate(@Param("codeHash") String codeHash);

    @Update("UPDATE pms_dsh_authorization_codes SET used_at = #{usedAt} "
            + "WHERE id = #{id} AND used_at IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);
}
