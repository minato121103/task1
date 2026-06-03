package com.example.backend.service.impl;

import com.example.backend.controller.dto.UserProfileResponse;
import com.example.backend.model.UserEntity;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.KeycloakJwtRoles;
import com.example.backend.service.UserProfileService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

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
        UserEntity databaseUser = userRepository.findByUsername(username)
                .orElseGet(() -> createUserFromToken(jwt, username));

        return new UserProfileResponse(
                databaseUser.getUsername(),
                databaseUser.getFullName(),
                databaseUser.getEmail(),
                databaseUser.getPosition(),
                KeycloakJwtRoles.extract(jwt)
        );
    }

    private String getUsername(Jwt jwt) {
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        return preferredUsername != null && !preferredUsername.isBlank()
                ? preferredUsername
                : jwt.getSubject();
    }

    private UserEntity createUserFromToken(Jwt jwt, String username) {
        String email = jwt.getClaimAsString("email");
        String fullName = jwt.getClaimAsString("name");

        return new UserEntity(
                username,
                fullName != null && !fullName.isBlank() ? fullName : username,
                email != null && !email.isBlank() ? email : "unknown@example.com",
                DEFAULT_POSITION
        );
    }
}
