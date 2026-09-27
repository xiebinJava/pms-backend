package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeIterationPlanDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeIterationPlanMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Objects;

/** Write boundary for iteration plans; iteration plans deliberately have no workflow instance. */
@Service
@RequiredArgsConstructor
public class IterationPlanCommandService {

    private final ProjectNodeIterationPlanMapper iterationPlanMapper;
    private final ProjectNodeDevelopmentStoryMapper storyMapper;
    private final ProjectNodeMapper nodeMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;

    @Transactional
    public Long create(Long projectId, Long nodeId, NodeIterationPlanCmd cmd) {
        ProjectNodeDO node = requireNode(projectId, nodeId, "创建迭代计划");
        validate(cmd);
        ProjectNodeIterationPlanDO plan = new ProjectNodeIterationPlanDO();
        plan.setProjectId(projectId);
        plan.setNodeId(node.getId());
        plan.setCreatedBy(UserContext.userIdOrNull());
        apply(plan, cmd, cmd.getSort() == null ? 0 : cmd.getSort());
        if (iterationPlanMapper.insert(plan) != 1 || plan.getId() == null) {
            throw BusinessException.conflict("迭代计划创建失败，请重试");
        }
        return plan.getId();
    }

    @Transactional
    public void update(Long id, NodeIterationPlanCmd cmd) {
        if (id == null) throw BusinessException.notFound("迭代计划不存在");
        ProjectNodeIterationPlanDO plan = iterationPlanMapper.selectById(id);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        permissionService.requireProjectManageable(plan.getProjectId(), "编辑迭代计划");
        validate(cmd);
        apply(plan, cmd, cmd.getSort() == null ? safeSort(plan.getSort()) : cmd.getSort());
        if (iterationPlanMapper.updateById(plan) != 1) {
            throw BusinessException.conflict("迭代计划已被其他人修改，请刷新后重试");
        }
    }

    @Transactional
    public void addStory(Long planId, Long storyId) {
        ProjectNodeIterationPlanDO plan = requirePlan(planId, "关联故事到迭代计划");
        if (storyId == null) throw BusinessException.error("故事不能为空");
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectByIdForUpdate(storyId);
        if (story == null) throw BusinessException.notFound("故事不存在");
        if (!Objects.equals(plan.getProjectId(), story.getProjectId())) {
            throw BusinessException.error("故事和迭代计划必须属于同一个项目");
        }
        story.setIterationPlanId(plan.getId());
        if (storyMapper.updateById(story) != 1) throw BusinessException.conflict("故事关联迭代计划失败，请重试");
    }

    @Transactional
    public void removeStory(Long planId, Long storyId) {
        ProjectNodeIterationPlanDO plan = requirePlan(planId, "解除迭代计划故事关联");
        if (storyId == null) throw BusinessException.error("故事不能为空");
        ProjectNodeDevelopmentStoryDO story = storyMapper.selectByIdForUpdate(storyId);
        if (story == null || !Objects.equals(plan.getProjectId(), story.getProjectId())
                || !Objects.equals(plan.getId(), story.getIterationPlanId())) {
            throw BusinessException.notFound("故事不属于当前迭代计划");
        }
        story.setIterationPlanId(null);
        if (storyMapper.updateById(story) != 1) throw BusinessException.conflict("解除故事关联失败，请重试");
    }

    private ProjectNodeIterationPlanDO requirePlan(Long id, String action) {
        if (id == null) throw BusinessException.notFound("迭代计划不存在");
        ProjectNodeIterationPlanDO plan = iterationPlanMapper.selectById(id);
        if (plan == null) throw BusinessException.notFound("迭代计划不存在");
        permissionService.requireProjectManageable(plan.getProjectId(), action);
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
        if (cmd.getStatus() != null && cmd.getStatus().length() > 20) throw BusinessException.error("迭代计划状态不合法");
    }

    private void apply(ProjectNodeIterationPlanDO plan, NodeIterationPlanCmd cmd, int sort) {
        plan.setName(cmd.getName());
        plan.setOwnerId(cmd.getOwnerId());
        plan.setGoal(cmd.getGoal());
        plan.setStatus(cmd.getStatus());
        plan.setStartDate(cmd.getStartDate());
        plan.setDueDate(cmd.getDueDate());
        plan.setSort(sort);
    }

    private int safeSort(Integer sort) { return sort == null ? 0 : sort; }
}
