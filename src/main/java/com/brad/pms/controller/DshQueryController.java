package com.brad.pms.controller;

import com.brad.pms.ai.query.DshQueryRequest;
import com.brad.pms.ai.query.DshQueryResult;
import com.brad.pms.ai.query.DshQueryService;
import com.brad.pms.common.response.ResponseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integration/dsh/v1")
@RequiredArgsConstructor
public class DshQueryController {

    private final DshQueryService queryService;

    @PostMapping("/query")
    public ResponseResult<DshQueryResult> query(@RequestBody DshQueryRequest request) {
        return ResponseResult.success(queryService.query(request));
    }
}
