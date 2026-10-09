package com.brad.pms.controller;

import com.brad.pms.ai.connector.AutomaticCommandExecutionService;
import com.brad.pms.ai.connector.AiConnectorCapabilityService;
import com.brad.pms.ai.connector.AiConnectorQueryService;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.ai.api.AiAutomaticExecuteRequest;
import com.brad.pms.integration.ai.api.AiCapabilityDTO;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import com.brad.pms.integration.ai.api.AiOperationPreviewDTO;
import com.brad.pms.integration.ai.api.AiOperationPreviewRequest;
import com.brad.pms.integration.ai.api.AiQueryRequest;
import com.brad.pms.integration.ai.api.AiQueryResultDTO;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.brad.pms.integration.ai.security.AiConnectorScopePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

/** Stable integration facade consumed by the PMS CLI and legacy adapters during migration. */
@RestController
@RequestMapping("/integration/ai/v1")
public class AiConnectorController {

    private final AutomaticCommandExecutionService executionService;
    private final AiConnectorCapabilityService capabilityService;
    private final AiConnectorQueryService queryService;
    private final CommandPreviewService previewService;

    public AiConnectorController(AutomaticCommandExecutionService executionService) {
        this(executionService, null, null, null);
    }

    @Autowired
    public AiConnectorController(AutomaticCommandExecutionService executionService,
                                 AiConnectorCapabilityService capabilityService,
                                 AiConnectorQueryService queryService,
                                 CommandPreviewService previewService) {
        this.executionService = executionService;
        this.capabilityService = capabilityService;
        this.queryService = queryService;
        this.previewService = previewService;
    }

    @GetMapping("/capabilities")
    public ResponseResult<AiCapabilityDTO> capabilities() {
        return ResponseResult.success(capabilityService.capabilities());
    }

    @PostMapping("/query")
    public ResponseResult<AiQueryResultDTO> query(@RequestBody AiQueryRequest request) {
        return ResponseResult.success(queryService.query(request));
    }

    @GetMapping("/context/{resourceType}/{resourceId}")
    public ResponseResult<AiWorkflowContextDTO> context(@PathVariable String resourceType,
                                                        @PathVariable Long resourceId) {
        return ResponseResult.success(queryService.context(resourceType, resourceId));
    }

    @PostMapping("/operations/preview")
    public ResponseResult<AiOperationPreviewDTO> preview(@RequestBody AiOperationPreviewRequest request) {
        if (request == null) throw new BusinessException("操作预览请求不能为空");
        AiConnectorScopePolicy.requireClient(request.clientId());
        return ResponseResult.success(AiOperationPreviewDTO.from(
                previewService.preview(request.toCommandPreviewRequest())));
    }

    @PostMapping("/operations/execute")
    public ResponseResult<AiOperationResultDTO> execute(@RequestBody AiAutomaticExecuteRequest request) {
        if (request == null) throw new BusinessException("自动执行请求不能为空");
        AiConnectorScopePolicy.requireClient(request.clientId());
        return ResponseResult.success(AiOperationResultDTO.from(executionService.execute(
                request.toOperationRequest())));
    }
}
