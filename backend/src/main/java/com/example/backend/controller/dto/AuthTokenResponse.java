package com.example.backend.controller.dto;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        String tokenType
) {
}
