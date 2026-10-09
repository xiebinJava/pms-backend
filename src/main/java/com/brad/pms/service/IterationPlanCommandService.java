package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.IterationPlanStatusUpdateCmd;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Write boundary for iteration plans; iteration plans deliberately have no workflow instance. */
@Service
@RequiredArgsConstructor
public class IterationPlanCommandService {

    private static final Set<String> ITERATION_STATUSES = Set.of("PLANNED", "IN_PROGRESS", "DONE", "PAUSED");

    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final SystemVersionReferenceService systemVersionReferenceService;
    private final IterationPlanSystemService iterationPlanSystemService;

    @Transactional
    public Long create(Long projectId, Long nodeId, NodeIterationPlanCmd cmd) {
        ProjectNodeDO node = projectId == null && nodeId == null
                ? null : requireNode(projectId, nodeId, "创建迭代计划");
        validate(cmd);
        Long systemVersionId = systemVersionReferenceService.resolveForSave(cmd, null);
        systemVersionReferenceService.validateForSave(systemVersionId, null);
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setProjectId(projectId);
        plan.setNodeId(node == null ? null : node.getId());
        Long systemId = iterationPlanSystemService.resolveForSave(plan, cmd, true);
        systemVersionReferenceService.validateSystem(systemVersionId, systemId);
        plan.setCreatedBy(UserContext.userIdOrNull());
        apply(plan, cmd, cmd.getSort() == null ? 0 : cmd.getSort(), systemVersionId, systemId);
        if (iterationPlanMapper.insert(plan) != 1 || plan.getId() == null) {
            throw BusinessException.conflict("迭代计划创建失败，请重试");
        }
        return plan.getId();
    }

    /** Resolve the current project iteration workbench for standalone creation. */
    @Transactional
    public Long createForProject(Long projectId, NodeIterationPlanCmd cmd) {
        ProjectNodeDO node = permissionService.findNodeWithComponent(projectId, WorkflowComponentKey.PLAN_RESOURCE_RISK);
        if (node == null) throw BusinessException.error("项目未配置迭代计划节点");
        return create(projectId, node.getId(), cmd);
    }

    @Transactional
    public void update(Long id, NodeIterationPlanCmd cmd) {
        if (id == null) throw BusinessException.notFound("迭代计划不存在");
        ProjectNodeIterationPlanDO plan = iterationPlanMapper.selectByIdForUpdate(id);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        requireProjectManageableIfBound(plan.getProjectId(), "编辑迭代计划");
        validate(cmd);
        Long systemVersionId = systemVersionReferenceService.resolveForSave(cmd, plan.getSystemVersionId());
        systemVersionReferenceService.validateForSave(systemVersionId, plan.getSystemVersionId());
        Long systemId = iterationPlanSystemService.resolveForSave(plan, cmd, false);
        systemVersionReferenceService.validateSystem(systemVersionId, systemId);
        apply(plan, cmd, cmd.getSort() == null ? safeSort(plan.getSort()) : cmd.getSort(), systemVersionId, systemId);
        if (iterationPlanMapper.updateById(plan) != 1) {
            throw BusinessException.conflict("迭代计划已被其他人修改，请刷新后重试");
        }
    }

    @Transactional
    public void updateStatus(Long id, IterationPlanStatusUpdateCmd cmd) {
        if (id == null) throw BusinessException.notFound("迭代计划不存在");
        ProjectNodeIterationPlanDO plan = iterationPlanMapper.selectByIdForUpdate(id);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        requireProjectManageableIfBound(plan.getProjectId(), "更新迭代计划状态");
        String status = normalizeStatus(cmd == null ? null : cmd.getStatus());
        if (status == null || !ITERATION_STATUSES.contains(status)) throw BusinessException.error("迭代计划状态不合法");
        if (iterationPlanMapper.updateStatus(id, status) != 1) {
            throw BusinessException.conflict("迭代计划状态已被其他人修改，请刷新后重试");
        }
    }

    @Transactional
    public void delete(Long id) {
        if (id == null) throw BusinessException.notFound("迭代计划不存在");
        ProjectNodeIterationPlanDO plan = iterationPlanMapper.selectByIdForUpdate(id);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        requireProjectManageableIfBound(plan.getProjectId(), "删除迭代计划");

        long storyReferences = storyMapper.selectCount(new LambdaQueryWrapper<ProjectNodeDevelopmentStoryDO>()
                .eq(ProjectNodeDevelopmentStoryDO::getIterationPlanId, id));
        long taskReferences = taskMapper.selectCount(new LambdaQueryWrapper<ProjectTaskDO>()
                .eq(ProjectTaskDO::getIterationPlanId, id));
        if (storyReferences > 0 || taskReferences > 0) {
            throw BusinessException.error("迭代计划已被故事或任务使用，请先解除关联后再删除");
        }
        if (iterationPlanMapper.deleteById(id) != 1) {
            throw BusinessException.conflict("迭代计划已被其他人修改，请刷新后重试");
        }
    }

    @Transactional
    public void addStory(Long planId, Long storyId) {
        if (storyId == null) throw BusinessException.error("故事不能为空");
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectByIdForUpdate(storyId);
        if (story == null) throw BusinessException.notFound("故事不存在");
        // Match workflow edits: lock the story before the iteration, avoiding inverse lock order.
        ProjectNodeIterationPlanDO plan = requirePlan(planId, "关联故事到迭代计划");
        requireProjectManageableIfBound(story.getProjectId(), "关联故事到迭代计划");
        iterationPlanSystemService.bindStory(plan, story);
        story.setIterationPlanId(plan.getId());
        if (storyMapper.updateById(story) != 1) throw BusinessException.conflict("故事关联迭代计划失败，请重试");
    }

    @Transactional
    public void removeStory(Long planId, Long storyId) {
        if (storyId == null) throw BusinessException.error("故事不能为空");
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectByIdForUpdate(storyId);
        ProjectNodeIterationPlanDO plan = requirePlan(planId, "解除迭代计划故事关联");
        if (story == null || !Objects.equals(plan.getId(), story.getIterationPlanId())) {
            throw BusinessException.notFound("故事不属于当前迭代计划");
        }
        requireProjectManageableIfBound(story.getProjectId(), "解除迭代计划故事关联");
        story.setIterationPlanId(null);
        if (storyMapper.updateIterationPlan(story.getId(), null) != 1) throw BusinessException.conflict("解除故事关联失败，请重试");
    }

    private ProjectNodeIterationPlanDO requirePlan(Long id, String action) {
        if (id == null) throw BusinessException.notFound("迭代计划不存在");
        ProjectNodeIterationPlanDO plan = iterationPlanMapper.selectByIdForUpdate(id);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        requireProjectManageableIfBound(plan.getProjectId(), action);
        return plan;
    }

    private ProjectNodeDO requireNode(Long projectId, Long nodeId, String action) {
        if (projectId == null || nodeId == null) throw BusinessException.error("项目和项目节点不能为空");
        permissionService.requireProjectManageable(projectId, action);
        ProjectNodeDO node = nodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getProjectId(), projectId)) {
            throw BusinessException.notFound("项目节点不存在");
        }
        return node;
    }

    private void validate(NodeIterationPlanCmd cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.getName())) throw BusinessException.error("迭代计划名称不能为空");
        cmd.setName(cmd.getName().trim());
        if (cmd.getOwnerId() != null) userService.requireActiveUser(cmd.getOwnerId());
        if (cmd.getStartDate() != null && cmd.getDueDate() != null
                && cmd.getStartDate().isAfter(cmd.getDueDate())) {
            throw BusinessException.error("迭代计划开始日期不能晚于结束日期");
        }
        if (cmd.getSort() != null && cmd.getSort() < 0) throw BusinessException.error("迭代计划排序不合法");
        if (cmd.getStatus() != null) {
            cmd.setStatus(normalizeStatus(cmd.getStatus()));
            if (!ITERATION_STATUSES.contains(cmd.getStatus())) throw BusinessException.error("迭代计划状态不合法");
        }
    }

    private String normalizeStatus(String status) {
        return status == null ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    private void apply(ProjectNodeIterationPlanDO plan, NodeIterationPlanCmd cmd, int sort,
                       Long systemVersionId, Long systemId) {
        plan.setName(cmd.getName());
        plan.setSystemId(systemId);
        plan.setSystemVersionId(systemVersionId);
        plan.setOwnerId(cmd.getOwnerId());
        plan.setGoal(cmd.getGoal());
        plan.setStatus(cmd.getStatus());
        plan.setStartDate(cmd.getStartDate());
        plan.setDueDate(cmd.getDueDate());
        plan.setSort(sort);
    }

    private int safeSort(Integer sort) { return sort == null ? 0 : sort; }

    private void requireProjectManageableIfBound(Long projectId, String action) {
        if (projectId != null) permissionService.requireProjectManageable(projectId, action);
    }
}
