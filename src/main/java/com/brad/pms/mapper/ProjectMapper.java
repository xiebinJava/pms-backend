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
    @Select("SELECT id, code, name, description, status, priority, project_level, project_type_id, workflow_template_version_id, owner_id, project_manager_id, org_unit_id, start_date, end_date, progress, deleted, version, created_by, created_at, updated_at FROM project WHERE id = #{id}")
    ProjectDO selectIncludingDeleted(@Param("id") Long id);

    @Select({
            "<script>",
            "SELECT id, code, name, description, status, priority, project_level, project_type_id, workflow_template_version_id, owner_id, project_manager_id, org_unit_id, start_date, end_date, progress, deleted, version, created_by, created_at, updated_at",
            "FROM project WHERE id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<ProjectDO> selectIncludingDeletedByIds(@Param("ids") Collection<Long> ids);

    @Update("UPDATE project SET status = #{status}, deleted = TRUE, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = FALSE")
    int softDeleteProject(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE project SET status = #{status}, deleted = FALSE, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int restoreProject(@Param("id") Long id, @Param("status") Integer status);

    /** Binds every legacy project, including soft-deleted rows, without rewriting its node records. */
    @Update("UPDATE project p LEFT JOIN pms_project_type t ON t.id = p.project_type_id "
            + "SET p.project_type_id = COALESCE(p.project_type_id, #{fallbackTypeId}), "
            + "p.workflow_template_version_id = COALESCE(p.workflow_template_version_id, COALESCE(t.default_template_version_id, #{fallbackVersionId})), "
            + "p.version = p.version + 1, p.updated_at = CURRENT_TIMESTAMP "
            + "WHERE p.project_type_id IS NULL OR p.workflow_template_version_id IS NULL")
    int bindMissingWorkflowConfiguration(@Param("fallbackTypeId") Long fallbackTypeId,
                                        @Param("fallbackVersionId") Long fallbackVersionId);
}
