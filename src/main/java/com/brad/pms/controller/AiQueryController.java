package com.brad.pms.controller;

import com.brad.pms.ai.query.AiTaskQueryRequest;
import com.brad.pms.ai.query.AiTaskQueryResult;
import com.brad.pms.ai.query.AiTaskQueryService;
import com.brad.pms.common.response.ResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/query")
@RequiredArgsConstructor
public class AiQueryController {

    private final AiTaskQueryService taskQueryService;

    @PostMapping("/tasks")
    public ResponseResult<AiTaskQueryResult> queryTasks(@RequestBody AiTaskQueryRequest request) {
        return ResponseResult.success(taskQueryService.query(request));
    }
}
