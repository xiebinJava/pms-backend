package com.brad.pms.integration.dsh.api;

import java.util.List;

public record DshAuthorizationCodeIssueResponse(
        String authorizationCode,
        int expiresInSeconds,
        List<String> scopes) {
}
