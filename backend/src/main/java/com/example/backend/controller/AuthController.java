package com.example.backend.controller;

import com.example.backend.controller.dto.AuthTokenResponse;
import com.example.backend.controller.dto.RefreshTokenRequest;
import com.example.backend.service.KeycloakAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KeycloakAuthService keycloakAuthService;

    public AuthController(KeycloakAuthService keycloakAuthService) {
        this.keycloakAuthService = keycloakAuthService;
    }

    @GetMapping("/authorize")
    public RedirectView authorize() {
        return new RedirectView(keycloakAuthService.buildAuthorizationRedirectUrl());
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription
    ) {
        if (error != null || code == null || code.isBlank()) {
            return new RedirectView(keycloakAuthService.buildCallbackErrorRedirectUrl(
                    error != null ? error : "missing_code",
                    errorDescription != null ? errorDescription : "Keycloak did not return authorization code"
            ));
        }

        return new RedirectView(keycloakAuthService.exchangeCodeAndBuildFrontendRedirectUrl(code, state));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(keycloakAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        keycloakAuthService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
