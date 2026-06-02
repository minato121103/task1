package com.example.backend.controller;

import com.example.backend.controller.dto.AuthTokenResponse;
import com.example.backend.controller.dto.RefreshTokenRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String tokenUrl;
    private final String logoutUrl;
    private final String clientId;
    private final String clientSecret;
    private final String backendRedirectUri;
    private final String frontendRedirectUri;

    public AuthController(
            @Value("${app.keycloak.base-url}") String baseUrl,
            @Value("${app.keycloak.realm}") String realm,
            @Value("${app.keycloak.client-id}") String clientId,
            @Value("${app.keycloak.client-secret}") String clientSecret,
            @Value("${app.auth.backend-redirect-uri:http://localhost:8080/api/auth/callback}") String backendRedirectUri,
            @Value("${app.auth.frontend-redirect-uri:http://localhost:4200}") String frontendRedirectUri
    ) {
        String realmBaseUrl = baseUrl + "/realms/" + realm + "/protocol/openid-connect";
        this.tokenUrl = realmBaseUrl + "/token";
        this.logoutUrl = realmBaseUrl + "/logout";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.backendRedirectUri = backendRedirectUri;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription
    ) {
        if (error != null || code == null || code.isBlank()) {
            String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .fragment("error=" + (error != null ? error : "missing_code")
                            + "&error_description=" + (errorDescription != null ? errorDescription : "Keycloak did not return authorization code"))
                    .build()
                    .toUriString();
            return new RedirectView(redirectUrl);
        }

        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", backendRedirectUri);

        AuthTokenResponse token = toTokenResponse(postForm(tokenUrl, form));
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment("access_token=" + token.accessToken()
                        + "&refresh_token=" + token.refreshToken()
                        + "&expires_in=" + token.expiresIn()
                        + "&refresh_expires_in=" + token.refreshExpiresIn()
                        + "&token_type=" + token.tokenType())
                .build()
                .toUriString();

        return new RedirectView(redirectUrl);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", request.refreshToken());

        return ResponseEntity.ok(toTokenResponse(postForm(tokenUrl, form)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        MultiValueMap<String, String> form = baseClientForm();
        form.add("refresh_token", request.refreshToken());
        postForm(logoutUrl, form);
        return ResponseEntity.noContent().build();
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
}
