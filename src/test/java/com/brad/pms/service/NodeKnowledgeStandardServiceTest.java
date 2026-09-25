package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.NodeKnowledgeActionCmd;
import com.brad.pms.dto.request.NodeKnowledgeStandardUpdateCmd;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeKnowledgeBaselineDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.mapper.ProjectNodeKnowledgeActionMapper;
import com.brad.pms.mapper.ProjectNodeKnowledgeAssetMapper;
import com.brad.pms.mapper.ProjectNodeKnowledgeBaselineMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeKnowledgeStandardServiceTest {

    @Mock ProjectNodeKnowledgeBaselineMapper baselineMapper;
    @Mock ProjectNodeKnowledgeAssetMapper assetMapper;
    @Mock ProjectNodeKnowledgeActionMapper actionMapper;
    @Mock ProjectMemberMapper memberMapper;
    @Mock MemberService memberService;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;

    @InjectMocks NodeKnowledgeStandardService service;

    @Test
    void rejectsNonKnowledgeNodes() {
        ProjectNodeDO node = node("develop");
        when(permissionService.requireNode(1L, 10L)).thenReturn(node);
        doThrow(BusinessException.error("仅配置了知识标准组件的节点支持知识工作台"))
                .when(permissionService).requireNodeComponent(node, WorkflowComponentKey.KNOWLEDGE_STANDARD,
                        "仅配置了知识标准组件的节点支持知识工作台");

        assertThatThrownBy(() -> service.get(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识标准组件");
    }

    @Test
    void rejectsStaleDraftVersion() {
        when(permissionService.requireManageableNode(1L, 10L, "保存知识沉淀与标准改进")).thenReturn(node("knowledge"));
        ProjectNodeKnowledgeBaselineDO baseline = new ProjectNodeKnowledgeBaselineDO();
        baseline.setVersion(4);
        when(baselineMapper.selectOne(any())).thenReturn(baseline);

        NodeKnowledgeStandardUpdateCmd cmd = new NodeKnowledgeStandardUpdateCmd();
        cmd.setVersion(3);
        NodeKnowledgeActionCmd action = new NodeKnowledgeActionCmd();
        action.setTitle("建立检索索引");
        action.setStatus("NOT_STARTED");
        cmd.setActions(List.of(action));

        assertThatThrownBy(() -> service.save(1L, 10L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("修改");
        verify(baselineMapper, never()).updateById(any(ProjectNodeKnowledgeBaselineDO.class));
    }

    private ProjectNodeDO node(String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(10L);
        node.setProjectId(1L);
        node.setNodeKey(key);
        node.setStatus(1);
        return node;
    }
}
