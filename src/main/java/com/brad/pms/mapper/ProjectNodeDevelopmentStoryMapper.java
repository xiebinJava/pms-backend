package com.brad.pms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ProjectNodeDevelopmentStoryMapper extends BaseMapper<ProjectNodeDevelopmentStoryDO> {

    @Select("SELECT * FROM project_node_development_story WHERE topic_id IS NULL ORDER BY sort ASC, id ASC")
    java.util.List<ProjectNodeDevelopmentStoryDO> selectIndependent();

    @Select("SELECT * FROM project_node_development_story WHERE topic_id = #{topicId} ORDER BY sort ASC, id ASC")
    java.util.List<ProjectNodeDevelopmentStoryDO> selectByTopicId(@Param("topicId") Long topicId);

    @Select("SELECT * FROM project_node_development_story WHERE topic_id = #{topicId} AND topic_workflow_node_id = #{topicWorkflowNodeId} ORDER BY sort ASC, id ASC")
    List<ProjectNodeDevelopmentStoryDO> selectByTopicWorkflowNodeId(
            @Param("topicId") Long topicId,
            @Param("topicWorkflowNodeId") Long topicWorkflowNodeId);

    @Select("SELECT * FROM project_node_development_story WHERE id = #{id} FOR UPDATE")
    ProjectNodeDevelopmentStoryDO selectByIdForUpdate(@Param("id") Long id);

    /** Renames a story as part of the writing workbench, independent of its workflow scope. */
    @Update("UPDATE project_node_development_story SET title = #{title}, updated_at = NOW() WHERE id = #{id}")
    int updateTitle(@Param("id") Long id, @Param("title") String title);

    /** Rebinds a story to a topic (or detaches it) and clears the now-stale iteration plan. */
    @Update("UPDATE project_node_development_story SET topic_id = #{topicId}, topic_workflow_node_id = #{topicWorkflowNodeId}, "
            + "project_id = #{projectId}, node_id = #{nodeId}, iteration_plan_id = NULL, updated_at = NOW() WHERE id = #{id}")
    int updateScope(@Param("id") Long id, @Param("topicId") Long topicId,
                    @Param("topicWorkflowNodeId") Long topicWorkflowNodeId,
                    @Param("projectId") Long projectId, @Param("nodeId") Long nodeId);

    /** Links or detaches a story from an iteration plan set from the iteration node workbench. */
    @Update("UPDATE project_node_development_story SET iteration_plan_id = #{iterationPlanId}, updated_at = NOW() WHERE id = #{id}")
    int updateIterationPlan(@Param("id") Long id, @Param("iterationPlanId") Long iterationPlanId);

    /** Updates only the workflow-derived fields, without overwriting story identity or scope. */
    @Update("UPDATE project_node_development_story SET status = #{status}, progress = #{progress}, updated_at = NOW() WHERE id = #{id}")
    int updateWorkflowProgress(@Param("id") Long id, @Param("status") String status,
                               @Param("progress") int progress);
}
