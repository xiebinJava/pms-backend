package com.brad.pms.controller;

import com.brad.pms.ai.query.AiTaskQueryRequest;
import com.brad.pms.ai.query.AiTaskQueryResult;
import com.brad.pms.ai.query.AiTaskQueryService;
import com.brad.pms.common.response.ResponseResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.same;

class AiQueryControllerTest {

    @Test
    void queryTasksDelegatesToServiceAndKeepsResponseEnvelope() {
        AiTaskQueryService service = mock(AiTaskQueryService.class);
        AiQueryController controller = new AiQueryController(service);
        AiTaskQueryRequest request = new AiTaskQueryRequest("mine", "today", "open", null, null, 1, 50);
        AiTaskQueryResult result = new AiTaskQueryResult(
                "task-query", true, "Asia/Shanghai", LocalDate.now(), 0, 1, 50, 0,
                new AiTaskQueryResult.Pagination(1, 50, 0), List.of());
        when(service.query(same(request))).thenReturn(result);

        ResponseResult<AiTaskQueryResult> response = controller.queryTasks(request);

        verify(service).query(same(request));
        assertThat(response.getData()).isSameAs(result);
    }
}
