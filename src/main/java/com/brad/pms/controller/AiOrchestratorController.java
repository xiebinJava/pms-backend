package com.brad.pms.controller;

import com.brad.pms.ai.orchestrator.api.AiOrchestratorRequest;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorService;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.ai.security.AiConnectorScopePolicy;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Preview-first orchestration facade for external agents and the PMS CLI. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/integration/ai/v1/orchestrator")
public class AiOrchestratorController {

    private final AiOrchestratorService service;

    @PostMapping("/conversations")
    public ResponseResult<AiOrchestratorResponse.ConversationView> createConversation(
            @RequestHeader(value = "x-client-id", required = false) String clientId,
            @RequestBody(required = false) AiOrchestratorRequest.CreateConversationRequest request) {
        requireClient(clientId);
        return ResponseResult.success(service.createConversation(UserContext.userId(),
                request == null ? null : new AiOrchestratorRequest.CreateConversationCommand(
                        request.resourceType(), request.resourceId())));
    }

    @PostMapping("/conversations/{conversationId}/turns")
    public ResponseResult<AiOrchestratorResponse.PlanView> appendTurn(
            @RequestHeader(value = "x-client-id", required = false) String clientId,
            @PathVariable Long conversationId,
            @RequestBody(required = false) AiOrchestratorRequest.AppendTurnRequest request) {
        requireClient(clientId);
        return ResponseResult.success(service.appendTurn(UserContext.userId(), conversationId,
                request == null ? null : new AiOrchestratorRequest.AppendTurnCommand(
                        request.message(), request.contractVersion(), request.draft(), request.nodeId(),
                        request.contextVersion(), request.requestId(),
                        request.nodeVersion(), request.componentKey())));
    }

    @PostMapping("/plans/{planId}/preview")
    public ResponseResult<AiOrchestratorResponse.PlanView> preview(
            @RequestHeader(value = "x-client-id", required = false) String clientId,
            @PathVariable Long planId) {
        requireClient(clientId);
        return ResponseResult.success(service.preview(UserContext.userId(), planId));
    }

    @PostMapping("/plans/{planId}/confirm")
    public ResponseResult<AiOrchestratorResponse.PlanView> confirm(
            @RequestHeader(value = "x-client-id", required = false) String clientId,
            @PathVariable Long planId,
            @RequestBody(required = false) AiOrchestratorRequest.ConfirmPlanRequest request) {
        requireClient(clientId);
        return ResponseResult.success(service.confirm(UserContext.userId(), planId,
                request == null ? null : request.idempotencyKey()));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseResult<AiOrchestratorResponse.ConversationView> conversation(
            @RequestHeader(value = "x-client-id", required = false) String clientId,
            @PathVariable Long conversationId) {
        requireClient(clientId);
        return ResponseResult.success(service.getConversation(UserContext.userId(), conversationId));
    }

    @GetMapping("/conversations/{conversationId}/events")
    public ResponseResult<List<AiOrchestratorResponse.OperationEventView>> events(
            @RequestHeader(value = "x-client-id", required = false) String clientId,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") long afterSequence) {
        requireClient(clientId);
        return ResponseResult.success(service.listEvents(UserContext.userId(), conversationId, afterSequence));
    }

    @GetMapping("/conversations/{conversationId}/session")
    public ResponseResult<AiOrchestratorResponse.SessionView> session(
            @RequestHeader(value = "x-client-id", required = false) String clientId,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") long afterSequence) {
        requireClient(clientId);
        return ResponseResult.success(service.getSession(UserContext.userId(), conversationId, afterSequence));
    }

    private void requireClient(String clientId) {
        AiConnectorScopePolicy.requireClient(clientId);
    }
}
