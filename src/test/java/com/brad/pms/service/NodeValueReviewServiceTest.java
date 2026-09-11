package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeValueReviewUpdateCmd;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeValueReviewDO;
import com.brad.pms.mapper.ProjectNodeValueReviewMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeValueReviewServiceTest {

    @Mock ProjectNodeValueReviewMapper reviewMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock OperationLogService operationLogService;

    @InjectMocks NodeValueReviewService service;

    @Test
    void completionRequiresActualResultAndRetrospectiveConclusion() {
        ProjectNodeDO node = node("review");
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);

        ProjectNodeValueReviewDO incomplete = baseline("PENDING");
        incomplete.setActualResult(null);
        incomplete.setRetrospectiveConclusion(null);
        when(reviewMapper.selectOne(any())).thenReturn(incomplete);

        assertThatThrownBy(() -> service.requireCompleted(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价值验证");
    }

    @Test
    void completionAcceptsPartialValueWhenConclusionIsRecorded() {
        ProjectNodeDO node = node("review");
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(reviewMapper.selectOne(any())).thenReturn(baseline("PARTIAL"));

        service.requireCompleted(1L, 10L);
    }

    @Test
    void saveRejectsStaleDraftVersion() {
        ProjectNodeDO node = node("review");
        when(permissionService.requireManageableNode(1L, 10L, "保存价值验证与项目复盘")).thenReturn(node);
        ProjectNodeValueReviewDO current = baseline("ACHIEVED");
        current.setVersion(4);
        when(reviewMapper.selectOne(any())).thenReturn(current);

        NodeValueReviewUpdateCmd cmd = new NodeValueReviewUpdateCmd();
        cmd.setVersion(3);
        cmd.setResultStatus("ACHIEVED");
        cmd.setActualResult("实际结果");
        cmd.setRetrospectiveConclusion("复盘结论");

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("修改");
        verify(reviewMapper, never()).updateById(any(ProjectNodeValueReviewDO.class));
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }

    private ProjectNodeValueReviewDO baseline(String resultStatus) {
        ProjectNodeValueReviewDO baseline = new ProjectNodeValueReviewDO();
        baseline.setProjectId(1L);
        baseline.setNodeId(10L);
        baseline.setVersion(1);
        baseline.setResultStatus(resultStatus);
        baseline.setActualResult("实际结果");
        baseline.setRetrospectiveConclusion("复盘结论");
        return baseline;
    }
}
