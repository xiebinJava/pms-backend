package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.dto.TaskReminderCandidate;
import com.brad.pms.entity.ProjectTaskDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ProjectTaskMapper extends BaseMapper<ProjectTaskDO> {

    @Select({
            "<script>",
            "SELECT project_id, status, COUNT(*) AS cnt",
            "FROM project_task",
            "WHERE deleted = FALSE AND project_id IN",
            "<foreach collection='projectIds' item='pid' open='(' separator=',' close=')'>",
            "#{pid}",
            "</foreach>",
            "GROUP BY project_id, status",
            "</script>"
    })
    List<Map<String, Object>> countByProjectIds(@Param("projectIds") List<Long> projectIds);

    @Select({
            "<script>",
            "SELECT t.id AS task_id, t.project_id, t.assignee_id,",
            "       t.title AS task_title, p.name AS project_name, t.due_date",
            "FROM project_task t",
            "JOIN project p ON p.id = t.project_id",
            "JOIN sys_user u ON u.id = t.assignee_id",
            "WHERE t.deleted = FALSE",
            "  AND t.status &lt;&gt; #{doneStatus}",
            "  AND t.due_date IN (#{dueSoonDate}, #{overdueDate})",
            "  AND p.deleted = FALSE",
            "  AND p.status IN (0, #{activeStatus})",
            "  AND u.deleted = FALSE",
            "  AND u.status = 'ACTIVE'",
            "ORDER BY t.due_date, t.id",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<TaskReminderCandidate> findReminderCandidates(
            @Param("dueSoonDate") LocalDate dueSoonDate,
            @Param("overdueDate") LocalDate overdueDate,
            @Param("doneStatus") int doneStatus,
            @Param("activeStatus") int activeStatus,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
