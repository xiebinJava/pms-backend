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
        dto.setNameZh(do_.getNameZh());
        dto.setDisplayName(userDisplayName(do_));
        dto.setNickname(userDisplayName(do_));
        dto.setEmail(do_.getEmail());
        dto.setPhone(do_.getPhone());
        dto.setAvatar(do_.getAvatar());
        dto.setSystemRole(do_.getSystemRole());
        dto.setStatus(do_.getStatus());
        dto.setCreatedAt(do_.getCreatedAt());
        return dto;
    }

    public static String userDisplayName(UserDO user) {
        if (user == null) return null;
        String nameZh = firstNonBlank(user.getNameZh(), user.getNickname());
        String username = trimToNull(user.getUsername());
        String email = trimToNull(user.getEmail());
        boolean generatedUsername = username != null && username.matches("user-[a-fA-F0-9]{16}");
        if (nameZh != null && username != null && !generatedUsername) return nameZh + "（" + username + "）";
        if (nameZh != null) return nameZh;
        // Email is the stable identity fallback. The generated legacy username
        // exists only for schema/backwards compatibility and must not leak into
        // user-facing labels when no display names were supplied.
        if (username != null && !generatedUsername) return username;
        if (email != null) return email;
        return username;
    }

    private static String firstNonBlank(String first, String second) {
        return trimToNull(first) == null ? trimToNull(second) : trimToNull(first);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static List<UserDTO> toUsers(List<UserDO> list) {
        return list.stream().map(Convertors::toUser).collect(Collectors.toList());
    }

    public static ProjectDTO toProject(ProjectDO do_, UserDO owner, UserDO createdBy, UserDO projectManager,
                                       int memberCount, int taskCount, int doneTaskCount, int progress) {
        ProjectDTO dto = new ProjectDTO();
        dto.setId(do_.getId());
        dto.setVersion(do_.getVersion());
        dto.setCode(do_.getCode());
        dto.setName(do_.getName());
        dto.setDescription(do_.getDescription());
        dto.setStatus(do_.getStatus());
        dto.setPriority(do_.getPriority());
        dto.setProjectLevel(do_.getProjectLevel());
        dto.setOwnerId(do_.getOwnerId());
        dto.setOwnerName(userDisplayName(owner));
        dto.setCreatedBy(do_.getCreatedBy());
        dto.setCreatedByName(userDisplayName(createdBy));
        dto.setCreatedByAvatar(createdBy == null ? null : createdBy.getAvatar());
        dto.setProjectManagerId(do_.getProjectManagerId());
        dto.setProjectManagerName(userDisplayName(projectManager));
        dto.setProjectManagerAvatar(projectManager == null ? null : projectManager.getAvatar());
        dto.setOrgUnitId(do_.getOrgUnitId());
        dto.setStartDate(do_.getStartDate());
        dto.setEndDate(do_.getEndDate());
        dto.setProgress(progress);
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
        dto.setEmail(user == null ? null : user.getEmail());
        dto.setNickname(userDisplayName(user));
        dto.setDisplayName(userDisplayName(user));
        dto.setAvatar(user == null ? null : user.getAvatar());
        dto.setRole(do_.getRole());
        dto.setCreatedAt(do_.getCreatedAt());
        return dto;
    }

    public static ProjectTaskDTO toTask(ProjectTaskDO do_, UserDO assignee) {
        ProjectTaskDTO dto = new ProjectTaskDTO();
        dto.setId(do_.getId());
        dto.setVersion(do_.getVersion());
        dto.setProjectId(do_.getProjectId());
        dto.setNodeId(do_.getNodeId());
        dto.setParentId(do_.getParentId());
        dto.setTitle(do_.getTitle());
        dto.setDescription(do_.getDescription());
        dto.setDeliverable(do_.getDeliverable());
        dto.setStatus(do_.getStatus());
        dto.setPriority(do_.getPriority());
        dto.setAssigneeId(do_.getAssigneeId());
        dto.setAssigneeName(userDisplayName(assignee));
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
        dto.setUserNickname(userDisplayName(user));
        dto.setCreatedAt(do_.getCreatedAt());
        return dto;
    }

    public static Map<Long, UserDO> userMap(List<UserDO> users) {
        return users.stream().collect(Collectors.toMap(UserDO::getId, Function.identity()));
    }
}
