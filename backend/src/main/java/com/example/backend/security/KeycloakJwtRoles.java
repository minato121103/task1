package com.example.backend.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class KeycloakJwtRoles {

    private KeycloakJwtRoles() {
    }

    public static List<String> extract(Jwt jwt) {
        return Stream.concat(getRealmRoles(jwt).stream(), getClientRoles(jwt).stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<String> getRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> rawRoles)) {
            return List.of();
        }

        return rawRoles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> getClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }

        return resourceAccess.values().stream()
                .filter(Map.class::isInstance)
                .map(clientAccess -> (Map<String, Object>) clientAccess)
                .map(clientAccess -> clientAccess.get("roles"))
                .filter(List.class::isInstance)
                .flatMap(rawRoles -> ((List<?>) rawRoles).stream())
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
