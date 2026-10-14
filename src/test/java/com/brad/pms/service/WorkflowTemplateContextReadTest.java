package com.brad.pms.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.entity.WorkflowTemplateVersionDO;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.WorkflowTemplateVersionMapper;
import com.brad.pms.workflow.WorkflowComponentKey;
import com.brad.pms.workflow.WorkflowNodeDefinition;
import com.brad.pms.workflow.WorkflowTemplateDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** getDefinitionForContext and applyLegacyTopicBinding are the context-only strict resolvers. */
@ExtendWith(MockitoExtension.class)
class WorkflowTemplateContextReadTest {

    @Mock ProjectNodeMapper unusedNodeMapper;
    @Mock WorkflowTemplateVersionMapper versionMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;

    @Test
    void applyLegacyTopicBindingDerivesTheComponentOnlyFromNodesThatActuallyHostTopics() {
        ProjectNodeDO host = node(301L, 8L, "develop");
        ProjectNodeDO plain = node(302L, 8L, "kickoff");
        WorkflowTemplateDefinition stored = definition(List.of(
                nodeDefinition("develop"), nodeDefinition("kickoff")));
        when(topicMapper.selectList(any())).thenReturn(List.of(
                topic(20L, 8L, 301L, false), topic(21L, 8L, 301L, true)));

        WorkflowTemplateDefinition effective = bindingService().applyLegacyTopicBinding(
                8L, stored, List.of(host, plain));

        assertThat(components(effective, "develop")).contains(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        assertThat(components(effective, "kickoff")).doesNotContain(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        assertThat(components(stored, "develop")).doesNotContain(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        verifyNoMoreInteractions(topicMapper);
    }

    @Test
    void applyLegacyTopicBindingNeverResolvesTheCurrentDefaultMount() {
        ProjectNodeDO plain = node(302L, 8L, "kickoff");
        WorkflowTemplateDefinition stored = definition(List.of(nodeDefinition("kickoff")));
        when(topicMapper.selectList(any())).thenReturn(List.of());

        WorkflowTemplateDefinition effective = bindingService().applyLegacyTopicBinding(8L, stored, List.of(plain));

        assertThat(components(effective, "kickoff")).doesNotContain(WorkflowComponentKey.DEVELOPMENT_CONTROL);
        verifyNoMoreInteractions(topicMapper);
    }

    @Test
    void getDefinitionForContextRejectsMissingVersionsAsConflicts() {
        when(versionMapper.selectById(4242L)).thenReturn(null);

        assertThatThrownBy(() -> templateService().getDefinitionForContext(4242L))
                .isInstanceOfSatisfying(BusinessException.class, thrown ->
                        assertThat(thrown.getCode()).isEqualTo(409))
                .hasMessageContaining("绑定");
    }

    @Test
    void getDefinitionForContextRejectsBrokenDefinitionsAsConflicts() {
        WorkflowTemplateVersionDO version = new WorkflowTemplateVersionDO();
        version.setId(7L);
        version.setDefinitionJson("{not json");
        when(versionMapper.selectById(7L)).thenReturn(version);

        assertThatThrownBy(() -> templateService().getDefinitionForContext(7L))
                .isInstanceOfSatisfying(BusinessException.class, thrown ->
                        assertThat(thrown.getCode()).isEqualTo(409));
    }

    @Test
    void getDefinitionForContextReturnsThePinnedDefinitionWithoutDefaultResolution() {
        WorkflowTemplateVersionDO version = new WorkflowTemplateVersionDO();
        version.setId(7L);
        version.setDefinitionJson("{\"schemaVersion\":1,\"nodes\":[{\"key\":\"design\",\"name\":\"设计\","
                + "\"components\":[],\"fields\":[{\"key\":\"summary\",\"label\":\"摘要\",\"type\":\"TEXT\"}]}]}");
        when(versionMapper.selectById(7L)).thenReturn(version);

        WorkflowTemplateDefinition definition = templateService().getDefinitionForContext(7L);

        assertThat(definition.nodes()).hasSize(1);
        assertThat(definition.nodes().get(0).key()).isEqualTo("design");
        verify(versionMapper, never()).selectList(any());
    }

    private WorkflowTemplateService templateService() {
        return new WorkflowTemplateService(null, null, null, versionMapper, null,
                new ObjectMapper(), null);
    }

    private WorkflowComponentBindingService bindingService() {
        return new WorkflowComponentBindingService(templateService(), topicMapper);
    }

    private static WorkflowTemplateDefinition definition(List<WorkflowNodeDefinition> nodes) {
        return new WorkflowTemplateDefinition(1, nodes);
    }

    private static WorkflowNodeDefinition nodeDefinition(String key) {
        return new WorkflowNodeDefinition(key, key, "", "", "", List.of(), List.of(),
                false, List.of(), List.of("fields"));
    }

    private static ProjectNodeDO node(Long id, Long projectId, String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(projectId);
        node.setNodeKey(key);
        return node;
    }

    private static ProjectNodeDevelopmentTopicDO topic(Long id, Long projectId, Long nodeId, boolean deleted) {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(id);
        topic.setProjectId(projectId);
        topic.setNodeId(nodeId);
        topic.setDeleted(deleted);
        return topic;
    }

    private static List<String> components(WorkflowTemplateDefinition definition, String key) {
        return definition.nodes().stream().filter(node -> node.key().equals(key)).findFirst().orElseThrow()
                .runtimeComponents();
    }
}
