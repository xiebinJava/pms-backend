package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ExternalIdentityDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ExternalIdentityMapper extends BaseMapper<ExternalIdentityDO> {

    @Select("SELECT * FROM sys_external_identity WHERE issuer = #{issuer} AND subject = #{subject} LIMIT 1")
    ExternalIdentityDO findByIssuerAndSubject(@Param("issuer") String issuer, @Param("subject") String subject);
}
