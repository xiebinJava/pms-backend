package com.brad.pms.service;

import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.request.DevelopmentTopicProjectQry;
import com.brad.pms.dto.request.DevelopmentTopicUpdateCmd;
import com.brad.pms.entity.DevelopmentItemWorkflowDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.mapper.DevelopmentItemTaskMapper;
import com.brad.pms.mapper.DevelopmentItemWorkflowMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.security.ProjectPermissionPolicy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevelopmentTopicManagementServiceTest {

    @Mock ProjectMapper projectMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock DevelopmentItemWorkflowMapper workflowMapper;
    @Mock DevelopmentItemTaskMapper taskMapper;
    @Mock ProjectService projectService;
    @Mock ProjectPermissionService permissionService;
    @Mock WorkflowTemplateService workflowTemplateService;
    @Mock DevelopmentItemWorkflowService developmentItemWorkflowService;
    @Mock OperationLogService operationLogService;
    @Mock UserService userService;
    @Mock ProjectMemberAssignmentService projectMemberAssignmentService;
    @InjectMocks DevelopmentTopicManagementService service;

    @BeforeEach
    void initMybatisLambdaCaches() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "topic-management-test");
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentTopicDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectNodeDevelopmentStoryDO.class);
        TableInfoHelper.initTableInfo(assistant, DevelopmentItemWorkflowDO.class);
    }

    @Test
    void exposesTopicEditDeleteRestoreAndProjectOptionsRoutes() throws Exception {
        Class<?> controller = optionalClass("com.brad.pms.controller.DevelopmentTopicController");
        assertThat(controller).as("topic-management controller").isNotNull();
        assertThat(controller.getAnnotation(RequestMapping.class).value()).containsExactly("/development/topics");

        assertEndpoint(controller, "create", PostMapping.class, "", PermissionCode.PROJECT_WRITE);
        assertEndpoint(controller, "update", PutMapping.class, "/{id}", PermissionCode.PROJECT_WRITE);
        assertEndpoint(controller, "delete", DeleteMapping.class, "/{id}", PermissionCode.PROJECT_WRITE);
        assertEndpoint(controller, "restore", PostMapping.class, "/{id}/restore", PermissionCode.PROJECT_WRITE);
        assertEndpoint(controller, "projectOptions", PostMapping.class, "/projects/page", PermissionCode.PROJECT_READ);
    }

    @Test
    void representsTopicDeletionAsRecoverableStateAndAddsDeletedPageScope() throws Exception {
        Optional<Field> deletedField = Arrays.stream(ProjectNodeDevelopmentTopicDO.class.getDeclaredFields())
                .filter(field -> field.getName().equals("deleted")).findFirst();
        assertThat(deletedField).as("recoverable topic deleted marker").isPresent();
        Field deleted = deletedField.orElseThrow();
        assertThat(deleted.getType()).isEqualTo(Boolean.class);
        assertThat(Arrays.stream(DevelopmentItemPageQry.class.getMethods())
                .anyMatch(method -> method.getName().equals("getDeleted"))).isTrue();
    }

    @Test
    void exposesTransactionalTopicManagementOperations() throws Exception {
        Class<?> service = optionalClass("com.brad.pms.service.DevelopmentTopicManagementService");
        assertThat(service).as("topic-management service").isNotNull();
        assertThat(hasMethod(service, "update", 2)).isTrue();
        assertThat(hasMethod(service, "create", 1)).isTrue();
        assertThat(hasMethod(service, "softDelete", 1)).isTrue();
        assertThat(hasMethod(service, "restore", 1)).isTrue();
        assertThat(hasMethod(service, "projectOptions", 1)).isTrue();
    }

    @Test
    void createsTopicUnderConfiguredProjectNodeAndInitializesItsWorkflow() {
        ProjectDO target = project(2L, 1);
        ProjectNodeDO targetNode = node(22L, 2L, "host");
        DevelopmentTopicUpdateCmd cmd = update("  新专题  ", 88L, 2L);
        when(projectMapper.selectById(2L)).thenReturn(target);
        when(permissionService.canManageProject(target)).thenReturn(true);
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("host");
        when(nodeMapper.selectOne(any())).thenReturn(targetNode);
        when(developmentItemWorkflowService.createIfDefaultExists(
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 99L, 2L, 22L))
                .thenReturn(workflow(3000L, "TOPIC", 99L, 2L, 22L, 501L));
        doAnswer(invocation -> {
            ProjectNodeDevelopmentTopicDO inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        }).when(topicMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeDevelopmentTopicDO>any());

        Long topicId = service.create(cmd);

        assertThat(topicId).isEqualTo(99L);
        verify(userService).requireActiveUser(88L);
        verify(projectMemberAssignmentService).replaceAssignment(2L,
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 99L,
                com.brad.pms.common.enums.DevelopmentAssignmentType.TOPIC_OWNER, 99L, 88L);
        verify(topicMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeDevelopmentTopicDO>argThat(inserted ->
                inserted.getId().equals(99L)
                        && inserted.getProjectId().equals(2L)
                        && inserted.getNodeId().equals(22L)
                        && inserted.getTitle().equals("新专题")
                        && inserted.getTestStatus().equals("NOT_STARTED")
                        && Boolean.FALSE.equals(inserted.getDeleted())));
        verify(developmentItemWorkflowService).createIfDefaultExists(
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 99L, 2L, 22L);
        verify(operationLogService).record(any());
    }

    @Test
    void createsUnboundTopicAndUsesGlobalOwnerValidation() {
        when(developmentItemWorkflowService.createIfDefaultExists(
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 99L, null, null))
                .thenReturn(workflow(3000L, "TOPIC", 99L, null, null, 501L));
        doAnswer(invocation -> {
            ProjectNodeDevelopmentTopicDO inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        }).when(topicMapper).insert(any(ProjectNodeDevelopmentTopicDO.class));

        Long id = service.create(update("新专题", 88L, null));

        assertThat(id).isEqualTo(99L);
        verify(userService).requireActiveUser(88L);
        verify(permissionService, never()).requireProjectMember(any(Long.class), any(Long.class));
        verify(projectMemberAssignmentService, never()).replaceAssignment(any(), any(), any(), any(), any(), any());
        verify(developmentItemWorkflowService).createIfDefaultExists(
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 99L, null, null);
    }

    @Test
    void unbindsTopicStoriesAndWorkflowsWithoutDeletingTheirHistory() {
        ProjectNodeDevelopmentTopicDO topic = topic(10L, 1L, 11L, 88L);
        ProjectNodeDevelopmentStoryDO story = story(30L, 10L, 1L, 11L, 99L);
        DevelopmentItemWorkflowDO topicFlow = workflow(1000L, "TOPIC", 10L, 1L, 11L, 501L);
        DevelopmentItemWorkflowDO storyFlow = workflow(2000L, "STORY", 30L, 1L, 11L, 502L);
        when(topicMapper.selectByIdForUpdate(10L)).thenReturn(topic);
        when(projectMapper.selectIncludingDeleted(1L)).thenReturn(project(1L, 1));
        when(permissionService.canManageProject(any(ProjectDO.class))).thenReturn(true);
        when(storyMapper.selectList(any())).thenReturn(List.of(story));
        when(storyMapper.selectByIdForUpdate(30L)).thenReturn(story);
        when(workflowMapper.selectForUpdate("TOPIC", 10L)).thenReturn(topicFlow);
        when(workflowMapper.selectForUpdate("STORY", 30L)).thenReturn(storyFlow);
        when(taskMapper.selectByWorkflowIdsForUpdate(any())).thenReturn(List.of());
        when(topicMapper.updateById(any(ProjectNodeDevelopmentTopicDO.class))).thenReturn(1);
        when(storyMapper.updateById(any(ProjectNodeDevelopmentStoryDO.class))).thenReturn(1);
        when(workflowMapper.updateById(any(DevelopmentItemWorkflowDO.class))).thenReturn(1);

        service.update(10L, update("专题", 88L, null));

        assertThat(topic.getProjectId()).isNull();
        assertThat(topic.getNodeId()).isNull();
        assertThat(story.getProjectId()).isNull();
        assertThat(story.getNodeId()).isNull();
        assertThat(topicFlow.getTemplateVersionId()).isEqualTo(501L);
        assertThat(storyFlow.getTemplateVersionId()).isEqualTo(502L);
        assertThat(topicFlow.getSourceNodeId()).isNull();
        assertThat(storyFlow.getSourceNodeId()).isNull();
        verify(projectMemberAssignmentService).synchronizeItemAssignments(1L, null,
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 10L);
        verify(projectMemberAssignmentService).synchronizeItemAssignments(1L, null,
                com.brad.pms.workflow.DevelopmentItemType.STORY, 30L);
    }

    @Test
    void rejectsTopicCreationWhenNoPublishedDefaultWorkflowCanBePinned() {
        ProjectDO target = project(2L, 1);
        ProjectNodeDO targetNode = node(22L, 2L, "host");
        when(projectMapper.selectById(2L)).thenReturn(target);
        when(permissionService.canManageProject(target)).thenReturn(true);
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("host");
        when(nodeMapper.selectOne(any())).thenReturn(targetNode);
        doAnswer(invocation -> {
            ProjectNodeDevelopmentTopicDO inserted = invocation.getArgument(0);
            inserted.setId(99L);
            return 1;
        }).when(topicMapper).insert(org.mockito.ArgumentMatchers.<ProjectNodeDevelopmentTopicDO>any());

        assertThatThrownBy(() -> service.create(update("新专题", null, 2L)))
                .isInstanceOf(com.brad.pms.common.exception.BusinessException.class)
                .hasMessageContaining("专题流程模板");
        verify(operationLogService, never()).record(any());
    }

    @Test
    void rejectsTopicCreationForProjectsThatAreNotActive() {
        ProjectDO completed = project(2L, 2);
        when(projectMapper.selectById(2L)).thenReturn(completed);

        assertThatThrownBy(() -> service.create(update("专题", null, 2L)))
                .isInstanceOf(com.brad.pms.common.exception.BusinessException.class)
                .hasMessageContaining("进行中的项目");
        verify(topicMapper, never()).insert(any(ProjectNodeDevelopmentTopicDO.class));
    }

    @Test
    void rebindMovesAggregateAndWorkflowRootsButPreservesWorkflowSnapshotAndAssignments() {
        ProjectDO source = project(1L, 1);
        ProjectDO target = project(2L, 1);
        ProjectNodeDO targetNode = node(22L, 2L, "host");
        ProjectNodeDevelopmentTopicDO topic = topic(10L, 1L, 11L, 88L);
        ProjectNodeDevelopmentStoryDO story = story(30L, 10L, 1L, 11L, 99L);
        DevelopmentItemWorkflowDO topicWorkflow = workflow(1000L, "TOPIC", 10L, 1L, 11L, 501L);
        DevelopmentItemWorkflowDO storyWorkflow = workflow(2000L, "STORY", 30L, 1L, 11L, 502L);
        when(topicMapper.selectByIdForUpdate(10L)).thenReturn(topic);
        when(projectMapper.selectIncludingDeleted(1L)).thenReturn(source);
        when(projectMapper.selectById(2L)).thenReturn(target);
        when(permissionService.canManageProject(source)).thenReturn(true);
        when(permissionService.canManageProject(target)).thenReturn(true);
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("host");
        when(nodeMapper.selectOne(any())).thenReturn(targetNode);
        when(storyMapper.selectList(any())).thenReturn(List.of(story));
        when(storyMapper.selectByIdForUpdate(30L)).thenReturn(story);
        when(workflowMapper.selectForUpdate("TOPIC", 10L)).thenReturn(topicWorkflow);
        when(workflowMapper.selectForUpdate("STORY", 30L)).thenReturn(storyWorkflow);
        when(taskMapper.selectByWorkflowIdsForUpdate(any())).thenReturn(List.of());
        when(topicMapper.updateById(any(ProjectNodeDevelopmentTopicDO.class))).thenReturn(1);
        when(storyMapper.updateById(any(ProjectNodeDevelopmentStoryDO.class))).thenReturn(1);
        when(workflowMapper.updateById(any(DevelopmentItemWorkflowDO.class))).thenReturn(1);

        service.update(10L, update("专题调整名", 88L, 2L));

        assertThat(topic.getTitle()).isEqualTo("专题调整名");
        assertThat(topic.getProjectId()).isEqualTo(2L);
        assertThat(topic.getNodeId()).isEqualTo(22L);
        assertThat(topic.getOwnerId()).isEqualTo(88L);
        assertThat(topic.getMilestoneId()).isNull();
        assertThat(story.getProjectId()).isEqualTo(2L);
        assertThat(story.getNodeId()).isEqualTo(22L);
        assertThat(story.getIterationPlanId()).isNull();
        assertThat(story.getOwnerId()).isEqualTo(99L);
        assertThat(topicWorkflow.getTemplateVersionId()).isEqualTo(501L);
        assertThat(topicWorkflow.getProjectId()).isEqualTo(2L);
        assertThat(topicWorkflow.getSourceNodeId()).isEqualTo(22L);
        assertThat(storyWorkflow.getTemplateVersionId()).isEqualTo(502L);
        verify(storyMapper, never()).deleteById(any(Long.class));
        verify(workflowMapper, never()).deleteById(any(Long.class));
        verify(userService).requireActiveUser(88L);
        verify(projectMemberAssignmentService).synchronizeItemAssignments(1L, 2L,
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 10L);
        verify(projectMemberAssignmentService).synchronizeItemAssignments(1L, 2L,
                com.brad.pms.workflow.DevelopmentItemType.STORY, 30L);
        verify(taskMapper).selectByWorkflowIdsForUpdate(List.of(1000L, 2000L));
    }

    @Test
    void rejectsCompletedOrTerminatedTargetBeforeMovingTopic() {
        ProjectDO source = project(1L, 1);
        ProjectDO target = project(2L, 2);
        ProjectNodeDevelopmentTopicDO topic = topic(10L, 1L, 11L, 88L);
        when(topicMapper.selectByIdForUpdate(10L)).thenReturn(topic);
        when(projectMapper.selectIncludingDeleted(1L)).thenReturn(source);
        when(projectMapper.selectById(2L)).thenReturn(target);
        when(permissionService.canManageProject(source)).thenReturn(true);

        assertThatThrownBy(() -> service.update(10L, update("专题", 88L, 2L)))
                .isInstanceOf(com.brad.pms.common.exception.BusinessException.class)
                .hasMessageContaining("进行中的项目");
        verify(topicMapper, never()).updateById(any(ProjectNodeDevelopmentTopicDO.class));
    }

    @Test
    void rejectsTargetProjectWithoutTheConfiguredTopicHostNode() {
        ProjectDO source = project(1L, 1);
        ProjectDO target = project(2L, 1);
        ProjectNodeDevelopmentTopicDO topic = topic(10L, 1L, 11L, 88L);
        when(topicMapper.selectByIdForUpdate(10L)).thenReturn(topic);
        when(projectMapper.selectIncludingDeleted(1L)).thenReturn(source);
        when(projectMapper.selectById(2L)).thenReturn(target);
        when(permissionService.canManageProject(source)).thenReturn(true);
        when(permissionService.canManageProject(target)).thenReturn(true);
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("host");
        when(nodeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.update(10L, update("专题", 88L, 2L)))
                .isInstanceOf(com.brad.pms.common.exception.BusinessException.class)
                .hasMessageContaining("不包含当前专题流程绑定的项目节点");
        verify(topicMapper, never()).updateById(any(ProjectNodeDevelopmentTopicDO.class));
    }

    @Test
    void softDeleteAndRestoreToggleOnlyTheTopicAggregateMarker() {
        ProjectDO project = project(1L, 3);
        ProjectNodeDevelopmentTopicDO topic = topic(10L, 1L, 11L, null);
        ProjectNodeDevelopmentStoryDO story = story(30L, 10L, 1L, 11L, 99L);
        when(topicMapper.selectByIdForUpdate(10L)).thenReturn(topic, topic);
        when(projectMapper.selectIncludingDeleted(1L)).thenReturn(project);
        when(permissionService.canManageProject(project)).thenReturn(true);
        when(storyMapper.selectList(any())).thenReturn(List.of(story));
        when(storyMapper.selectByIdForUpdate(30L)).thenReturn(story);
        when(topicMapper.updateById(topic)).thenReturn(1);

        service.softDelete(10L);
        assertThat(topic.getDeleted()).isTrue();
        service.restore(10L);
        assertThat(topic.getDeleted()).isFalse();
        verify(storyMapper, never()).deleteById(any(Long.class));
        verify(workflowMapper, never()).deleteById(any(Long.class));
        verify(projectMemberAssignmentService).synchronizeItemAssignments(1L, 1L,
                com.brad.pms.workflow.DevelopmentItemType.TOPIC, 10L);
        verify(projectMemberAssignmentService).synchronizeItemAssignments(1L, 1L,
                com.brad.pms.workflow.DevelopmentItemType.STORY, 30L);
    }

    @Test
    void projectOptionsOnlyIncludeManageableActiveProjectsWithTheConfiguredNode() {
        ProjectDO active = project(1L, 1);
        ProjectDO completed = project(2L, 2);
        ProjectNodeDO host = node(11L, 1L, "host");
        when(workflowTemplateService.resolveDefaultForProcessType("topic-management"))
                .thenReturn(new WorkflowTemplateService.WorkflowTemplateBinding(null, null, null));
        when(projectService.listReadableIds()).thenReturn(List.of(1L, 2L));
        when(projectMapper.selectBatchIds(any())).thenReturn(List.of(active, completed));
        when(permissionService.canManageProject(active)).thenReturn(true);
        when(workflowTemplateService.resolveTopicSourceProjectNodeKey()).thenReturn("host");
        when(nodeMapper.selectList(any())).thenReturn(List.of(host));

        var result = service.projectOptions(new DevelopmentTopicProjectQry());

        assertThat(result.getList()).containsExactly(new com.brad.pms.dto.response.DevelopmentTopicProjectOptionDTO(
                1L, "PRJ-1", "项目1", "host", "开发测试与项目控制"));
    }

    @Test
    void projectOptionsStayEmptyUntilATopicWorkflowDefaultIsPublished() {
        when(workflowTemplateService.resolveDefaultForProcessType("topic-management")).thenReturn(null);

        var result = service.projectOptions(new DevelopmentTopicProjectQry());

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(projectService, never()).listReadableIds();
    }

    private static ProjectDO project(Long id, int status) {
        ProjectDO project = new ProjectDO();
        project.setId(id);
        project.setCode("PRJ-" + id);
        project.setName("项目" + id);
        project.setStatus(status);
        project.setDeleted(false);
        return project;
    }

    private static ProjectNodeDO node(Long id, Long projectId, String key) {
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(id);
        node.setProjectId(projectId);
        node.setNodeKey(key);
        node.setName("开发测试与项目控制");
        return node;
    }

    private static ProjectNodeDevelopmentTopicDO topic(Long id, Long projectId, Long nodeId, Long ownerId) {
        ProjectNodeDevelopmentTopicDO topic = new ProjectNodeDevelopmentTopicDO();
        topic.setId(id);
        topic.setProjectId(projectId);
        topic.setNodeId(nodeId);
        topic.setTitle("专题");
        topic.setOwnerId(ownerId);
        topic.setMilestoneId(100L);
        topic.setDeleted(false);
        return topic;
    }

    private static ProjectNodeDevelopmentStoryDO story(Long id, Long topicId, Long projectId, Long nodeId, Long ownerId) {
        ProjectNodeDevelopmentStoryDO story = new ProjectNodeDevelopmentStoryDO();
        story.setId(id);
        story.setTopicId(topicId);
        story.setProjectId(projectId);
        story.setNodeId(nodeId);
        story.setOwnerId(ownerId);
        story.setIterationPlanId(900L);
        return story;
    }

    private static DevelopmentItemWorkflowDO workflow(Long id, String type, Long itemId,
                                                       Long projectId, Long sourceNodeId, Long versionId) {
        DevelopmentItemWorkflowDO workflow = new DevelopmentItemWorkflowDO();
        workflow.setId(id);
        workflow.setItemType(type);
        workflow.setItemId(itemId);
        workflow.setProjectId(projectId);
        workflow.setSourceNodeId(sourceNodeId);
        workflow.setTemplateVersionId(versionId);
        return workflow;
    }

    private static DevelopmentTopicUpdateCmd update(String title, Long ownerId, Long projectId) {
        DevelopmentTopicUpdateCmd cmd = new DevelopmentTopicUpdateCmd();
        cmd.setTitle(title);
        cmd.setOwnerId(ownerId);
        cmd.setProjectId(projectId);
        return cmd;
    }

    private static void assertEndpoint(Class<?> controller, String name, Class<?> mappingType,
                                       String path, String permissionCode) {
        Optional<Method> method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst();
        assertThat(method).as("%s endpoint", name).isPresent();
        Object mapping = method.orElseThrow().getAnnotation((Class) mappingType);
        assertThat(mapping).isNotNull();
        String[] paths = mappingType == PutMapping.class ? ((PutMapping) mapping).value()
                : mappingType == DeleteMapping.class ? ((DeleteMapping) mapping).value()
                : ((PostMapping) mapping).value();
        assertThat(paths).containsExactly(path);
        RequirePermission permission = method.orElseThrow().getAnnotation(RequirePermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(permissionCode);
    }

    private static boolean hasMethod(Class<?> type, String name, int parameterCount) {
        return Arrays.stream(type.getMethods())
                .anyMatch(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount);
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
