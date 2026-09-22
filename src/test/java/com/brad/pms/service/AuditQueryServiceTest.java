package com.brad.pms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.brad.pms.dto.response.AuditLogDTO;
import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.mapper.ProjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditQueryServiceTest {
    @Test
    void getsSystemAuditRowWithoutOperatorOrProject() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        UserService userService = mock(UserService.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        OperationLogDO row = new OperationLogDO();
        row.setId(7L);
        row.setAction("AUDIT_RETENTION_RUN");
        row.setResourceType("AUDIT");
        row.setResult("SUCCESS");
        when(operationLogMapper.selectById(7L)).thenReturn(row);

        AuditLogDTO result = new AuditQueryService(operationLogMapper, userService, projectMapper).get(7L);

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getOperatorDisplayName()).isNull();
        assertThat(result.getProjectName()).isNull();
    }

    @Test
    void listsSystemAuditRowsWithoutOperatorOrProject() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        UserService userService = mock(UserService.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        OperationLogDO row = new OperationLogDO();
        row.setId(6L);
        row.setAction("AUTHORIZATION_DENIED");
        row.setResourceType("USER");
        row.setResult("FAILURE");
        Page<OperationLogDO> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(row));
        when(operationLogMapper.selectPage(any(), any())).thenReturn(page);

        AuditLogDTO result = new AuditQueryService(operationLogMapper, userService, projectMapper)
                .page(new AuditQueryService.Query(null, null, null, null, null, null, null, null, null, 1, 20))
                .getList().get(0);

        assertThat(result.getId()).isEqualTo(6L);
        assertThat(result.getOperatorDisplayName()).isNull();
        assertThat(result.getProjectName()).isNull();
    }

    @Test
    void enrichesAuditRowsWithOperatorAndDeletedProjectContext() {
        OperationLogMapper operationLogMapper = mock(OperationLogMapper.class);
        UserService userService = mock(UserService.class);
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        OperationLogDO row = new OperationLogDO();
        row.setId(5L);
        row.setOperatorId(7L);
        row.setAction("PROJECT_DELETED");
        row.setResourceType("PROJECT");
        row.setResourceId(9L);
        row.setProjectId(9L);
        row.setResult("SUCCESS");
        row.setRequestId("req-5");
        row.setBeforeJson("{\"status\":1}");
        row.setAfterJson("{\"status\":4}");
        Page<OperationLogDO> page = new Page<>(1, 20);
        page.setTotal(1);
        page.setRecords(List.of(row));
        when(operationLogMapper.selectPage(any(), any())).thenReturn(page);
        UserDO user = new UserDO();
        user.setId(7L);
        user.setNameZh("审计员");
        user.setUsername("audit");
        when(userService.listByIds(List.of(7L))).thenReturn(List.of(user));
        ProjectDO deleted = new ProjectDO();
        deleted.setId(9L);
        deleted.setName("已删除项目");
        deleted.setStatus(4);
        when(projectMapper.selectIncludingDeletedByIds(List.of(9L))).thenReturn(List.of(deleted));

        AuditLogDTO result = new AuditQueryService(operationLogMapper, userService, projectMapper)
                .page(new AuditQueryService.Query(null, null, null, null, 9L, "SUCCESS", "req-5", null, null, 1, 20))
                .getList().get(0);

        assertThat(result.getOperatorDisplayName()).isEqualTo("审计员（audit）");
        assertThat(result.getProjectName()).isEqualTo("已删除项目");
        assertThat(result.getProjectId()).isEqualTo(9L);
        assertThat(result.getRequestId()).isEqualTo("req-5");
        assertThat(result.getAfterJson()).contains("status");
    }
}
