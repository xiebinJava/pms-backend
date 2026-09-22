package com.brad.pms.integration.dsh.api;

/** The ID Token DSH obtained from SSO for the current browser session. */
public record DshSsoSessionRequest(String idToken) {
}
