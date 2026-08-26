package com.brad.pms.convertor;

import com.brad.pms.dto.response.*;
import com.brad.pms.entity.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 实体 <-> DTO 转换器（实体与 DTO 互转，简化为静态方法）
 */
public final class Convertors {

    private Convertors() {
    }

    public static UserDTO toUser(UserDO do_) {
        if (do_ == null) return null;
        UserDTO dto = new UserDTO();
        dto.setId(do_.getId());
        dto.setUsername(do_.getUsername());
        dto.setNickname(do_.getNickname());
        dto.setEmail(do_.getEmail());
        dto.setAvatar(do_.getAvatar());
        dto.setCreatedAt(do_.getCreatedAt());
        return dto;
    }

    public static List<UserDTO> toUsers(List<UserDO> list) {
        return list.stream().map(Convertors::toUser).collect(Collectors.toList());
    }

    public static ProjectDTO toProject(ProjectDO do_, UserDO owner, UserDO createdBy, UserDO projectManager,
                                       int memberCount, int taskCount, int doneTaskCount) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(do_.getId());
        dto.setCode(do_.getCode());
        dto.setName(do_.getName());
        dto.setDescription(do_.getDescription());
        dto.setStatus(do_.getStatus());
        dto.setPriority(do_.getPriority());
        dto.setOwnerId(do_.getOwnerId());
        dto.setOwnerName(owner == null ? null : owner.getNickname());
        dto.setCreatedBy(do_.getCreatedBy());
        dto.setCreatedByName(createdBy == null ? null : createdBy.getNickname());
        dto.setCreatedByAvatar(createdBy == null ? null : createdBy.getAvatar());
        dto.setProjectManagerId(do_.getProjectManagerId());
        dto.setProjectManagerName(projectManager == null ? null : projectManager.getNickname());
        dto.setProjectManagerAvatar(projectManager == null ? null : projectManager.getAvatar());
        dto.setStartDate(do_.getStartDate());
        dto.setEndDate(do_.getEndDate());
        dto.setProgress(do_.getProgress() == null ? 0 : do_.getProgress());
        dto.setMemberCount(memberCount);
        dto.setTaskCount(taskCount);
        dto.setDoneTaskCount(doneTaskCount);
        dto.setCreatedAt(do_.getCreatedAt());
        dto.setUpdatedAt(do_.getUpdatedAt());
        return dto;
    }

    public static ProjectMemberDTO toMember(ProjectMemberDO do_, UserDO user) {
        ProjectMemberDTO dto = new ProjectMemberDTO();
        dto.setId(do_.getId());
        dto.setProjectId(do_.getProjectId());
        dto.setUserId(do_.getUserId());
        dto.setUsername(user == null ? null : user.getUsername());
        dto.setNickname(user == null ? null : user.getNickname());
        dto.setAvatar(user == null ? null : user.getAvatar());
        dto.setRole(do_.getRole());
        dto.setCreatedAt(do_.getCreatedAt());
        return dto;
    }

    public static ProjectTaskDTO toTask(ProjectTaskDO do_, UserDO assignee) {
        ProjectTaskDTO dto = new ProjectTaskDTO();
        dto.setId(do_.getId());
        dto.setProjectId(do_.getProjectId());
        dto.setNodeId(do_.getNodeId());
        dto.setParentId(do_.getParentId());
        dto.setTitle(do_.getTitle());
        dto.setDescription(do_.getDescription());
        dto.setDeliverable(do_.getDeliverable());
        dto.setStatus(do_.getStatus());
        dto.setPriority(do_.getPriority());
        dto.setAssigneeId(do_.getAssigneeId());
        dto.setAssigneeName(assignee == null ? null : assignee.getNickname());
        dto.setMilestoneId(do_.getMilestoneId());
        dto.setSort(do_.getSort());
        dto.setDueDate(do_.getDueDate());
        dto.setCreatedAt(do_.getCreatedAt());
        dto.setUpdatedAt(do_.getUpdatedAt());
        return dto;
    }

    public static ProjectMilestoneDTO toMilestone(ProjectMilestoneDO do_) {
        ProjectMilestoneDTO dto = new ProjectMilestoneDTO();
        dto.setId(do_.getId());
        dto.setProjectId(do_.getProjectId());
        dto.setTitle(do_.getTitle());
        dto.setDescription(do_.getDescription());
        dto.setDueDate(do_.getDueDate());
        dto.setStatus(do_.getStatus());
        dto.setTaskCount(0);
        dto.setDoneTaskCount(0);
        dto.setCreatedAt(do_.getCreatedAt());
        return dto;
    }

    public static ProjectCommentDTO toComment(ProjectCommentDO do_, UserDO user) {
        ProjectCommentDTO dto = new ProjectCommentDTO();
        dto.setId(do_.getId());
        dto.setProjectId(do_.getProjectId());
        dto.setTaskId(do_.getTaskId());
        dto.setContent(do_.getContent());
        dto.setUserId(do_.getUserId());
        dto.setUserNickname(user == null ? null : user.getNickname());
        dto.setCreatedAt(do_.getCreatedAt());
        return dto;
    }

    public static Map<Long, UserDO> userMap(List<UserDO> users) {
        return users.stream().collect(Collectors.toMap(UserDO::getId, Function.identity()));
    }
}
