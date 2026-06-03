package com.example.backend.service;

import com.example.backend.controller.dto.UserProfileResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserProfileService {
    UserProfileResponse getCurrentUserProfile(Jwt jwt);
}
