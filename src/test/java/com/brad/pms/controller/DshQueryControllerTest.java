package com.brad.pms.controller;

import com.brad.pms.ai.query.DshQueryResult;
import com.brad.pms.ai.query.DshQueryService;
import com.brad.pms.common.response.ResponseResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DshQueryControllerTest {

    @Test
    void delegatesTheGenericQueryToThePermissionAwareService() {
        DshQueryService service = mock(DshQueryService.class);
        DshQueryResult expected = new DshQueryResult(
                "projects", true, Instant.now(), Map.of("projects", List.of()), Map.of());
        when(service.query(any())).thenReturn(expected);

        ResponseResult<DshQueryResult> response = new DshQueryController(service).query(
                new com.brad.pms.ai.query.DshQueryRequest("projects", Map.of(), List.of(), 1, 50));

        assertThat(response.getData()).isSameAs(expected);
    }
}
