package com.example.backend.service.impl;

import com.example.backend.controller.dto.UserProfileResponse;
import com.example.backend.model.UserEntity;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.KeycloakJwtRoles;
import com.example.backend.service.UserProfileService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private static final String DEFAULT_POSITION = "Position is not configured";

    private final UserRepository userRepository;

    public UserProfileServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserProfileResponse getCurrentUserProfile(Jwt jwt) {
        String username = getUsername(jwt);
        return userRepository.findByUsername(username)
                .map(databaseUser -> createUserProfileFromDatabase(jwt, databaseUser))
                .orElseGet(() -> createUserProfileFromToken(jwt, username));
    }

    private UserProfileResponse createUserProfileFromDatabase(Jwt jwt, UserEntity databaseUser) {
        return new UserProfileResponse(
                databaseUser.getUsername(),
                databaseUser.getFullName(),
                databaseUser.getPosition(),
                getRoles(jwt, databaseUser)
        );
    }

    private UserProfileResponse createUserProfileFromToken(Jwt jwt, String username) {
        return new UserProfileResponse(
                username,
                getFullName(jwt, username),
                DEFAULT_POSITION,
                KeycloakJwtRoles.extract(jwt)
        );
    }

    private String getUsername(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        return preferredUsername != null && !preferredUsername.isBlank()
                ? preferredUsername
                : jwt.getSubject();
    }

    private String getFullName(Jwt jwt, String username) {
        String fullName = jwt.getClaimAsString("name");
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }

    private List<String> getRoles(Jwt jwt, UserEntity databaseUser) {
        List<String> jwtRoles = KeycloakJwtRoles.extract(jwt);
        if (!jwtRoles.isEmpty()) {
            return jwtRoles;
        }

        String databaseRoles = databaseUser.getRoles();
        if (databaseRoles == null || databaseRoles.isBlank()) {
            return List.of();
        }

        return Arrays.stream(databaseRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .toList();
    }
}
