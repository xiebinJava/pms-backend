package com.brad.pms.service;

import com.brad.pms.common.enums.DevelopmentAssignmentType;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.DevelopmentItemTaskDO;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.DevelopmentItemWorkflowNodeDO;
import com.brad.pms.entity.ProjectMemberAssignmentRefDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowNodeMapper;
import com.brad.pms.mapper.ProjectMemberAssignmentRefMapper;
import com.brad.pms.mapper.ProjectMemberAutoManagedMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.workflow.DevelopmentItemType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Keeps project membership in sync with development-item assignments. */
@Service
@RequiredArgsConstructor
public class ProjectMemberAssignmentService {

    private final ProjectMemberAssignmentRefMapper refMapper;
    private final ProjectMemberAutoManagedMapper autoManagedMapper;
    private final MemberService memberService;
    private final UserService userService;
    private final DevelopmentItemWorkflowMapper workflowMapper;
    private final DevelopmentItemWorkflowNodeMapper workflowNodeMapper;
    private final DevelopmentItemTaskMapper taskMapper;
    private final ProjectNodeDevelopmentTopicMapper topicMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;

    @Transactional
    public void replaceAssignment(Long projectId, DevelopmentItemType itemType, Long itemId,
                                  DevelopmentAssignmentType assignmentType, Long assignmentId, Long userId) {
        if (projectId == null) return;
        requireKey(itemType, itemId, assignmentType, assignmentId);
        if (userId != null) userService.requireActiveUser(userId);
        ProjectMemberAssignmentRefDO current = refMapper.selectByKey(projectId, itemType.name(), itemId,
                assignmentType.name(), assignmentId);
        if (Objects.equals(current == null ? null : current.getUserId(), userId)) {
            if (userId != null) ensureMember(projectId, userId);
            return;
        }
        if (current != null) {
            refMapper.deleteByKey(projectId, itemType.name(), itemId, assignmentType.name(), assignmentId);
            cleanup(projectId, current.getUserId());
        }
        if (userId == null) return;
        ensureMember(projectId, userId);
        refMapper.upsert(ref(projectId, itemType, itemId, assignmentType, assignmentId, userId));
    }

    @Transactional
    public void releaseAssignment(Long projectId, DevelopmentItemType itemType, Long itemId,
                                  DevelopmentAssignmentType assignmentType, Long assignmentId) {
        if (projectId == null) return;
        requireKey(itemType, itemId, assignmentType, assignmentId);
        ProjectMemberAssignmentRefDO current = refMapper.selectByKey(projectId, itemType.name(), itemId,
                assignmentType.name(), assignmentId);
        if (current == null) return;
        refMapper.deleteByKey(projectId, itemType.name(), itemId, assignmentType.name(), assignmentId);
        cleanup(projectId, current.getUserId());
    }

    /** Rebuilds all persisted assignment references for an item while moving its project context. */
    @Transactional
    public void synchronizeItemAssignments(Long sourceProjectId, Long targetProjectId,
                                           DevelopmentItemType itemType, Long itemId) {
        requireItem(itemType, itemId);
        List<Assignment> assignments = collectAssignments(itemType, itemId);
        if (targetProjectId != null) {
            Map<AssignmentKey, ProjectMemberAssignmentRefDO> existing = safeList(
                    sourceProjectId != null && Objects.equals(sourceProjectId, targetProjectId)
                            ? refMapper.selectByItemForUpdate(targetProjectId, itemType.name(), itemId)
                            : refMapper.selectByItemForUpdate(targetProjectId, itemType.name(), itemId))
                    .stream().collect(Collectors.toMap(this::keyOf, ref -> ref, (left, right) -> left));
            Set<AssignmentKey> desired = new HashSet<>();
            for (Assignment assignment : assignments) {
                desired.add(assignment.key());
                ProjectMemberAssignmentRefDO previous = existing.get(assignment.key());
                if (previous != null && !Objects.equals(previous.getUserId(), assignment.userId())) {
                    refMapper.deleteByKey(targetProjectId, previous.getItemType(), previous.getItemId(),
                            previous.getAssignmentType(), previous.getAssignmentId());
                    cleanup(targetProjectId, previous.getUserId());
                }
                ensureMemberIfActive(targetProjectId, assignment.userId());
                refMapper.upsert(ref(targetProjectId, assignment));
            }
            existing.values().stream()
                    .filter(ref -> !desired.contains(keyOf(ref)))
                    .forEach(ref -> {
                        refMapper.deleteByKey(targetProjectId, ref.getItemType(), ref.getItemId(),
                                ref.getAssignmentType(), ref.getAssignmentId());
                        cleanup(targetProjectId, ref.getUserId());
                    });
        }
        if (sourceProjectId != null && !Objects.equals(sourceProjectId, targetProjectId)) {
            releaseItem(sourceProjectId, itemType, itemId);
        }
    }

    private void releaseItem(Long projectId, DevelopmentItemType itemType, Long itemId) {
        for (ProjectMemberAssignmentRefDO ref : safeList(
                refMapper.selectByItemForUpdate(projectId, itemType.name(), itemId))) {
            refMapper.deleteByKey(projectId, ref.getItemType(), ref.getItemId(),
                    ref.getAssignmentType(), ref.getAssignmentId());
            cleanup(projectId, ref.getUserId());
        }
    }

    private List<Assignment> collectAssignments(DevelopmentItemType itemType, Long itemId) {
        List<Assignment> result = new ArrayList<>();
        if (itemType == DevelopmentItemType.TOPIC) {
            ProjectNodeDevelopmentTopicDO topic = topicMapper.selectById(itemId);
            if (topic != null && topic.getOwnerId() != null) {
                result.add(new Assignment(new AssignmentKey(itemType, itemId,
                        DevelopmentAssignmentType.TOPIC_OWNER, itemId), topic.getOwnerId()));
            }
        } else {
            ProjectNodeDevelopmentStoryDO story = storyMapper.selectById(itemId);
            if (story != null && story.getOwnerId() != null) {
                result.add(new Assignment(new AssignmentKey(itemType, itemId,
                        DevelopmentAssignmentType.STORY_OWNER, itemId), story.getOwnerId()));
            }
        }
        DevelopmentItemWorkflowDO workflow = workflowMapper.selectByItem(itemType.name(), itemId);
        if (workflow == null || workflow.getId() == null) return result;
        List<DevelopmentItemWorkflowNodeDO> nodes = safeList(
                workflowNodeMapper.selectByWorkflowIdsForUpdate(List.of(workflow.getId())));
        for (DevelopmentItemWorkflowNodeDO node : nodes) {
            if (node.getOwnerId() != null) {
                result.add(new Assignment(new AssignmentKey(itemType, itemId,
                        DevelopmentAssignmentType.WORKFLOW_NODE_OWNER, node.getId()), node.getOwnerId()));
            }
        }
        for (DevelopmentItemTaskDO task : safeList(taskMapper.selectByWorkflowIdsForUpdate(List.of(workflow.getId())))) {
            if (!Boolean.TRUE.equals(task.getDeleted()) && task.getAssigneeId() != null) {
                result.add(new Assignment(new AssignmentKey(itemType, itemId,
                        DevelopmentAssignmentType.TASK_ASSIGNEE, task.getId()), task.getAssigneeId()));
            }
        }
        return result;
    }

    private void ensureMemberIfActive(Long projectId, Long userId) {
        if (userId == null) return;
        try {
            userService.requireActiveUser(userId);
        } catch (BusinessException historicalAssignment) {
            // Existing inactive/deleted accounts remain visible as historical refs but are never added again.
            return;
        }
        ensureMember(projectId, userId);
    }

    private void ensureMember(Long projectId, Long userId) {
        MemberService.EnsureMemberResult ensured = memberService.ensureMemberForAssignment(projectId, userId);
        if (ensured.inserted()) autoManagedMapper.insertIgnore(projectId, userId);
    }

    private void cleanup(Long projectId, Long userId) {
        if (userId == null) return;
        if (!safeList(refMapper.selectByProjectUserForUpdate(projectId, userId)).isEmpty()) return;
        if (autoManagedMapper.selectForUpdate(projectId, userId) == null) return;
        autoManagedMapper.delete(projectId, userId);
        memberService.removeAutoManagedMember(projectId, userId);
    }

    private ProjectMemberAssignmentRefDO ref(Long projectId, DevelopmentItemType itemType, Long itemId,
                                             DevelopmentAssignmentType assignmentType, Long assignmentId,
                                             Long userId) {
        ProjectMemberAssignmentRefDO ref = new ProjectMemberAssignmentRefDO();
        ref.setProjectId(projectId);
        ref.setItemType(itemType.name());
        ref.setItemId(itemId);
        ref.setAssignmentType(assignmentType.name());
        ref.setAssignmentId(assignmentId);
        ref.setUserId(userId);
        return ref;
    }

    private ProjectMemberAssignmentRefDO ref(Long projectId, Assignment assignment) {
        AssignmentKey key = assignment.key();
        return ref(projectId, key.itemType(), key.itemId(), key.assignmentType(), key.assignmentId(), assignment.userId());
    }

    private AssignmentKey keyOf(ProjectMemberAssignmentRefDO ref) {
        return new AssignmentKey(DevelopmentItemType.from(ref.getItemType()), ref.getItemId(),
                DevelopmentAssignmentType.valueOf(ref.getAssignmentType()), ref.getAssignmentId());
    }

    private void requireKey(DevelopmentItemType itemType, Long itemId,
                            DevelopmentAssignmentType assignmentType, Long assignmentId) {
        requireItem(itemType, itemId);
        if (assignmentType == null || assignmentId == null) {
            throw BusinessException.error("研发事项分配标识不能为空");
        }
    }

    private void requireItem(DevelopmentItemType itemType, Long itemId) {
        if (itemType == null || itemId == null) throw BusinessException.error("研发事项标识不能为空");
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    private record Assignment(AssignmentKey key, Long userId) { }

    private record AssignmentKey(DevelopmentItemType itemType, Long itemId,
                                 DevelopmentAssignmentType assignmentType, Long assignmentId) { }
}
