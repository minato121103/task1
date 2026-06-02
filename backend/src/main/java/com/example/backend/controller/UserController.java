package com.example.backend.controller;

import com.example.backend.controller.dto.UserProfileResponse;
import com.example.backend.model.UserEntity;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.KeycloakJwtRoles;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final String DEFAULT_POSITION = "Position is not configured";

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String username = getUsername(jwt);
        UserEntity databaseUser = userRepository.findByUsername(username)
                .orElseGet(() -> createUserFromToken(jwt, username));

        return ResponseEntity.ok(new UserProfileResponse(
                databaseUser.getUsername(),
                databaseUser.getFullName(),
                databaseUser.getEmail(),
                databaseUser.getPosition(),
                KeycloakJwtRoles.extract(jwt)
        ));
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
