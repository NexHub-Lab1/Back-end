package com.nexhub.backend.service;

import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaServiceTest {
    private UserRepository userRepository;
    private JwtUtils jwtUtils;
    private PasswordEncoder passwordEncoder;
    private FigmaService figmaService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtUtils = mock(JwtUtils.class);
        passwordEncoder = mock(PasswordEncoder.class);
        figmaService = spy(new FigmaService(userRepository, jwtUtils, passwordEncoder));
        ReflectionTestUtils.setField(figmaService, "clientId", "figma-client");
        ReflectionTestUtils.setField(figmaService, "clientSecret", "figma-secret");
        ReflectionTestUtils.setField(figmaService, "redirectUri", "https://backend.example/api/auth/figma/callback");
    }

    @Test
    void authorizationUsesMetadataScopeAndKeepsState() {
        when(jwtUtils.generateStateToken("figma-oauth")).thenReturn("signed-state");

        var authorization = figmaService.buildAuthorizationRequest();

        assertThat(authorization.state()).isEqualTo("signed-state");
        assertThat(authorization.url())
                .contains("current_user%3Aread%2Cfile_metadata%3Aread")
                .contains("state=signed-state");
    }

    @Test
    void loginExchangesCodeWithBasicAuthAndStoresRefreshData() {
        HttpResponse<String> tokenResponse = response(200, """
                {"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}
                """);
        HttpResponse<String> profileResponse = response(200, """
                {"id":"figma-7","handle":"alex.design","email":"alex@example.com","img_url":"https://img.example/alex.png"}
                """);
        List<HttpRequest> requests = new ArrayList<>();
        doAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            requests.add(request);
            return requests.size() == 1 ? tokenResponse : profileResponse;
        }).when(figmaService).send(any(HttpRequest.class));

        when(jwtUtils.validateStateToken("state", "figma-oauth")).thenReturn(true);
        when(jwtUtils.generateToken("alex@example.com")).thenReturn("nexhub-token");
        when(userRepository.findByFigmaId("figma-7")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("alexdesign")).thenReturn(false);
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = figmaService.authenticateWithFigma("authorization-code", "state", "state");

        String expectedBasic = "Basic " + Base64.getEncoder().encodeToString(
                "figma-client:figma-secret".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(requests.get(0).headers().firstValue("Authorization")).contains(expectedBasic);
        assertThat(result.token()).isEqualTo("nexhub-token");
        assertThat(result.user().getFigma_access_token()).isEqualTo("access-1");
        assertThat(result.user().getFigma_refresh_token()).isEqualTo("refresh-1");
        assertThat(result.user().getFigma_token_expires_at()).isAfter(Timestamp.from(Instant.now()));
    }

    @Test
    void expiredTokenIsRefreshedBeforeLoadingMetadata() {
        User owner = new User();
        owner.setFigma_access_token("expired-access");
        owner.setFigma_refresh_token("refresh-1");
        owner.setFigma_token_expires_at(Timestamp.from(Instant.now().minusSeconds(10)));

        HttpResponse<String> refreshResponse = response(200, """
                {"access_token":"access-2","expires_in":3600}
                """);
        HttpResponse<String> metadataResponse = response(200, """
                {"file":{"name":"Checkout redesign","thumbnail_url":"https://img.example/checkout.png"}}
                """);
        List<HttpRequest> requests = new ArrayList<>();
        doAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            requests.add(request);
            return requests.size() == 1 ? refreshResponse : metadataResponse;
        }).when(figmaService).send(any(HttpRequest.class));
        when(userRepository.save(owner)).thenReturn(owner);

        var metadata = figmaService.fetchFileMetadata("file-key", owner);

        assertThat(metadata.name()).isEqualTo("Checkout redesign");
        assertThat(owner.getFigma_access_token()).isEqualTo("access-2");
        assertThat(requests.get(1).uri().getPath()).isEqualTo("/v1/files/file-key/meta");
        assertThat(requests.get(1).headers().firstValue("Authorization")).contains("Bearer access-2");
        verify(userRepository).save(owner);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
