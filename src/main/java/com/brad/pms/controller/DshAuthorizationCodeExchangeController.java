package com.brad.pms.controller;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeExchangeRequest;
import com.brad.pms.integration.dsh.api.DshTokenExchangeResponse;
import com.brad.pms.integration.dsh.service.DshAuthorizationCodeService;
import com.brad.pms.security.IgnoreAuth;
import com.brad.pms.security.JwtTokenProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/** Exchanges a one-time browser code from the trusted DSH Host process. */
@RestController
@RequestMapping("/integration/dsh/v1/token")
public class DshAuthorizationCodeExchangeController {

    private static final int EXPIRES_IN_SECONDS = 120;

    private final JwtTokenProvider tokenProvider;
    private final DshAuthorizationCodeService authorizationCodeService;
    private final String serviceKey;

    public DshAuthorizationCodeExchangeController(
            JwtTokenProvider tokenProvider,
            DshAuthorizationCodeService authorizationCodeService,
            @Value("${pms.dsh.service-key:}") String serviceKey) {
        this.tokenProvider = tokenProvider;
        this.authorizationCodeService = authorizationCodeService;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
    }

    @IgnoreAuth
    @PostMapping("/exchange")
    public ResponseResult<DshTokenExchangeResponse> exchange(
            @RequestHeader(value = "X-DSH-Service-Key", required = false) String suppliedServiceKey,
            @RequestBody(required = false) DshAuthorizationCodeExchangeRequest request) {
        if (!matchesServiceKey(suppliedServiceKey)) {
            throw BusinessException.forbidden("PMS_DSH_SERVICE_AUTH_FAILED");
        }
        DshAuthorizationCodeService.ConsumedAuthorizationCode consumed = authorizationCodeService.consume(request);
        String token = tokenProvider.createDshDelegationToken(
                consumed.userId(), consumed.pmsSessionId(), consumed.dshSessionId(),
                consumed.agentId(), Set.copyOf(consumed.scopes()));
        return ResponseResult.success(new DshTokenExchangeResponse(
                token, EXPIRES_IN_SECONDS, "dsh-pms", consumed.scopes()));
    }

    private boolean matchesServiceKey(String suppliedServiceKey) {
        if (serviceKey.isBlank() || suppliedServiceKey == null || suppliedServiceKey.isBlank()) return false;
        return MessageDigest.isEqual(
                serviceKey.getBytes(StandardCharsets.UTF_8),
                suppliedServiceKey.trim().getBytes(StandardCharsets.UTF_8));
    }
}
