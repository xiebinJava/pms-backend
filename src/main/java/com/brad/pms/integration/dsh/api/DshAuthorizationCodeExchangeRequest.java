package com.brad.pms.integration.dsh.api;

import java.util.Set;

public record DshAuthorizationCodeExchangeRequest(
        String authorizationCode,
        String dshSessionId,
        String agentId,
        Set<String> scopes) {
}
