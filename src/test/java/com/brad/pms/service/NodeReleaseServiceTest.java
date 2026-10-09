package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeReleaseUpdateCmd;
import com.brad.pms.dto.response.NodeReleaseDTO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeReleaseBaselineDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectNodeReleaseBaselineMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeReleaseServiceTest {

    @Mock ProjectNodeReleaseBaselineMapper baselineMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock OperationLogService operationLogService;
    @Mock UserService userService;

    @InjectMocks NodeReleaseService service;

    @Test
    void completionRequiresHandoverOwnerAndNotes() {
        ProjectNodeDO node = node("release");
        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(completeBaseline());

        service.requireCompleted(1L, 10L);

        ProjectNodeReleaseBaselineDO legacyFieldsEmpty = completeBaseline();
        legacyFieldsEmpty.setHandoverOwnerId(42L);
        when(baselineMapper.selectOne(any())).thenReturn(legacyFieldsEmpty);
        service.requireCompleted(1L, 10L);

        legacyFieldsEmpty.setHandoverOwnerId(null);
        assertThatThrownBy(() -> service.requireCompleted(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("交接人");

        legacyFieldsEmpty.setHandoverOwnerId(42L);
        legacyFieldsEmpty.setHandoverNotes(null);
        assertThatThrownBy(() -> service.requireCompleted(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("交接说明");
    }

    @Test
    void saveRejectsStaleDraftVersion() {
        ProjectNodeDO node = node("release");
        when(permissionService.requireManageableNode(1L, 10L, "保存发布决策与运营交接")).thenReturn(node);
        ProjectNodeReleaseBaselineDO current = completeBaseline();
        current.setVersion(3);
        when(baselineMapper.selectOne(any())).thenReturn(current);
        NodeReleaseUpdateCmd cmd = new NodeReleaseUpdateCmd();
        cmd.setVersion(2);

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("修改");
        verify(baselineMapper, never()).updateById(any(ProjectNodeReleaseBaselineDO.class));
    }

    @Test
    void saveStoresHandoverOwnerAndNotes() {
        ProjectNodeDO node = node("release");
        when(permissionService.requireManageableNode(1L, 10L, "保存发布决策与运营交接")).thenReturn(node);
        ProjectNodeReleaseBaselineDO current = completeBaseline();
        current.setVersion(3);
        when(baselineMapper.selectOne(any())).thenReturn(current);
        when(baselineMapper.updateById(any(ProjectNodeReleaseBaselineDO.class))).thenReturn(1);

        when(userService.requireActiveUser(42L)).thenReturn(null);
        NodeReleaseUpdateCmd cmd = new NodeReleaseUpdateCmd();
        cmd.setVersion(3);
        cmd.setHandoverOwnerId(42L);
        cmd.setHandoverNotes("新的交接说明");

        service.save(1L, 10L, cmd);

        org.assertj.core.api.Assertions.assertThat(current.getHandoverOwnerId()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(current.getHandoverNotes()).isEqualTo("新的交接说明");
    }

    @Test
    void getReturnsHandoverOwnerNameAndUsername() {
        ProjectNodeDO node = node("release");
        ProjectNodeReleaseBaselineDO baseline = completeBaseline();
        UserDO owner = new UserDO();
        owner.setId(42L);
        owner.setNameZh("张三");
        owner.setUsername("zhangsan");

        when(permissionService.requireProjectReadable(1L)).thenReturn(null);
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);
        when(userService.listByIds(List.of(42L))).thenReturn(List.of(owner));

        NodeReleaseDTO result = service.get(1L, 10L);

        assertThat(result.getHandoverOwnerId()).isEqualTo(42L);
        assertThat(result.getHandoverOwnerName()).isEqualTo("张三（zhangsan）");
        assertThat(result.getHandoverOwnerUsername()).isEqualTo("zhangsan");
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }

    private ProjectNodeReleaseBaselineDO completeBaseline() {
        ProjectNodeReleaseBaselineDO baseline = new ProjectNodeReleaseBaselineDO();
        baseline.setProjectId(1L);
        baseline.setNodeId(10L);
        baseline.setVersion(1);
        baseline.setHandoverOwnerId(42L);
        baseline.setHandoverNotes("运维已接收发布说明");
        return baseline;
    }
}
