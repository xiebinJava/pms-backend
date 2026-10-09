package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.SystemVersionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface SystemVersionMapper extends BaseMapper<SystemVersionDO> {
    @Select("SELECT * FROM pms_system_version WHERE id = #{id} FOR UPDATE")
    SystemVersionDO selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT * FROM pms_system_version WHERE system_id = #{systemId} AND version_no = #{versionNo} LIMIT 1")
    SystemVersionDO findBySystemIdAndVersionNo(@Param("systemId") Long systemId,
                                                 @Param("versionNo") String versionNo);

    @Select({
            "<script>",
            "SELECT * FROM pms_system_version WHERE id IN",
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>",
            "FOR UPDATE",
            "</script>"
    })
    List<SystemVersionDO> selectBatchByIdsForUpdate(@Param("ids") Collection<Long> ids);
}
