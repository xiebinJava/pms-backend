package com.brad.pms.integration.dsh.api;

import java.util.Set;

public record DshAuthorizationCodeIssueRequest(
        String dshSessionId,
        String agentId,
        Set<String> scopes) {
}
