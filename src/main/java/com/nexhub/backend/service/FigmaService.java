package com.nexhub.backend.service;

import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FigmaService {
    private static final String FIGMA_AUTHORIZE_URL = "https://www.figma.com/oauth";
    private static final String FIGMA_ACCESS_TOKEN_URL = "https://api.figma.com/v1/oauth/token";
    private static final String FIGMA_REFRESH_TOKEN_URL = "https://api.figma.com/v1/oauth/refresh";
    private static final String FIGMA_USER_URL = "https://api.figma.com/v1/me";
    private static final String FIGMA_USER_AGENT = "NexHub";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    @Value("${figma.oauth.client-id}")
    private String clientId;

    @Value("${figma.oauth.client-secret}")
    private String clientSecret;

    @Value("${figma.oauth.redirect-uri}")
    private String redirectUri;

    public FigmaAuthorizationRequest buildAuthorizationRequest() {
        requireOAuthConfiguration();
        String state = jwtUtils.generateStateToken("figma-oauth");
        String query = "client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("current_user:read,file_metadata:read")
                + "&state=" + encode(state)
                + "&response_type=code";
        return new FigmaAuthorizationRequest(FIGMA_AUTHORIZE_URL + "?" + query, state);
    }

    public boolean usesSecureRedirect() {
        return redirectUri != null && redirectUri.startsWith("https://");
    }

    public FigmaLoginResult authenticateWithFigma(String code, String state, String expectedState) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Figma authorization code is required");
        }
        if (expectedState == null || !expectedState.equals(state)
                || !jwtUtils.validateStateToken(state, "figma-oauth")) {
            throw new IllegalArgumentException("Invalid Figma OAuth state");
        }

        FigmaOAuthTokens tokens = exchangeCodeForTokens(code);
        FigmaUserProfile figmaUser = fetchFigmaUser(tokens.accessToken());
        FigmaUserLogin figmaUserLogin = loginOrCreateFigmaUser(figmaUser, tokens);

        return new FigmaLoginResult(
                figmaUserLogin.user(),
                jwtUtils.generateToken(figmaUserLogin.user().getEmail()),
                figmaUserLogin.firstFigmaLogin()
        );
    }

    private FigmaOAuthTokens exchangeCodeForTokens(String code) {
        requireOAuthConfiguration();
        String requestBody = "redirect_uri=" + encode(redirectUri)
                + "&code=" + encode(code)
                + "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(FIGMA_ACCESS_TOKEN_URL))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.AUTHORIZATION, basicAuthorization())
                .header(HttpHeaders.USER_AGENT, FIGMA_USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Unable to exchange Figma authorization code");
        }

        try {
            Map<String, Object> payload = jsonParser.parseMap(response.body());
            String accessToken = asString(payload.get("access_token"));
            if (accessToken.isBlank() || "null".equals(accessToken)) {
                throw new IllegalArgumentException("Figma access token is missing");
            }
            return new FigmaOAuthTokens(
                    accessToken,
                    asNullableString(payload.get("refresh_token")),
                    expirationFrom(payload.get("expires_in"))
            );
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unable to parse Figma token response");
        }
    }

    private FigmaUserProfile fetchFigmaUser(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(FIGMA_USER_URL))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, FIGMA_USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Unable to load Figma user: " + response.body());
        }

        try {
            Map<String, Object> payload = jsonParser.parseMap(response.body());
            return new FigmaUserProfile(
                    asString(payload.get("id")),
                    asString(payload.get("handle")),
                    asNullableString(payload.get("email")),
                    asNullableString(payload.get("img_url"))
            );
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unable to parse Figma user response");
        }
    }

    private FigmaUserLogin loginOrCreateFigmaUser(FigmaUserProfile figmaUser, FigmaOAuthTokens tokens) {
        if (figmaUser.id() == null || figmaUser.handle() == null || figmaUser.handle().isBlank()) {
            throw new IllegalArgumentException("Incomplete Figma user profile");
        }

        User user = userRepository.findByFigmaId(figmaUser.id())
                .orElseGet(() -> {
                    if (figmaUser.email() != null && !figmaUser.email().isBlank()) {
                        return userRepository.findByEmail(figmaUser.email()).orElseGet(User::new);
                    }
                    return new User();
                });
        boolean firstFigmaLogin = user.getFigma_id() == null;

        if (user.getId() == null) {
            user.setUsername(resolveUniqueUsername(figmaUser.handle()));
            user.setEmail(resolveUniqueEmail(figmaUser.email(), figmaUser.handle()));
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

            Date now = new Date(System.currentTimeMillis());
            user.setCreated_at(now);
            user.setStatus("active");
            user.setTotal_points(0);
            user.setStreak_day(0);
            user.setReputation_score(0);
        }

        user.setFigma_id(figmaUser.id());
        user.setFigma_username(figmaUser.handle());
        user.setFigma_access_token(tokens.accessToken());
        user.setFigma_refresh_token(tokens.refreshToken());
        user.setFigma_token_expires_at(tokens.expiresAt());

        if (user.getProfile_image_url() == null || user.getProfile_image_url().isBlank()) {
            user.setProfile_image_url(figmaUser.imgUrl());
        }

        user.setLast_active_at(new Date(System.currentTimeMillis()));
        user.setUpdated_at(new Date(System.currentTimeMillis()));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            user.setEmail(resolveUniqueEmail(figmaUser.email(), figmaUser.handle()));
        }

        return new FigmaUserLogin(userRepository.save(user), firstFigmaLogin);
    }

    private String resolveUniqueUsername(String baseUsername) {
        String normalized = baseUsername == null || baseUsername.isBlank() ? "figma-user" : baseUsername.trim();
        normalized = normalized.replaceAll("[^a-zA-Z0-9_-]", "");
        if (normalized.isBlank()) {
            normalized = "figma-user";
        }

        if (!userRepository.existsByUsername(normalized)) {
            return normalized;
        }

        int suffix = 1;
        while (userRepository.existsByUsername(normalized + "-" + suffix)) {
            suffix++;
        }
        return normalized + "-" + suffix;
    }

    private String resolveUniqueEmail(String preferredEmail, String handle) {
        String candidate = preferredEmail == null || preferredEmail.isBlank()
                ? buildFallbackEmail(handle)
                : preferredEmail.trim();

        if (!userRepository.existsByEmail(candidate)) {
            return candidate;
        }

        String localPart = candidate.substring(0, candidate.indexOf('@'));
        String domain = candidate.substring(candidate.indexOf('@') + 1);
        int suffix = 1;
        while (userRepository.existsByEmail(localPart + "+" + suffix + "@" + domain)) {
            suffix++;
        }
        return localPart + "+" + suffix + "@" + domain;
    }


    public String extractFileKey(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Figma URL is required");
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("figma.com") || host.toLowerCase().endsWith(".figma.com"))) {
                throw new IllegalArgumentException("Invalid Figma file or design URL");
            }

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^/(?:file|design)/([^/\\s?#]+)");
            java.util.regex.Matcher matcher = pattern.matcher(uri.getPath());
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Figma file or design URL");
        }
        throw new IllegalArgumentException("Invalid Figma file or design URL");
    }

    public FigmaFileMetadata fetchFileMetadata(String fileKey, User owner) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("Figma file key is required");
        }
        if (owner == null) {
            throw new IllegalArgumentException("Figma account owner is required");
        }

        String accessToken = currentAccessToken(owner);
        String url = "https://api.figma.com/v1/files/" + encode(fileKey) + "/meta";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.USER_AGENT, FIGMA_USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Unable to load Figma file metadata: " + response.body());
        }

        try {
            Map<String, Object> payload = jsonParser.parseMap(response.body());
            Map<String, Object> file = asMap(payload.get("file"));
            return new FigmaFileMetadata(
                    asString(file.get("name")),
                    asNullableString(file.get("thumbnail_url"))
            );
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unable to parse Figma file response");
        }
    }

    private String currentAccessToken(User owner) {
        String accessToken = owner.getFigma_access_token();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("You must connect your Figma account first.");
        }

        Timestamp expiresAt = owner.getFigma_token_expires_at();
        boolean expiresSoon = expiresAt != null
                && expiresAt.toInstant().isBefore(Instant.now().plusSeconds(60));
        if (!expiresSoon) {
            return accessToken;
        }

        String refreshToken = owner.getFigma_refresh_token();
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Your Figma connection expired. Sign in with Figma again.");
        }

        FigmaOAuthTokens refreshed = refreshAccessToken(refreshToken);
        owner.setFigma_access_token(refreshed.accessToken());
        owner.setFigma_token_expires_at(refreshed.expiresAt());
        userRepository.save(owner);
        return refreshed.accessToken();
    }

    private FigmaOAuthTokens refreshAccessToken(String refreshToken) {
        requireOAuthConfiguration();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(FIGMA_REFRESH_TOKEN_URL))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.AUTHORIZATION, basicAuthorization())
                .header(HttpHeaders.USER_AGENT, FIGMA_USER_AGENT)
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString("refresh_token=" + encode(refreshToken)))
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Your Figma connection expired. Sign in with Figma again.");
        }

        try {
            Map<String, Object> payload = jsonParser.parseMap(response.body());
            String accessToken = asString(payload.get("access_token"));
            if (accessToken.isBlank()) {
                throw new IllegalArgumentException("Figma access token is missing");
            }
            return new FigmaOAuthTokens(accessToken, refreshToken, expirationFrom(payload.get("expires_in")));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unable to refresh Figma connection");
        }
    }

    public record FigmaFileMetadata(String name, String thumbnailUrl) {}

    private String buildFallbackEmail(String handle) {
        String normalized = handle == null || handle.isBlank() ? "figma-user" : handle.trim().replaceAll("[^a-zA-Z0-9_-]", "");
        return normalized + "@users.noreply.figma.local";
    }

    HttpResponse<String> send(HttpRequest request) {
        try {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to contact Figma API");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Figma request was interrupted");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String basicAuthorization() {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private void requireOAuthConfiguration() {
        if (clientId == null || clientId.isBlank()
                || clientSecret == null || clientSecret.isBlank()
                || redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("Figma OAuth is not configured");
        }
    }

    private Timestamp expirationFrom(Object expiresInValue) {
        if (expiresInValue == null) {
            return null;
        }
        long expiresIn = expiresInValue instanceof Number number
                ? number.longValue()
                : Long.parseLong(String.valueOf(expiresInValue));
        return Timestamp.from(Instant.now().plusSeconds(expiresIn));
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String asNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Expected object in Figma response");
    }

    public record FigmaAuthorizationRequest(String url, String state) {}
    public record FigmaLoginResult(User user, String token, boolean firstFigmaLogin) {}

    private record FigmaUserLogin(User user, boolean firstFigmaLogin) {}

    private record FigmaUserProfile(
            String id,
            String handle,
            String email,
            String imgUrl
    ) {}

    private record FigmaOAuthTokens(
            String accessToken,
            String refreshToken,
            Timestamp expiresAt
    ) {}
}
