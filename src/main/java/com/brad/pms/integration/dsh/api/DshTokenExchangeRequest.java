package com.brad.pms.integration.dsh.api;

import java.util.Set;

/** Request from the authenticated DSH server to mint a short-lived PMS token. */
public record DshTokenExchangeRequest(
        String dshSessionId,
        String agentId,
        Set<String> scopes) {
}
