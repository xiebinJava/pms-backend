package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.MilestoneCreateCmd;
import com.brad.pms.dto.request.MilestoneUpdateCmd;
import com.brad.pms.dto.response.ProjectMilestoneDTO;
import com.brad.pms.entity.ProjectMilestoneDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.mapper.ProjectMilestoneMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectPermissionService permissionService;

    public List<ProjectMilestoneDTO> listByProject(Long projectId) {
        permissionService.requireProject(projectId);
        List<ProjectMilestoneDO> milestones = milestoneMapper.selectList(
                new LambdaQueryWrapper<ProjectMilestoneDO>()
                        .eq(ProjectMilestoneDO::getProjectId, projectId)
                        .orderByAsc(ProjectMilestoneDO::getDueDate));

        List<ProjectTaskDO> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<ProjectTaskDO>()
                        .eq(ProjectTaskDO::getProjectId, projectId)
                        .isNotNull(ProjectTaskDO::getMilestoneId));

        Map<Long, List<ProjectTaskDO>> byMilestone = tasks.stream()
                .collect(Collectors.groupingBy(ProjectTaskDO::getMilestoneId));

        return milestones.stream().map(m -> {
            ProjectMilestoneDTO dto = Convertors.toMilestone(m);
            List<ProjectTaskDO> msTasks = byMilestone.getOrDefault(m.getId(), List.of());
            dto.setTaskCount(msTasks.size());
            dto.setDoneTaskCount((int) msTasks.stream().filter(t -> t.getStatus() == 2).count());
            return dto;
        }).collect(Collectors.toList());
    }

    public ProjectMilestoneDTO create(Long projectId, MilestoneCreateCmd cmd) {
        permissionService.requireManageableProject(projectId, "创建里程碑");
        ProjectMilestoneDO milestone = new ProjectMilestoneDO();
        milestone.setProjectId(projectId);
        milestone.setTitle(cmd.getTitle());
        milestone.setDescription(cmd.getDescription());
        milestone.setDueDate(cmd.getDueDate());
        milestone.setStatus(cmd.getStatus());
        milestoneMapper.insert(milestone);
        return Convertors.toMilestone(milestone);
    }

    public ProjectMilestoneDTO update(Long id, MilestoneUpdateCmd cmd) {
        ProjectMilestoneDO milestone = requireMilestone(id);
        permissionService.requireManageableProject(milestone.getProjectId(), "编辑里程碑");
        if (StringUtils.hasText(cmd.getTitle())) milestone.setTitle(cmd.getTitle());
        if (cmd.getDescription() != null) milestone.setDescription(cmd.getDescription());
        if (cmd.getDueDate() != null) milestone.setDueDate(cmd.getDueDate());
        if (cmd.getStatus() != null) milestone.setStatus(cmd.getStatus());
        milestoneMapper.updateById(milestone);
        return Convertors.toMilestone(milestone);
    }

    public void delete(Long id) {
        ProjectMilestoneDO milestone = requireMilestone(id);
        permissionService.requireManageableProject(milestone.getProjectId(), "删除里程碑");
        milestoneMapper.deleteById(id);
    }

    private ProjectMilestoneDO requireMilestone(Long id) {
        ProjectMilestoneDO milestone = milestoneMapper.selectById(id);
        if (milestone == null) {
            throw BusinessException.error("里程碑不存在");
        }
        return milestone;
    }
}
