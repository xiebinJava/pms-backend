package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.audit.AuditAction;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeSolutionDecisionConfirmCmd;
import com.brad.pms.dto.request.NodeSolutionDecisionUpdateCmd;
import com.brad.pms.dto.request.NodeSolutionPackageUpdateCmd;
import com.brad.pms.dto.response.NodeRequirementScopeDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeSolutionDecisionDO;
import com.brad.pms.entity.ProjectNodeSolutionPackageDO;
import com.brad.pms.entity.ProjectNodeSolutionReviewDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectNodeSolutionDecisionMapper;
import com.brad.pms.mapper.ProjectNodeSolutionPackageMapper;
import com.brad.pms.mapper.ProjectNodeSolutionReviewMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import com.brad.pms.workflow.WorkflowComponentKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeSolutionDesignServiceTest {

    @Mock ProjectNodeSolutionPackageMapper packageMapper;
    @Mock ProjectNodeSolutionReviewMapper reviewMapper;
    @Mock ProjectNodeSolutionDecisionMapper decisionMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock MemberService memberService;
    @Mock ProjectPermissionService permissionService;
    @Mock NodeRequirementScopeService requirementScopeService;
    @Mock OperationLogService operationLogService;

    @InjectMocks NodeSolutionDesignService service;

    @BeforeEach
    void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "test"), ProjectNodeSolutionReviewDO.class);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void rejectsNonDesignNodes() {
        ProjectNodeDO node = node("requirement");
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        doThrow(BusinessException.error("仅配置了方案设计组件的节点支持方案工作台"))
                .when(permissionService).requireNodeComponent(node, WorkflowComponentKey.SOLUTION_DESIGN,
                        "仅配置了方案设计组件的节点支持方案工作台");

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

        service.saveDraft(1L, 10L, packageCmd());

        ArgumentCaptor<ProjectNodeSolutionPackageDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionPackageDO.class);
        verify(packageMapper).insert(captor.capture());
        assertThat(captor.getValue().getProductSolution()).isEqualTo("产品方案");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_SOLUTION_PACKAGE_DRAFT_SAVED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
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
        when(permissionService.findNodeWithComponent(1L, WorkflowComponentKey.REQUIREMENT_SCOPE)).thenReturn(requirementNode);
        NodeRequirementScopeDTO baseline = new NodeRequirementScopeDTO();
        baseline.setBaselineStatus(0);
        when(requirementScopeService.get(1L, 11L)).thenReturn(baseline);

        assertThatThrownBy(() -> service.submitPackage(1L, 10L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需求范围基线");
    }

    @Test
    void submitsPackageAfterRequirementBaselineIsConfirmedAndAuditsIt() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionPackageDO pkg = packageEntity();
        pkg.setStatus("DRAFT");
        ProjectNodeDO requirementNode = node("requirement");
        requirementNode.setId(11L);
        NodeRequirementScopeDTO baseline = new NodeRequirementScopeDTO();
        baseline.setBaselineStatus(1);
        when(permissionService.requireManageableNode(1L, 10L, "提交方案包")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(pkg);
        when(packageMapper.updateById(any(ProjectNodeSolutionPackageDO.class))).thenReturn(1);
        when(permissionService.findNodeWithComponent(1L, WorkflowComponentKey.REQUIREMENT_SCOPE)).thenReturn(requirementNode);
        when(requirementScopeService.get(1L, 11L)).thenReturn(baseline);
        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        var result = service.submitPackage(1L, 10L, 0);

        assertThat(result.getSolutionPackage().getStatus()).isEqualTo("SUBMITTED");
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_SOLUTION_PACKAGE_SUBMITTED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void rejectsUnknownReviewType() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireReviewableNode(1L, 10L, "完成方案评审")).thenReturn(node);

        assertThatThrownBy(() -> service.completeReview(1L, 10L, "ARCHITECTURE", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评审类型");
    }

    @Test
    void assignsAReviewToAnExistingProjectMember() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "设置方案评审人")).thenReturn(node);
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(ProjectNodeSolutionReviewDO.class))).thenReturn(1);

        service.assignReviewer(1L, 10L, "TECHNICAL", null, 7L);

        ArgumentCaptor<ProjectNodeSolutionReviewDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionReviewDO.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getReviewerId()).isEqualTo(7L);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_SOLUTION_REVIEWER_ASSIGNED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void reassignsAPassedReviewAndReturnsItToPending() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionReviewDO review = review("TECHNICAL", "PASSED");
        review.setReviewerId(7L);
        review.setCompletedBy(7L);
        review.setCompletedAt(java.time.LocalDateTime.now());
        when(permissionService.requireManageableNode(1L, 10L, "设置方案评审人")).thenReturn(node);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(reviewMapper.updateById(any(ProjectNodeSolutionReviewDO.class))).thenReturn(1);

        service.assignReviewer(1L, 10L, "TECHNICAL", 0, 8L);

        ArgumentCaptor<ProjectNodeSolutionReviewDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionReviewDO.class);
        verify(reviewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getReviewerId()).isEqualTo(8L);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getCompletedBy()).isNull();
        assertThat(captor.getValue().getCompletedAt()).isNull();
    }

    @Test
    void updatesAReviewSuggestionWhileTheNodeIsOpen() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionReviewDO review = review("TECHNICAL", "PASSED");
        when(permissionService.requireManageableNode(1L, 10L, "更新方案评审建议")).thenReturn(node);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(reviewMapper.updateById(any(ProjectNodeSolutionReviewDO.class))).thenReturn(1);

        service.updateReviewSuggestion(1L, 10L, "TECHNICAL", 0, "补充接口边界说明");

        ArgumentCaptor<ProjectNodeSolutionReviewDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionReviewDO.class);
        verify(reviewMapper).updateById(captor.capture());
        assertThat(captor.getValue().getComment()).isEqualTo("补充接口边界说明");
        assertThat(captor.getValue().getStatus()).isEqualTo("PASSED");
    }

    @Test
    void preventsAnUnassignedMemberFromCompletingAReview() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionReviewDO review = review("TECHNICAL", "PENDING");
        review.setReviewerId(7L);
        UserContext.set(new LoginUser(8L, "other", "其他成员"));
        when(permissionService.requireReviewableNode(1L, 10L, "完成方案评审")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectOne(any())).thenReturn(review);

        assertThatThrownBy(() -> service.completeReview(1L, 10L, "TECHNICAL", 0, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("指定评审人");
        verify(reviewMapper, never()).updateById(any(ProjectNodeSolutionReviewDO.class));
        verifyNoInteractions(operationLogService);
    }

    @Test
    void completesAReviewAsTheAssignedReviewerAndAuditsIt() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionReviewDO review = review("TECHNICAL", "PENDING");
        review.setReviewerId(7L);
        UserContext.set(new LoginUser(7L, "reviewer", "评审人"));
        when(permissionService.requireReviewableNode(1L, 10L, "完成方案评审")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(reviewMapper.updateById(any(ProjectNodeSolutionReviewDO.class))).thenReturn(1);
        when(reviewMapper.selectList(any())).thenReturn(List.of(review));

        var result = service.completeReview(1L, 10L, "TECHNICAL", 0, "补充接口边界说明");

        assertThat(result.getReviews()).anyMatch(item ->
                "TECHNICAL".equals(item.getReviewType()) && "PASSED".equals(item.getStatus()));
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_SOLUTION_REVIEW_COMPLETED.name().equals(event.action())
                        && "TECHNICAL".equals(event.reason())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void requiresAnAdminToAssignAReviewerBeforeCompletingAReview() {
        ProjectNodeDO node = node("design");
        UserContext.set(new LoginUser(1L, "admin", "管理员", 1));
        when(permissionService.requireReviewableNode(1L, 10L, "完成方案评审")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.completeReview(1L, 10L, "TECHNICAL", 0, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先设置指定评审人");
        verify(reviewMapper, never()).updateById(any(ProjectNodeSolutionReviewDO.class));
    }

    @Test
    void rejectsCompletingAnAlreadyPassedReview() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionReviewDO review = review("TECHNICAL", "PASSED");
        review.setReviewerId(7L);
        UserContext.set(new LoginUser(7L, "reviewer", "评审人"));
        when(permissionService.requireReviewableNode(1L, 10L, "完成方案评审")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectOne(any())).thenReturn(review);

        assertThatThrownBy(() -> service.completeReview(1L, 10L, "TECHNICAL", 0, "再次评审"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评审已完成");
        verify(reviewMapper, never()).updateById(any(ProjectNodeSolutionReviewDO.class));
    }

    @Test
    void mapsConcurrentReviewCreationToConflict() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "设置方案评审人")).thenReturn(node);
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(ProjectNodeSolutionReviewDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> service.assignReviewer(1L, 10L, "TECHNICAL", null, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他人修改");
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
        cmd.setConditions("上线前完成监控配置");

        var result = service.confirmDecision(1L, 10L, cmd);

        ArgumentCaptor<ProjectNodeSolutionDecisionDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionDecisionDO.class);
        verify(decisionMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
        assertThat(captor.getValue().getConfirmedAt()).isNotNull();
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_SOLUTION_DECISION_CONFIRMED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void savesDecisionDraftBeforeReviewsAreComplete() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionDecisionDO existing = new ProjectNodeSolutionDecisionDO();
        existing.setProjectId(1L);
        existing.setNodeId(10L);
        existing.setVersion(2);
        existing.setStatus("DRAFT");
        when(permissionService.requireManageableNode(1L, 10L, "保存方案决策")).thenReturn(node);
        when(decisionMapper.selectOne(any())).thenReturn(existing);
        when(decisionMapper.updateById(any(ProjectNodeSolutionDecisionDO.class))).thenReturn(1);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        NodeSolutionDecisionUpdateCmd cmd = new NodeSolutionDecisionUpdateCmd();
        cmd.setVersion(2);
        cmd.setResult("CONDITIONAL_PASS");
        cmd.setConditions(" 上线前补齐监控 ");

        var result = service.saveDecisionDraft(1L, 10L, cmd);

        ArgumentCaptor<ProjectNodeSolutionDecisionDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionDecisionDO.class);
        verify(decisionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("CONDITIONAL_PASS");
        assertThat(captor.getValue().getConditions()).isEqualTo("上线前补齐监控");
        assertThat(captor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(captor.getValue().getConfirmedAt()).isNull();
        assertThat(result.getDecision().getStatus()).isEqualTo("DRAFT");
        verify(operationLogService).record(org.mockito.ArgumentMatchers.argThat(event ->
                AuditAction.NODE_SOLUTION_DECISION_DRAFT_SAVED.name().equals(event.action())
                        && Long.valueOf(10L).equals(event.resourceId())
                        && Long.valueOf(1L).equals(event.projectId())));
    }

    @Test
    void confirmsPassWithoutASeparateDecisionReason() {
        ProjectNodeDO node = node("design");
        when(permissionService.requireManageableNode(1L, 10L, "确认方案决策")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                review("BUSINESS_PRODUCT", "PASSED"),
                review("TECHNICAL", "PASSED"),
                review("TEST_RELEASE", "PASSED")));
        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(decisionMapper.insert(any(ProjectNodeSolutionDecisionDO.class))).thenReturn(1);

        service.confirmDecision(1L, 10L, decisionCmd("PASS", null));

        ArgumentCaptor<ProjectNodeSolutionDecisionDO> captor = ArgumentCaptor.forClass(ProjectNodeSolutionDecisionDO.class);
        verify(decisionMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
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

        assertThatThrownBy(() -> service.confirmDecision(1L, 10L, decisionCmd("PASS", null)))
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

        NodeSolutionDecisionConfirmCmd cmd = decisionCmd("PASS", null);

        assertThatThrownBy(() -> service.confirmDecision(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("三类评审人并完成评审");
    }

    @Test
    void rejectsDecisionWhenARequiredReviewPassedWithoutAReviewer() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionReviewDO unassigned = review("TECHNICAL", "PASSED");
        unassigned.setReviewerId(null);
        when(permissionService.requireManageableNode(1L, 10L, "确认方案决策")).thenReturn(node);
        when(packageMapper.selectOne(any())).thenReturn(packageEntity());
        when(reviewMapper.selectList(any())).thenReturn(List.of(
                review("BUSINESS_PRODUCT", "PASSED"),
                unassigned,
                review("TEST_RELEASE", "PASSED")));

        assertThatThrownBy(() -> service.confirmDecision(1L, 10L, decisionCmd("PASS", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("三类评审人并完成评审");
        verify(decisionMapper, never()).insert(any(ProjectNodeSolutionDecisionDO.class));
    }

    @Test
    void editsAConfirmedPackageDirectlyAndClearsTheDecisionAndReviews() {
        ProjectNodeDO node = node("design");
        ProjectNodeSolutionDecisionDO decision = new ProjectNodeSolutionDecisionDO();
        decision.setStatus("CONFIRMED");
        decision.setVersion(2);
        ProjectNodeSolutionPackageDO current = packageEntity();
        when(permissionService.requireManageableNode(1L, 10L, "保存方案包")).thenReturn(node);
        when(decisionMapper.selectOne(any())).thenReturn(decision);
        when(packageMapper.selectOne(any())).thenReturn(current);
        when(packageMapper.updateById(any(ProjectNodeSolutionPackageDO.class))).thenReturn(1);
        when(decisionMapper.updateById(any(ProjectNodeSolutionDecisionDO.class))).thenReturn(1);
        when(reviewMapper.selectList(any())).thenReturn(List.of());

        NodeSolutionPackageUpdateCmd cmd = packageCmd();
        cmd.setProductSolution("修改后的产品方案");
        cmd.setVersion(0);
        var result = service.saveDraft(1L, 10L, cmd);

        assertThat(result.getDecision().getStatus()).isEqualTo("DRAFT");
        assertThat(result.getDecision().getConfirmedAt()).isNull();
        verify(decisionMapper).updateById(decision);
        verify(reviewMapper, never()).updateById(any(ProjectNodeSolutionReviewDO.class));
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

    private NodeSolutionPackageUpdateCmd packageCmd() {
        NodeSolutionPackageUpdateCmd cmd = new NodeSolutionPackageUpdateCmd();
        cmd.setProductSolution(" 产品方案 ");
        cmd.setTechnicalSolution("技术方案");
        cmd.setVersion(0);
        return cmd;
    }

    private ProjectNodeSolutionPackageDO packageEntity() {
        ProjectNodeSolutionPackageDO pkg = new ProjectNodeSolutionPackageDO();
        pkg.setId(100L);
        pkg.setProjectId(1L);
        pkg.setNodeId(10L);
        pkg.setProductSolution("产品方案");
        pkg.setTechnicalSolution("技术方案");
        pkg.setStatus("SUBMITTED");
        pkg.setVersion(0);
        return pkg;
    }

    private ProjectNodeSolutionReviewDO review(String type, String status) {
        ProjectNodeSolutionReviewDO review = new ProjectNodeSolutionReviewDO();
        review.setReviewType(type);
        review.setStatus(status);
        review.setReviewerId(7L);
        review.setVersion(0);
        return review;
    }

    private NodeSolutionDecisionConfirmCmd decisionCmd(String result, String conditions) {
        NodeSolutionDecisionConfirmCmd cmd = new NodeSolutionDecisionConfirmCmd();
        cmd.setResult(result);
        cmd.setConditions(conditions);
        return cmd;
    }
}
