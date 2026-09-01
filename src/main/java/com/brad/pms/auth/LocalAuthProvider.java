package com.brad.pms.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalAuthProvider implements AuthProvider {

    private final AuthProviderProperties properties;

    @Override
    public String type() {
        return "local";
    }

    @Override
    public boolean enabled() {
        return properties.isLocalEnabled();
    }

    @Override
    public String displayName() {
        return "local";
    }

    @Override
    public String startPath() {
        return null;
    }
}
