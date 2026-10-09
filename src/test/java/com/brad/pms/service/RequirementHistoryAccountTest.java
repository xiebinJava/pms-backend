package com.brad.pms.service;

import com.brad.pms.common.enums.RequirementExecutionTargetType;
import com.brad.pms.entity.RequirementExecutionTargetHistoryDO;
import com.brad.pms.mapper.RequirementExecutionTargetHistoryMapper;
import com.brad.pms.mapper.RequirementMapper;
import com.brad.pms.mapper.ProjectMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentStoryMapper;
import com.brad.pms.mapper.ProjectNodeDevelopmentTopicMapper;
import com.brad.pms.entity.UserDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementHistoryAccountTest {
    @Mock RequirementMapper requirementMapper;
    @Mock RequirementExecutionTargetHistoryMapper historyMapper;
    @Mock ProjectMapper projectMapper;
    @Mock ProjectNodeDevelopmentTopicMapper topicMapper;
    @Mock ProjectNodeDevelopmentStoryMapper storyMapper;
    @Mock ProjectPermissionService permissionService;
    @Mock UserService userService;
    @Mock OperationLogService operationLogService;
    @InjectMocks RequirementExecutionTargetService service;

    @Test
    void keepsInactiveOperatorVisibleInHistory() {
        RequirementExecutionTargetHistoryDO history = new RequirementExecutionTargetHistoryDO();
        history.setId(41L);
        history.setRequirementId(9L);
        history.setAction("UNLINK");
        history.setPreviousTargetType(RequirementExecutionTargetType.PROJECT);
        history.setPreviousTargetId(78L);
        history.setOperatorId(99L);
        UserDO inactive = new UserDO();
        inactive.setId(99L);
        inactive.setUsername("former-owner");
        when(historyMapper.selectList(any())).thenReturn(List.of(history));
        when(userService.listByIdsIncludingDeleted(List.of(99L))).thenReturn(List.of(inactive));

        var result = service.history(9L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperatorId()).isEqualTo(99L);
        assertThat(result.get(0).getOperatorName()).isNotBlank();
    }
}
