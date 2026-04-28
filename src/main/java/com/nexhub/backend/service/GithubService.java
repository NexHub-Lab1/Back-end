package com.nexhub.backend.service;

import com.nexhub.backend.dto.github.GithubRepositoryResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class GithubService {
    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";
    private static final String GITHUB_USER_EMAILS_URL = "https://api.github.com/user/emails";
    private static final String GITHUB_USER_REPOS_URL_TEMPLATE = "https://api.github.com/users/%s/repos?sort=updated&per_page=100";

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    @Value("${github.oauth.client-id}")
    private String clientId;

    @Value("${github.oauth.client-secret}")
    private String clientSecret;

    @Value("${github.oauth.redirect-uri}")
    private String redirectUri;

    public String buildAuthorizationUrl() {
        String state = jwtUtils.generateStateToken("github-oauth");
        String query = "client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("read:user user:email")
                + "&state=" + encode(state);
        return GITHUB_AUTHORIZE_URL + "?" + query;
    }

    public GithubLoginResult authenticateWithGithub(String code, String state) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("GitHub authorization code is required");
        }
        if (!jwtUtils.validateStateToken(state, "github-oauth")) {
            throw new IllegalArgumentException("Invalid GitHub OAuth state");
        }

        String accessToken = exchangeCodeForAccessToken(code);
        GithubUserProfile githubUser = fetchGithubUser(accessToken);
        String email = fetchPrimaryEmail(accessToken, githubUser);
        User user = loginOrCreateGithubUser(githubUser, email);

        return new GithubLoginResult(user, jwtUtils.generateToken(user.getEmail()));
    }

    @Transactional(readOnly = true)
    public List<GithubRepositoryResponse> getUserRepositories(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        if (user.getGithub_username() == null || user.getGithub_username().isBlank()) {
            throw new IllegalArgumentException("GitHub account is not connected");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(GITHUB_USER_REPOS_URL_TEMPLATE, encodePathSegment(user.getGithub_username()))))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Unable to load GitHub repositories");
        }

        try {
            List<Object> payloads = jsonParser.parseList(response.body());
            List<GithubRepositoryResponse> repositories = new ArrayList<>();
            for (Object payloadObject : payloads) {
                Map<String, Object> payload = asMap(payloadObject);
                repositories.add(new GithubRepositoryResponse(
                        asLong(payload.get("id")),
                        asString(payload.get("name")),
                        asString(payload.get("full_name")),
                        asNullableString(payload.get("description")),
                        asString(payload.get("html_url")),
                        asBoolean(payload.get("private"))
                ));
            }
            return repositories;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unable to parse GitHub repositories");
        }
    }

    private String exchangeCodeForAccessToken(String code) {
        String requestBody = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_ACCESS_TOKEN_URL))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Unable to exchange GitHub code for token");
        }

        try {
            Map<String, Object> payload = jsonParser.parseMap(response.body());
            String accessToken = asString(payload.get("access_token"));
            if (accessToken.isBlank()) {
                throw new IllegalArgumentException("GitHub access token is missing");
            }
            return accessToken;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unable to parse GitHub token response");
        }
    }

    private GithubUserProfile fetchGithubUser(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_USER_URL))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw new IllegalArgumentException("Unable to load GitHub user");
        }

        try {
            Map<String, Object> payload = jsonParser.parseMap(response.body());
            return new GithubUserProfile(
                    asInteger(payload.get("id")),
                    asString(payload.get("login")),
                    asNullableString(payload.get("email")),
                    asNullableString(payload.get("avatar_url"))
            );
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Unable to parse GitHub user response");
        }
    }

    private String fetchPrimaryEmail(String accessToken, GithubUserProfile githubUser) {
        if (githubUser.email() != null && !githubUser.email().isBlank()) {
            return githubUser.email().trim();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_USER_EMAILS_URL))
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            return buildFallbackEmail(githubUser.login());
        }

        try {
            List<Object> emails = jsonParser.parseList(response.body());
            return emails.stream()
                    .map(this::asMap)
                    .filter(emailData -> asBoolean(emailData.get("primary")))
                    .map(emailData -> asNullableString(emailData.get("email")))
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(buildFallbackEmail(githubUser.login()));
        } catch (RuntimeException e) {
            return buildFallbackEmail(githubUser.login());
        }
    }

    private User loginOrCreateGithubUser(GithubUserProfile githubUser, String email) {
        if (githubUser.id() == null || githubUser.login() == null || githubUser.login().isBlank()) {
            throw new IllegalArgumentException("Incomplete GitHub user profile");
        }

        User user = userRepository.findByGithubId(githubUser.id())
                .orElseGet(() -> userRepository.findByEmail(email).orElseGet(User::new));

        if (user.getId() == null) {
            user.setUsername(resolveUniqueUsername(githubUser.login()));
            user.setEmail(resolveUniqueEmail(email));
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

            Date now = new Date(System.currentTimeMillis());
            user.setCreated_at(now);
            user.setStatus("active");
            user.setTotal_points(0);
            user.setStreak_day(0);
            user.setReputation_score(0);
        }

        user.setGithub_id(githubUser.id());
        user.setGithub_username(githubUser.login());
        user.setProfile_image_url(githubUser.avatarUrl());
        user.setLast_active_at(new Date(System.currentTimeMillis()));
        user.setUpdated_at(new Date(System.currentTimeMillis()));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            user.setEmail(resolveUniqueEmail(email));
        }

        return userRepository.save(user);
    }

    private String resolveUniqueUsername(String baseUsername) {
        String normalized = baseUsername == null || baseUsername.isBlank() ? "github-user" : baseUsername.trim();
        if (!userRepository.existsByUsername(normalized)) {
            return normalized;
        }

        int suffix = 1;
        while (userRepository.existsByUsername(normalized + "-" + suffix)) {
            suffix++;
        }
        return normalized + "-" + suffix;
    }

    private String resolveUniqueEmail(String preferredEmail) {
        String candidate = preferredEmail == null || preferredEmail.isBlank()
                ? buildFallbackEmail("github-user")
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

    private String buildFallbackEmail(String githubLogin) {
        String normalized = githubLogin == null || githubLogin.isBlank() ? "github-user" : githubLogin.trim();
        return normalized + "@users.noreply.github.local";
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to contact GitHub");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("GitHub request was interrupted");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePathSegment(String value) {
        return value.replace(" ", "%20");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Unexpected GitHub response shape");
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String asNullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(asString(value));
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(asString(value));
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(asString(value));
    }

    public record GithubLoginResult(User user, String token) {}

    private record GithubUserProfile(
            Integer id,
            String login,
            String email,
            String avatarUrl
    ) {}
}
