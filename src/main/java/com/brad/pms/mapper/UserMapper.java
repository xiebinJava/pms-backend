package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.UserDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface UserMapper extends BaseMapper<UserDO> {

    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = FALSE")
    UserDO findByUsername(@Param("username") String username);

    @Select("SELECT * FROM sys_user WHERE username_normalized = #{usernameNormalized} AND deleted = FALSE LIMIT 1")
    UserDO findByUsernameNormalized(@Param("usernameNormalized") String usernameNormalized);

    @Select("SELECT * FROM sys_user WHERE email_normalized = #{emailNormalized} AND deleted = FALSE LIMIT 1")
    UserDO findByEmailNormalized(@Param("emailNormalized") String emailNormalized);

    /** Serializes concurrent primary-affiliation changes for one employee. */
    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = FALSE FOR UPDATE")
    UserDO selectForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM sys_user WHERE status = 'ACTIVE' AND deleted = FALSE AND (nickname LIKE CONCAT('%', #{keyword}, '%') OR name_zh LIKE CONCAT('%', #{keyword}, '%') OR username LIKE CONCAT('%', #{keyword}, '%') OR email LIKE CONCAT('%', #{keyword}, '%')) LIMIT 20")
    List<UserDO> search(@Param("keyword") String keyword);

    @Select({
            "<script>",
            "SELECT * FROM sys_user WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<UserDO> selectByIdsIncludingDeleted(@Param("ids") Collection<Long> ids);
}
