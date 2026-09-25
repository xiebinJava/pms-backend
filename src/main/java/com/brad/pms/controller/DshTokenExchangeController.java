package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshTokenExchangeRequest;
import com.brad.pms.integration.dsh.api.DshTokenExchangeResponse;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

/**
 * Server-to-server exchange endpoint for DSH. It deliberately does not accept
 * a user id: the authenticated PMS access token is the source of identity.
 */
@RestController
@RequestMapping("/integration/dsh/v1/token")
public class DshTokenExchangeController {

    private static final int EXPIRES_IN_SECONDS = 120;
    /**
     * Scopes this exchange will mint. It mirrors the first-party Agent policy
     * ({@code DshAgentScopePolicy.PROJECT_ASSISTANT_SCOPES}) so a DSH session that
     * authenticated through SSO can use the same assistant capabilities as one
     * bridged from an embedded PMS page; PMS still authorises every call.
     */
    private static final Set<String> SUPPORTED_SCOPES = Set.copyOf(
            com.brad.pms.integration.dsh.security.DshAgentScopePolicy.PROJECT_ASSISTANT_SCOPES);

    private final JwtTokenProvider tokenProvider;
    private final String serviceKey;

    public DshTokenExchangeController(JwtTokenProvider tokenProvider,
                                      @Value("${pms.dsh.service-key:}") String serviceKey) {
        this.tokenProvider = tokenProvider;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    @PostMapping
    public ResponseResult<DshTokenExchangeResponse> exchange(
            @RequestHeader(value = "X-DSH-Service-Key", required = false) String suppliedServiceKey,
            @RequestBody(required = false) DshTokenExchangeRequest request) {
        if (!matchesServiceKey(suppliedServiceKey)) {
            throw BusinessException.forbidden("DSH 服务鉴权失败");
        }
        LoginUser user = UserContext.get();
        if (user == null || user.getId() == null || user.getSessionId() == null) {
            throw BusinessException.unauthorized("PMS 登录会话不可用");
        }
        DshTokenExchangeRequest safeRequest = request == null
                ? new DshTokenExchangeRequest(null, null, null) : request;
        Set<String> scopes = safeRequest.scopes() == null || safeRequest.scopes().isEmpty()
                ? SUPPORTED_SCOPES : Set.copyOf(safeRequest.scopes());
        if (!SUPPORTED_SCOPES.containsAll(scopes)) {
            throw BusinessException.forbidden("请求的 DSH 权限范围未获允许");
        }
        return ResponseResult.success(new DshTokenExchangeResponse(
                tokenProvider.createDshDelegationToken(
                        user.getId(), user.getSessionId(), safeRequest.dshSessionId(), safeRequest.agentId(), scopes),
                EXPIRES_IN_SECONDS,
                "dsh-pms",
                scopes.stream().sorted().toList()));
    }

    private boolean matchesServiceKey(String suppliedServiceKey) {
        if (serviceKey.isBlank() || suppliedServiceKey == null || suppliedServiceKey.isBlank()) return false;
        return MessageDigest.isEqual(
                serviceKey.getBytes(StandardCharsets.UTF_8),
                suppliedServiceKey.trim().getBytes(StandardCharsets.UTF_8));
    }
}
