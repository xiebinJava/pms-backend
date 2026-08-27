package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.OrgUnitDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrgUnitMapper extends BaseMapper<OrgUnitDO> {
    @Select("SELECT * FROM sys_org_unit WHERE code = #{code} LIMIT 1")
    OrgUnitDO findByCode(@Param("code") String code);

    @Select("SELECT * FROM sys_org_unit WHERE status = 'ACTIVE' ORDER BY path, sort, id")
    List<OrgUnitDO> findActiveTree();

    @Select("SELECT id FROM sys_org_unit WHERE status = 'ACTIVE' AND (path LIKE CONCAT(#{path}, '%') OR id = #{rootId})")
    List<Long> findDescendantIds(@Param("path") String path, @Param("rootId") Long rootId);
}
