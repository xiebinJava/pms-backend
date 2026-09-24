package com.brad.pms.mapper;

import com.brad.pms.entity.ProjectMemberAssignmentRefDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ProjectMemberAssignmentRefMapper {

    @Select("SELECT * FROM pms_project_member_assignment_ref WHERE project_id = #{projectId} "
            + "AND item_type = #{itemType} AND item_id = #{itemId} "
            + "AND assignment_type = #{assignmentType} AND assignment_id = #{assignmentId} FOR UPDATE")
    ProjectMemberAssignmentRefDO selectByKey(@Param("projectId") Long projectId,
                                             @Param("itemType") String itemType,
                                             @Param("itemId") Long itemId,
                                             @Param("assignmentType") String assignmentType,
                                             @Param("assignmentId") Long assignmentId);

    @Select("SELECT * FROM pms_project_member_assignment_ref WHERE project_id = #{projectId} "
            + "AND item_type = #{itemType} AND item_id = #{itemId} FOR UPDATE")
    List<ProjectMemberAssignmentRefDO> selectByItemForUpdate(@Param("projectId") Long projectId,
                                                              @Param("itemType") String itemType,
                                                              @Param("itemId") Long itemId);

    @Select("SELECT * FROM pms_project_member_assignment_ref WHERE project_id = #{projectId} "
            + "AND user_id = #{userId} FOR UPDATE")
    List<ProjectMemberAssignmentRefDO> selectByProjectUserForUpdate(@Param("projectId") Long projectId,
                                                                     @Param("userId") Long userId);

    @Insert("INSERT INTO pms_project_member_assignment_ref"
            + "(project_id, item_type, item_id, assignment_type, assignment_id, user_id) "
            + "VALUES(#{projectId}, #{itemType}, #{itemId}, #{assignmentType}, #{assignmentId}, #{userId}) "
            + "ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)")
    int upsert(ProjectMemberAssignmentRefDO ref);

    @Delete("DELETE FROM pms_project_member_assignment_ref WHERE project_id = #{projectId} "
            + "AND item_type = #{itemType} AND item_id = #{itemId} "
            + "AND assignment_type = #{assignmentType} AND assignment_id = #{assignmentId}")
    int deleteByKey(@Param("projectId") Long projectId,
                    @Param("itemType") String itemType,
                    @Param("itemId") Long itemId,
                    @Param("assignmentType") String assignmentType,
                    @Param("assignmentId") Long assignmentId);

    @Delete("DELETE FROM pms_project_member_assignment_ref WHERE project_id = #{projectId} "
            + "AND item_type = #{itemType} AND item_id = #{itemId}")
    int deleteByItem(@Param("projectId") Long projectId,
                     @Param("itemType") String itemType,
                     @Param("itemId") Long itemId);
}
