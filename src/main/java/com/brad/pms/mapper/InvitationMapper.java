package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.InvitationDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface InvitationMapper extends BaseMapper<InvitationDO> {
    @Select("SELECT * FROM sys_invitation WHERE token_hash = #{hash} AND status = 'PENDING' LIMIT 1")
    InvitationDO findPendingByHash(@Param("hash") String hash);

    @Update("UPDATE sys_invitation SET status = 'USED', used_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'PENDING'")
    int markUsed(@Param("id") Long id);

    @Update("UPDATE sys_invitation SET status = 'EXPIRED' WHERE user_id = #{userId} AND status = 'PENDING'")
    int expirePendingByUserId(@Param("userId") Long userId);
}
