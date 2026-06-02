package com.example.backend.controller.dto;

import java.util.List;

public record UserProfileResponse(
        String username,
        String fullName,
        String email,
        String position,
        List<String> roles
) {
}
