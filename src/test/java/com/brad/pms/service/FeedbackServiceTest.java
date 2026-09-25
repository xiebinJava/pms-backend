package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.dto.request.FeedbackCreateCmd;
import com.brad.pms.dto.request.FeedbackReopenCmd;
import com.brad.pms.dto.request.FeedbackUpdateCmd;
import com.brad.pms.entity.FeedbackHistoryDO;
import com.brad.pms.entity.FeedbackTicketDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.ProjectNodeDO;
import com.brad.pms.entity.UserPositionDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.FeedbackHistoryMapper;
import com.brad.pms.mapper.FeedbackTicketMapper;
import com.brad.pms.mapper.ProjectMemberMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.mapper.UserPositionMapper;
import com.brad.pms.mapper.UserMapper;
import com.brad.pms.security.AuthorizationService;
import com.brad.pms.security.DataScopeResolver;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock FeedbackTicketMapper ticketMapper;
    @Mock FeedbackHistoryMapper historyMapper;
    @Mock UserMapper userMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock ProjectNodeMapper nodeMapper;
    @Mock ProjectMemberMapper projectMemberMapper;
    @Mock UserPositionMapper userPositionMapper;
    @Mock DataScopeResolver dataScopeResolver;
    @Mock ProjectPermissionService projectPermissionService;
    @Mock AuthorizationService authorizationService;
    @Mock OperationLogService operationLogService;

    @InjectMocks FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, FeedbackTicketDO.class);
        TableInfoHelper.initTableInfo(assistant, FeedbackHistoryDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectDO.class);
        TableInfoHelper.initTableInfo(assistant, ProjectMemberDO.class);
        TableInfoHelper.initTableInfo(assistant, UserPositionDO.class);
        UserContext.set(new LoginUser(7L, "alex.zhang", "张伟"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createIsIdempotentAndReturnsExistingTicketDetails() {
        FeedbackTicketDO existing = ticket(18L, "FB-20260902-EXISTING");
        when(ticketMapper.findByReporterAndClientRequestId(7L, "request-1")).thenReturn(existing);
        when(ticketMapper.selectById(18L)).thenReturn(existing);

        FeedbackCreateCmd cmd = new FeedbackCreateCmd();
        cmd.setTitle("重复点击");
        cmd.setContent("按钮反馈被重复提交");
        cmd.setFeedbackType("BUG");
        cmd.setClientRequestId("request-1");
        when(historyMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());

        var result = feedbackService.create(cmd);

        assertThat(result.getId()).isEqualTo(18L);
        assertThat(result.getTicketNo()).isEqualTo("FB-20260902-EXISTING");
        verify(ticketMapper, never()).insert(any(FeedbackTicketDO.class));
        verify(historyMapper, never()).insert(any(FeedbackHistoryDO.class));
    }

    @Test
    void updateRejectsInvalidStatusTransition() {
        FeedbackTicketDO ticket = ticket(18L, "FB-20260902-EXISTING");
        when(ticketMapper.selectForUpdate(18L)).thenReturn(ticket);
        doNothing().when(authorizationService).require(PermissionCode.FEEDBACK_MANAGE);

        FeedbackUpdateCmd cmd = new FeedbackUpdateCmd();
        cmd.setVersion(0);
        cmd.setStatus("CLOSED");

        assertThatThrownBy(() -> feedbackService.update(18L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不允许");
        verify(ticketMapper, never()).updateById(any(FeedbackTicketDO.class));
    }

    @Test
    void updateRejectsStaleOptimisticLockVersion() {
        FeedbackTicketDO ticket = ticket(18L, "FB-20260902-EXISTING");
        ticket.setVersion(3);
        when(ticketMapper.selectForUpdate(18L)).thenReturn(ticket);
        doNothing().when(authorizationService).require(PermissionCode.FEEDBACK_MANAGE);

        FeedbackUpdateCmd cmd = new FeedbackUpdateCmd();
        cmd.setVersion(2);
        cmd.setStatus("ASSIGNED");

        assertThatThrownBy(() -> feedbackService.update(18L, cmd))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessException.ResponseCode.CONFLICT);
        verify(ticketMapper, never()).updateById(any(FeedbackTicketDO.class));
    }

    @Test
    void managerListAddsScopedVisibilityPredicate() {
        when(authorizationService.has(PermissionCode.FEEDBACK_MANAGE)).thenReturn(true);
        when(dataScopeResolver.hasAllCompanyScope(any(), eq(PermissionCode.FEEDBACK_MANAGE))).thenReturn(false);
        when(dataScopeResolver.resolveOrgUnitIds(any(), eq(PermissionCode.FEEDBACK_MANAGE))).thenReturn(List.of(42L));
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of());
        IPage<FeedbackTicketDO> result = new Page<>(1, 10);
        when(ticketMapper.selectPage(any(), any())).thenReturn(result);

        feedbackService.page(new com.brad.pms.dto.request.FeedbackPageQry());

        var captor = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(ticketMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("projectId");
    }

    @Test
    void managerCannotReadTicketOutsideOrganizationScope() {
        FeedbackTicketDO ticket = ticket(18L, "FB-20260902-OUTSIDE");
        ticket.setReporterId(8L);
        ticket.setProjectId(99L);
        when(ticketMapper.selectById(18L)).thenReturn(ticket);
        when(authorizationService.has(PermissionCode.FEEDBACK_MANAGE)).thenReturn(true);
        when(dataScopeResolver.hasAllCompanyScope(any(), eq(PermissionCode.FEEDBACK_MANAGE))).thenReturn(false);
        when(dataScopeResolver.resolveOrgUnitIds(any(), eq(PermissionCode.FEEDBACK_MANAGE))).thenReturn(List.of(42L));
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> feedbackService.detail(18L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessException.ResponseCode.FORBIDDEN);
    }

    @Test
    void updateKeepsClosedAtWhenTicketStaysClosed() {
        FeedbackTicketDO ticket = ticket(18L, "FB-20260902-CLOSED");
        java.time.LocalDateTime closedAt = java.time.LocalDateTime.of(2026, 9, 1, 10, 0);
        ticket.setStatus("CLOSED");
        ticket.setClosedAt(closedAt);
        ticket.setResolutionNote("已关闭");
        when(ticketMapper.selectForUpdate(18L)).thenReturn(ticket);
        when(ticketMapper.selectById(18L)).thenReturn(ticket);
        when(ticketMapper.updateById(any(FeedbackTicketDO.class))).thenReturn(1);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());
        doNothing().when(authorizationService).require(PermissionCode.FEEDBACK_MANAGE);

        FeedbackUpdateCmd cmd = new FeedbackUpdateCmd();
        cmd.setVersion(0);
        cmd.setStatus("CLOSED");
        cmd.setPriority("NORMAL");
        cmd.setResolutionNote("已关闭");

        feedbackService.update(18L, cmd);

        assertThat(ticket.getClosedAt()).isEqualTo(closedAt);
    }

    @Test
    void reopenClearsPreviousResolutionNoteWhenNoNewNoteIsProvided() {
        FeedbackTicketDO ticket = ticket(18L, "FB-20260902-REOPEN");
        ticket.setStatus("RESOLVED");
        ticket.setAssigneeId(8L);
        ticket.setResolutionNote("旧处理说明");
        UserDO assignee = new UserDO();
        assignee.setId(8L);
        assignee.setStatus(UserStatus.ACTIVE.name());
        when(ticketMapper.selectForUpdate(18L)).thenReturn(ticket);
        when(ticketMapper.selectById(18L)).thenReturn(ticket);
        when(ticketMapper.updateById(any(FeedbackTicketDO.class))).thenReturn(1);
        when(userMapper.selectById(8L)).thenReturn(assignee);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(assignee));
        FeedbackReopenCmd cmd = new FeedbackReopenCmd();
        cmd.setVersion(0);

        feedbackService.reopen(18L, cmd);

        assertThat(ticket.getResolutionNote()).isNull();
    }

    @Test
    void rejectsTaskAndNodeWhenTaskHasNoMatchingNode() {
        FeedbackCreateCmd cmd = new FeedbackCreateCmd();
        cmd.setTitle("上下文校验");
        cmd.setContent("任务节点关系不一致");
        cmd.setFeedbackType("BUG");
        cmd.setProjectId(5L);
        cmd.setTaskId(7L);
        cmd.setNodeId(8L);
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(7L);
        task.setProjectId(5L);
        task.setNodeId(null);
        ProjectNodeDO node = new ProjectNodeDO();
        node.setId(8L);
        node.setProjectId(5L);
        when(taskMapper.selectById(7L)).thenReturn(task);
        when(projectPermissionService.requireNode(5L, 8L)).thenReturn(node);
        when(projectPermissionService.requireProject(5L)).thenReturn(new ProjectDO());

        assertThatThrownBy(() -> feedbackService.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("节点与任务");
        verify(ticketMapper, never()).insert(any(FeedbackTicketDO.class));
    }

    @Test
    void auditSnapshotDoesNotContainFeedbackContentOrSourceUrl() {
        FeedbackCreateCmd cmd = new FeedbackCreateCmd();
        cmd.setTitle("敏感标题");
        cmd.setContent("敏感正文");
        cmd.setFeedbackType("BUG");
        cmd.setSourceUrl("https://example.test/path?token=secret");
        cmd.setClientRequestId("client-secret");
        doAnswer(invocation -> {
            FeedbackTicketDO inserted = invocation.getArgument(0);
            inserted.setId(9L);
            return 1;
        }).when(ticketMapper).insert(any(FeedbackTicketDO.class));
        when(ticketMapper.selectById(9L)).thenAnswer(invocation -> {
            FeedbackTicketDO found = new FeedbackTicketDO();
            found.setId(9L);
            found.setTicketNo("FB-20260902-AUDIT");
            found.setTitle("敏感标题");
            found.setContent("敏感正文");
            found.setFeedbackType("BUG");
            found.setPriority("NORMAL");
            found.setStatus("PENDING_TRIAGE");
            found.setReporterId(7L);
            found.setVersion(0);
            found.setDeleted(false);
            return found;
        });
        when(userMapper.selectBatchIds(any())).thenReturn(List.of());

        feedbackService.create(cmd);

        var after = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(operationLogService).record(eq("FEEDBACK_CREATED"), eq("FEEDBACK_TICKET"), eq(9L), isNull(), after.capture());
        assertThat(after.getValue()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked") Map<String, Object> snapshot = (Map<String, Object>) after.getValue();
        assertThat(snapshot).doesNotContainKeys("title", "content", "sourceUrl", "clientRequestId", "resolutionNote");
    }

    private static FeedbackTicketDO ticket(Long id, String ticketNo) {
        FeedbackTicketDO ticket = new FeedbackTicketDO();
        ticket.setId(id);
        ticket.setTicketNo(ticketNo);
        ticket.setTitle("已有反馈");
        ticket.setContent("反馈内容");
        ticket.setFeedbackType("BUG");
        ticket.setPriority("NORMAL");
        ticket.setStatus("PENDING_TRIAGE");
        ticket.setReporterId(7L);
        ticket.setVersion(0);
        ticket.setDeleted(false);
        return ticket;
    }
}
