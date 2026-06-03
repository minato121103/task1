package com.example.backend.service;

import com.example.backend.controller.dto.AuthTokenResponse;

public interface KeycloakAuthService {
    String buildAuthorizationRedirectUrl();

    String buildCallbackErrorRedirectUrl(String error, String errorDescription);

    String exchangeCodeAndBuildFrontendRedirectUrl(String code, String state);

    AuthTokenResponse refresh(String refreshToken);

    void logout(String refreshToken);
}
