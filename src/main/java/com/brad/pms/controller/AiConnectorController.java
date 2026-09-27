package com.brad.pms.controller;

import com.brad.pms.ai.connector.AutomaticCommandExecutionService;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.ai.api.AiAutomaticExecuteRequest;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.security.AiConnectorScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Neutral write facade consumed by MCP and OpenCLI adapters. */
@RestController
@RequestMapping("/integration/ai/v1")
@RequiredArgsConstructor
public class AiConnectorController {

    private final AutomaticCommandExecutionService executionService;

    @PostMapping("/operations/execute")
    public ResponseResult<AiOperationResultDTO> execute(@RequestBody AiAutomaticExecuteRequest request) {
        if (request == null) throw new BusinessException("自动执行请求不能为空");
        AiConnectorScopePolicy.requireClient(request.clientId());
        return ResponseResult.success(AiOperationResultDTO.from(executionService.execute(
                request.toOperationRequest())));
    }
}
