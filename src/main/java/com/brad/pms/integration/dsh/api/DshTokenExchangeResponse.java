package com.brad.pms.integration.dsh.api;

import java.util.List;

public record DshTokenExchangeResponse(
        String token,
        int expiresInSeconds,
        String audience,
        List<String> scopes) {
}
