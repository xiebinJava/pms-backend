package com.brad.pms.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryOidcStateStore implements OidcStateStore {

    private final ConcurrentHashMap<String, OidcPendingAuth> pending = new ConcurrentHashMap<>();

    @Override
    public void put(String state, OidcPendingAuth value) {
        pending.put(state, value);
    }

    @Override
    public Optional<OidcPendingAuth> consume(String state, Instant now) {
        OidcPendingAuth value = pending.remove(state);
        if (value == null || value.expiresAt().isBefore(now)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
