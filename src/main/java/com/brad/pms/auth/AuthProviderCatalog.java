package com.brad.pms.auth;

import com.brad.pms.dto.response.AuthProviderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthProviderCatalog {

    private static final Map<String, Integer> ORDER = Map.of("local", 0, "oidc", 1, "ldap", 2);

    private final List<AuthProvider> providers;

    public List<AuthProviderDTO> list() {
        return providers.stream()
                .sorted(Comparator.comparingInt(item -> ORDER.getOrDefault(item.type(), 99)))
                .map(AuthProvider::toDto)
                .toList();
    }
}
