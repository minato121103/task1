package com.example.backend.service.impl;

import com.example.backend.controller.dto.AuthTokenResponse;
import com.example.backend.service.KeycloakAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class KeycloakAuthServiceImpl implements KeycloakAuthService {

    private static final String CODE_CHALLENGE_METHOD = "S256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RestTemplate restTemplate = new RestTemplate();
    private final ConcurrentMap<String, String> pkceVerifiers = new ConcurrentHashMap<>();
    private final String authorizationUrl;
    private final String tokenUrl;
    private final String logoutUrl;
    private final String clientId;
    private final String clientSecret;
    private final String backendRedirectUri;
    private final String frontendRedirectUri;

    public KeycloakAuthServiceImpl(
            @Value("${app.keycloak.base-url}") String baseUrl,
            @Value("${app.keycloak.realm}") String realm,
            @Value("${app.keycloak.client-id}") String clientId,
            @Value("${app.keycloak.client-secret}") String clientSecret,
            @Value("${app.auth.backend-redirect-uri:http://localhost:8080/api/auth/callback}") String backendRedirectUri,
            @Value("${app.auth.frontend-redirect-uri:http://localhost:4200}") String frontendRedirectUri
    ) {
        String realmBaseUrl = baseUrl + "/realms/" + realm + "/protocol/openid-connect";
        this.authorizationUrl = realmBaseUrl + "/auth";
        this.tokenUrl = realmBaseUrl + "/token";
        this.logoutUrl = realmBaseUrl + "/logout";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.backendRedirectUri = backendRedirectUri;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @Override
    public String buildAuthorizationRedirectUrl() {
        String state = createUrlSafeToken(32);
        String codeVerifier = createUrlSafeToken(64);
        pkceVerifiers.put(state, codeVerifier);

        return UriComponentsBuilder.fromUriString(authorizationUrl)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", backendRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email")
                .queryParam("prompt", "login")
                .queryParam("state", state)
                .queryParam("code_challenge", createCodeChallenge(codeVerifier))
                .queryParam("code_challenge_method", CODE_CHALLENGE_METHOD)
                .build()
                .toUriString();
    }

    @Override
    public String buildCallbackErrorRedirectUrl(String error, String errorDescription) {
        return UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment("error=" + error + "&error_description=" + errorDescription)
                .build()
                .toUriString();
    }

    @Override
    public String exchangeCodeAndBuildFrontendRedirectUrl(String code, String state) {
        String codeVerifier = state != null ? pkceVerifiers.remove(state) : null;
        if (codeVerifier == null) {
            return buildCallbackErrorRedirectUrl("invalid_state", "Missing PKCE code verifier");
        }

        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", backendRedirectUri);
        form.add("code_verifier", codeVerifier);

        AuthTokenResponse token = toTokenResponse(postForm(tokenUrl, form));
        return UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment("access_token=" + token.accessToken()
                        + "&refresh_token=" + token.refreshToken()
                        + "&expires_in=" + token.expiresIn()
                        + "&refresh_expires_in=" + token.refreshExpiresIn()
                        + "&token_type=" + token.tokenType())
                .build()
                .toUriString();
    }

    @Override
    public AuthTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return toTokenResponse(postForm(tokenUrl, form));
    }

    @Override
    public void logout(String refreshToken) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("refresh_token", refreshToken);
        postForm(logoutUrl, form);
    }

    private MultiValueMap<String, String> baseClientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<>() {
                }
        );
        return response.getBody();
    }

    private AuthTokenResponse toTokenResponse(Map<String, Object> token) {
        if (token == null) {
            throw new IllegalStateException("Keycloak did not return token response body");
        }

        return new AuthTokenResponse(
                (String) token.get("access_token"),
                (String) token.get("refresh_token"),
                ((Number) token.getOrDefault("expires_in", 0)).longValue(),
                ((Number) token.getOrDefault("refresh_expires_in", 0)).longValue(),
                (String) token.getOrDefault("token_type", "Bearer")
        );
    }

    private String createCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 algorithm is not available", error);
        }
    }

    private String createUrlSafeToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
