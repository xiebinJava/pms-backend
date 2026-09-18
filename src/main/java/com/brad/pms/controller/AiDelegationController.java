package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.security.JwtTokenProvider;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiDelegationController {

    private static final Set<String> SCOPES = Set.of(
            "ai:context:read", "ai:query:read", "ai:command:preview");
    private static final Set<String> AGENT_CONFIG_SCOPES = Set.of("ai:agent:configure");

    private final JwtTokenProvider tokenProvider;

    @PostMapping("/delegation")
    public ResponseResult<DelegationResponse> issue() {
        return issue(SCOPES);
    }

    @PostMapping("/agent-configuration-delegation")
    @RequirePermission(PermissionCode.WORKFLOW_WRITE)
    public ResponseResult<DelegationResponse> issueAgentConfiguration() {
        return issue(AGENT_CONFIG_SCOPES);
    }

    private ResponseResult<DelegationResponse> issue(Set<String> scopes) {
        LoginUser user = UserContext.get();
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("未登录");
        }
        return ResponseResult.success(new DelegationResponse(
                tokenProvider.createAiDelegationToken(user.getId(), user.getSessionId(), scopes),
                120,
                scopes.stream().sorted().toList()));
    }

    public record DelegationResponse(String token, int expiresInSeconds, java.util.List<String> scopes) {
    }
}
