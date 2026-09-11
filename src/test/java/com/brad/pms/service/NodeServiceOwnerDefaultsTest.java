package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.mapper.ProjectNodeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceOwnerDefaultsTest {

    @Mock ProjectNodeMapper nodeMapper;
    @Mock MemberService memberService;
    @Mock OperationLogService operationLogService;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;

    @InjectMocks NodeService nodeService;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDO.class);
    }

    @Test
    void firstNodeDefaultsToCreatorAndLaterNodesDefaultToProjectManager() {
        assertThat(NodeService.defaultNodeOwnerId(true, 11L, 22L)).isEqualTo(11L);
        assertThat(NodeService.defaultNodeOwnerId(false, 11L, 22L)).isEqualTo(22L);
        assertThat(NodeService.shouldAssignDefaultOwner(null, null, 11L)).isTrue();
        assertThat(NodeService.shouldAssignDefaultOwner(22L, 22L, 33L)).isTrue();
        assertThat(NodeService.shouldAssignDefaultOwner(22L, 22L, 22L)).isFalse();
        assertThat(NodeService.shouldAssignDefaultOwner(99L, 22L, 33L)).isFalse();
        assertThat(NodeService.shouldAssignDefaultOwner(null, null, null)).isFalse();
    }

    @Test
    void initDefaultAssignsTheCreatorOnlyToTheFirstNode() {
        when(nodeMapper.insert(any(ProjectNodeDO.class))).thenReturn(1);

        nodeService.initDefault(8L, 11L);

        ArgumentCaptor<ProjectNodeDO> captor = ArgumentCaptor.forClass(ProjectNodeDO.class);
        verify(nodeMapper, times(9)).insert(captor.capture());
        List<ProjectNodeDO> inserted = captor.getAllValues();
        assertThat(inserted.get(0).getNodeKey()).isEqualTo("kickoff");
        assertThat(inserted.get(0).getOwnerId()).isEqualTo(11L);
        assertThat(inserted.subList(1, inserted.size()))
                .allSatisfy(node -> assertThat(node.getOwnerId()).isNull());
    }

    @Test
    void applyDefaultOwnersFillsEmptyNodesAndFollowsThePreviousManager() {
        ProjectDO project = new ProjectDO();
        project.setId(8L);
        project.setCreatedBy(11L);
        project.setProjectManagerId(33L);
        ProjectNodeDO kickoff = node(1L, "kickoff", 0, null);
        ProjectNodeDO requirement = node(2L, "requirement", 1, 22L);
        ProjectNodeDO design = node(3L, "design", 2, 99L);
        ProjectNodeDO completed = node(4L, "plan", 3, null);
        completed.setStatus(2);
        when(nodeMapper.selectList(any())).thenReturn(List.of(kickoff, requirement, design, completed));
        when(nodeMapper.updateById(any(ProjectNodeDO.class))).thenReturn(1);

        nodeService.applyDefaultOwners(project, 22L);

        assertThat(kickoff.getOwnerId()).isEqualTo(11L);
        assertThat(requirement.getOwnerId()).isEqualTo(33L);
        assertThat(design.getOwnerId()).isEqualTo(99L);
        assertThat(completed.getOwnerId()).isNull();
        verify(memberService).ensureMember(8L, 11L);
        verify(memberService).ensureMember(8L, 33L);
        verify(memberService, never()).ensureMember(8L, 99L);
    }

    private static ProjectNodeDO node(Long id, String key, int sort, Long ownerId) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(8L);
        node.setNodeKey(key);
        node.setSort(sort);
        node.setStatus(sort == 0 ? 1 : 0);
        node.setOwnerId(ownerId);
        return node;
    }
}
