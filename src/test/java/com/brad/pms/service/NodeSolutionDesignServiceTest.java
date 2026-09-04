package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeSolutionDecisionConfirmCmd;
import com.brad.pms.dto.request.NodeSolutionPackageUpdateCmd;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeSolutionDecisionDO;
import com.brad.pms.entity.ProjectNodeSolutionPackageDO;
import com.brad.pms.entity.ProjectNodeSolutionReviewDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import com.brad.pms.mapper.ProjectNodeSolutionPackageMapper;
import com.brad.pms.mapper.ProjectNodeSolutionReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeSolutionDesignServiceTest {

    @Mock ProjectNodeSolutionPackageMapper packageMapper;
    @Mock ProjectNodeSolutionReviewMapper reviewMapper;
    @Mock ProjectNodeSolutionDecisionMapper decisionMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock NodeRequirementScopeService requirementScopeService;

    @InjectMocks NodeSolutionDesignService service;

    @Test
    void rejectsNonDesignNodes() {
        ProjectNodeDO node = node("requirement");
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);

        assertThatThrownBy(() -> service.get(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方案设计");
    }

    @Test
    void savesTrimmedDraftPackage() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "保存方案包")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(null);
        when(packageMapper.insert(any(ProjectNodeSolutionPackageDO.class))).thenReturn(1);

        service.saveDraft(1L, 10L, packageCmd(" v1.0 "));

        ArgumentCaptor<ProjectNodeSolutionPackageDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionPackageDO.class);
        verify(packageMapper).insert(captor.capture());
        assertThat(captor.getValue().getPackageVersion()).isEqualTo("v1.0");
        assertThat(captor.getValue().getProductSolution()).isEqualTo("产品方案");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void rejectsSubmittingAnIncompletePackage() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionPackageDO pkg = packageEntity();
        pkg.setTechnicalSolution("");
        when(permissionService.requireManageableNode(1L, 10L, "提交方案包")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(pkg);

        assertThatThrownBy(() -> service.submitPackage(1L, 10L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("技术方案");
        verify(packageMapper, never()).updateById(any(ProjectNodeSolutionPackageDO.class));
    }

    @Test
    void rejectsSubmittingWithoutAConfirmedRequirementBaseline() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "提交方案包")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        ProjectNodeDO requirementNode = node("requirement");
        requirementNode.setId(11L);
        when(nodeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(requirementNode);
        NodeRequirementScopeDTO baseline = new NodeRequirementScopeDTO();
        baseline.setBaselineStatus(0);
        when(requirementScopeService.get(1L, 11L)).thenReturn(baseline);

        assertThatThrownBy(() -> service.submitPackage(1L, 10L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需求范围基线");
    }

    @Test
    void rejectsUnknownReviewType() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "完成方案评审")).thenReturn(node);

        assertThatThrownBy(() -> service.completeReview(1L, 10L, "ARCHITECTURE", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评审类型");
    }

    @Test
    void mapsConcurrentReviewCreationToConflict() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "完成方案评审")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(ProjectNodeSolutionReviewDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.completeReview(1L, 10L, "TECHNICAL", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他人完成");
    }

    @Test
    void confirmsConditionalDecisionOnlyAfterAllReviewsPass() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "确认方案决策")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                review("BUSINESS_PRODUCT", "PASSED"),
                review("TECHNICAL", "PASSED"),
                review("TEST_RELEASE", "PASSED")));
        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(decisionMapper.insert(any(ProjectNodeSolutionDecisionDO.class))).thenReturn(1);

        NodeSolutionDecisionConfirmCmd cmd = new NodeSolutionDecisionConfirmCmd();
        cmd.setResult("CONDITIONAL_PASS");
        cmd.setReason("满足设计原则");
        cmd.setConditions("上线前完成监控配置");

        var result = service.confirmDecision(1L, 10L, cmd);

        ArgumentCaptor<ProjectNodeSolutionDecisionDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionDecisionDO.class);
        verify(decisionMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
        assertThat(captor.getValue().getConfirmedAt()).isNotNull();
    }

    @Test
    void mapsConcurrentDecisionCreationToConflict() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "确认方案决策")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                review("BUSINESS_PRODUCT", "PASSED"),
                review("TECHNICAL", "PASSED"),
                review("TEST_RELEASE", "PASSED")));
        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(decisionMapper.insert(any(ProjectNodeSolutionDecisionDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.confirmDecision(1L, 10L, decisionCmd("PASS", "方案可行", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他人确认");
    }

    @Test
    void rejectsDecisionWhenARequiredReviewIsPending() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "确认方案决策")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                review("BUSINESS_PRODUCT", "PASSED"),
                review("TECHNICAL", "PENDING"),
                review("TEST_RELEASE", "PASSED")));

        NodeSolutionDecisionConfirmCmd cmd = decisionCmd("PASS", "方案可行", null);

        assertThatThrownBy(() -> service.confirmDecision(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("三类必要评审");
    }

    @Test
    void reopensAConfirmedDecisionAndClearsItsConfirmationTime() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionDecisionDO decision = new ProjectNodeSolutionDecisionDO();
        decision.setStatus("CONFIRMED");
        decision.setVersion(2);
        when(permissionService.requireManageableNode(1L, 10L, "重新打开方案决策")).thenReturn(node);
        when(decisionMapper.selectOne(any())).thenReturn(decision);
        when(decisionMapper.updateById(any(ProjectNodeSolutionDecisionDO.class))).thenReturn(1);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        var result = service.reopenDecision(1L, 10L);

        assertThat(result.getDecision().getStatus()).isEqualTo("DRAFT");
        assertThat(result.getDecision().getConfirmedAt()).isNull();
        verify(decisionMapper).updateById(decision);
    }

    @Test
    void enforcesConfirmedDecisionAsNodeCompletionGate() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(decisionMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.requireConfirmed(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("确认方案决策");
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }

    private NodeSolutionPackageUpdateCmd packageCmd(String version) {
        NodeSolutionPackageUpdateCmd cmd = new NodeSolutionPackageUpdateCmd();
        cmd.setPackageVersion(version);
        cmd.setProductSolution(" 产品方案 ");
        cmd.setTechnicalSolution("技术方案");
        cmd.setSummary("方案摘要");
        cmd.setScopeCoverage("覆盖已确认范围");
        cmd.setRolloutPremise("按发布检查项执行");
        return cmd;
    }

    private ProjectNodeSolutionPackageDO packageEntity() {
        ProjectNodeSolutionPackageDO pkg = new ProjectNodeSolutionPackageDO();
        pkg.setId(100L);
        pkg.setProjectId(1L);
        pkg.setNodeId(10L);
        pkg.setPackageVersion("v1.0");
        pkg.setProductSolution("产品方案");
        pkg.setTechnicalSolution("技术方案");
        pkg.setSummary("方案摘要");
        pkg.setScopeCoverage("覆盖已确认范围");
        pkg.setRolloutPremise("按发布检查项执行");
        pkg.setStatus("SUBMITTED");
        pkg.setVersion(0);
        return pkg;
    }

    private ProjectNodeSolutionReviewDO review(String type, String status) {
        ProjectNodeSolutionReviewDO review = new ProjectNodeSolutionReviewDO();
        review.setReviewType(type);
        review.setStatus(status);
        return review;
    }

    private NodeSolutionDecisionConfirmCmd decisionCmd(String result, String reason, String conditions) {
        NodeSolutionDecisionConfirmCmd cmd = new NodeSolutionDecisionConfirmCmd();
        cmd.setResult(result);
        cmd.setReason(reason);
        cmd.setConditions(conditions);
        return cmd;
    }
}
