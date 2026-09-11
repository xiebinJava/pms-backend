package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

public interface ProjectMapper extends BaseMapper<ProjectDO> {

    /** Loads a soft-deleted project for an authorized restore/recovery flow. */
    @Select("SELECT id, code, name, description, status, priority, owner_id, project_manager_id, org_unit_id, start_date, end_date, progress, deleted, version, created_by, created_at, updated_at FROM project WHERE id = #{id}")
    ProjectDO selectIncludingDeleted(@Param("id") Long id);

    @Select({
            "<script>",
            "SELECT id, code, name, description, status, priority, owner_id, project_manager_id, org_unit_id, start_date, end_date, progress, deleted, version, created_by, created_at, updated_at",
            "FROM project WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<ProjectDO> selectIncludingDeletedByIds(@Param("ids") Collection<Long> ids);

    @Update("UPDATE project SET status = #{status}, deleted = TRUE, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = FALSE")
    int softDeleteProject(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE project SET status = #{status}, deleted = FALSE, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int restoreProject(@Param("id") Long id, @Param("status") Integer status);
}
