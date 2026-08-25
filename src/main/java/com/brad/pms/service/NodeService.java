package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目管理节点服务：节点初始化、列表、完成流转（完成当前节点自动解锁下一个）
 */
@Service
@RequiredArgsConstructor
public class NodeService {

    private final ProjectNodeMapper nodeMapper;
    private final ProjectMapper projectMapper;

    /**
     * 项目默认节点（参考项目管理全景图：9 个阶段节点）
     */
    private static final String[][] DEFAULT_NODES = {
            {"kickoff", "项目立项与启动", "判断项目是否值得投入，确认目标、范围与资源授权，提交立项材料并完成审批。",
                    "项目章程（目标、范围与成功标准）、项目阶段计划（WBS）",
                    "项目发起人、业务负责人、技术负责人、项目经理、审批人"},
            {"requirement", "需求澄清与范围基线", "澄清业务需求，汇总各方需求并去重，形成范围、优先级与验收基线，与业务确认做与不做。",
                    "需求基线、验收标准与追踪矩阵",
                    "产品负责人、业务代表、项目经理"},
            {"design", "方案设计、评审与决策", "验证产品与技术方案，完成关键方案决策，业务、产品、技术和测试共同评审。",
                    "业务与产品方案、技术方案与测试策略、评审结论与决策记录",
                    "产品负责人、项目经理、技术负责人、产品经理、测试负责人、业务代表"},
            {"plan", "计划、资源与风险基线", "建立进度、资源、质量与风险基线，排出里程碑、迭代与上线时间，拆解专题与任务并确认人员与依赖。",
                    "WBS 与项目计划、范围及优先级清单、资源及依赖清单、风险登记册",
                    "项目经理、技术负责人、产品负责人、测试负责人、运维负责人"},
            {"develop", "开发测试与项目控制", "按迭代完成开发、代码评审、测试与修复，定期更新进度，跟踪问题、风险与跨团队依赖。",
                    "可验证版本、进度/风险/变更记录",
                    "项目经理、开发团队、测试团队、技术负责人、运维负责人"},
            {"acceptance", "业务验收与缺陷闭环", "业务人员按照真实场景完成验收，整理验收用例，准备 UAT 环境，问题记录、分派并跟踪修复。",
                    "测试报告与缺陷清单、缺陷关闭及遗留项清单、业务验收结论",
                    "业务负责人、产品负责人、项目经理、测试负责人"},
            {"release", "发布决策与运营交接", "完成上线决策与运营交接，准备上线 Checklist 与审批，检查发布包、配置与回滚预案，确认监控、告警与值守。",
                    "发布记录与回滚预案、可交付版本与代码库、运维交接清单",
                    "项目经理、技术负责人、运维负责人、业务负责人"},
            {"review", "价值验证与项目复盘", "对照立项目标验证项目价值与系统使用情况，复盘延期、返工与沟通问题，回顾工期、质量、成本与团队投入。",
                    "项目价值评估、项目复盘报告",
                    "项目经理、业务负责人、技术负责人、核心项目成员"},
            {"knowledge", "知识沉淀与标准改进", "沉淀经验与标准，更新项目管理流程和常用检查项，整理模板、清单与案例，归档项目文档并清理过期内容。",
                    "项目知识库、标准模板与案例、改进行动清单、流程及检查清单更新",
                    "项目经理、PMO、技术负责人、知识资产负责人"},
    };

    @Transactional
    public void initDefault(Long projectId) {
        for (int i = 0; i < DEFAULT_NODES.length; i++) {
            String[] def = DEFAULT_NODES[i];
            ProjectNodeDO node = new ProjectNodeDO();
            node.setProjectId(projectId);
            node.setNodeKey(def[0]);
            node.setName(def[1]);
            node.setDescription(def[2]);
            node.setDeliverable(def[3]);
            node.setRoles(def[4]);
            node.setStatus(i == 0 ? 1 : 0);
            node.setSort(i);
            nodeMapper.insert(node);
        }
    }

    public List<ProjectNodeDTO> list(Long projectId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                        .eq(ProjectNodeDO::getProjectId, projectId)
                        .orderByAsc(ProjectNodeDO::getSort))
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    /**
     * 完成当前节点：标记已完成，自动解锁下一个节点；全部完成时项目收尾。
     */
    @Transactional
    public List<ProjectNodeDTO> complete(Long projectId, Long nodeId) {
        ProjectNodeDO node = nodeMapper.selectById(nodeId);
        if (node == null || !node.getProjectId().equals(projectId)) {
            throw BusinessException.error("节点不存在");
        }
        if (node.getStatus() == 2) {
            throw BusinessException.error("该节点已完成");
        }
        if (node.getStatus() != 1) {
            throw BusinessException.error("当前节点尚未解锁");
        }
        node.setStatus(2);
        nodeMapper.updateById(node);

        ProjectNodeDO next = nodeMapper.selectOne(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getStatus, 0)
                .orderByAsc(ProjectNodeDO::getSort)
                .last("LIMIT 1"));
        if (next != null) {
            next.setStatus(1);
            nodeMapper.updateById(next);
            refreshProgress(projectId);
        } else {
            ProjectDO project = projectMapper.selectById(projectId);
            if (project != null) {
                project.setStatus(2);
                project.setProgress(100);
                projectMapper.updateById(project);
            }
        }
        return list(projectId);
    }

    /**
     * 回滚到指定节点：指定节点设为进行中，之前节点标记完成，之后节点恢复待开始。
     */
    @Transactional
    public List<ProjectNodeDTO> rollback(Long projectId, Long nodeId) {
        ProjectNodeDO target = nodeMapper.selectById(nodeId);
        if (target == null || !target.getProjectId().equals(projectId)) {
            throw BusinessException.error("节点不存在");
        }
        List<ProjectNodeDO> nodes = nodeMapper.selectList(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .orderByAsc(ProjectNodeDO::getSort));
        for (ProjectNodeDO node : nodes) {
            int nextStatus = node.getSort() < target.getSort() ? 2
                    : node.getId().equals(target.getId()) ? 1 : 0;
            if (!Integer.valueOf(nextStatus).equals(node.getStatus())) {
                node.setStatus(nextStatus);
                nodeMapper.updateById(node);
            }
        }

        ProjectDO project = projectMapper.selectById(projectId);
        if (project != null) {
            project.setStatus(1);
            projectMapper.updateById(project);
        }
        refreshProgress(projectId);
        return list(projectId);
    }

    private void refreshProgress(Long projectId) {
        ProjectDO project = projectMapper.selectById(projectId);
        if (project == null || project.getStatus() == 2) {
            return;
        }
        Integer total = nodeMapper.selectCount(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId));
        Integer done = nodeMapper.selectCount(new LambdaQueryWrapper<ProjectNodeDO>()
                .eq(ProjectNodeDO::getProjectId, projectId)
                .eq(ProjectNodeDO::getStatus, 2));
        int progress = total == null || total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        project.setProgress(progress);
        projectMapper.updateById(project);
    }

    private ProjectNodeDTO toDTO(ProjectNodeDO node) {
        ProjectNodeDTO dto = new ProjectNodeDTO();
        dto.setId(node.getId());
        dto.setProjectId(node.getProjectId());
        dto.setNodeKey(node.getNodeKey());
        dto.setName(node.getName());
        dto.setDescription(node.getDescription());
        dto.setDeliverable(node.getDeliverable());
        dto.setRoles(node.getRoles());
        dto.setStatus(node.getStatus());
        dto.setSort(node.getSort());
        dto.setCreatedAt(node.getCreatedAt());
        return dto;
    }
}
