package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.OrgUnitHistoryDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrgUnitHistoryMapper extends BaseMapper<OrgUnitHistoryDO> {
    @Select("SELECT * FROM sys_org_unit_history WHERE org_unit_id = #{orgUnitId} ORDER BY created_at DESC, id DESC")
    List<OrgUnitHistoryDO> findByOrgUnitId(@Param("orgUnitId") Long orgUnitId);
}
