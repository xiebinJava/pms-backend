package com.brad.pms.auth;

import java.time.Instant;
import java.util.Optional;

public interface OidcStateStore {

    void put(String state, OidcPendingAuth pending);

    Optional<OidcPendingAuth> consume(String state, Instant now);
}
