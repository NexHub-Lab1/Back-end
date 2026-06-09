package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.auth.AuthRequest;
import com.nexhub.backend.dto.auth.AuthUserResponse;
import com.nexhub.backend.dto.auth.DeleteAccountRequest;
import com.nexhub.backend.dto.auth.ForgotPasswordRequest;
import com.nexhub.backend.dto.auth.LoginRequest;
import com.nexhub.backend.dto.auth.ResetPasswordRequest;
import com.nexhub.backend.dto.auth.UpdateAccountRequest;
import com.nexhub.backend.model.User;
import com.nexhub.backend.service.AuthService;
import com.nexhub.backend.service.GithubService;
import com.nexhub.backend.utils.JwtUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtUtils jwtUtils;
    private final GithubService githubService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public AuthController(AuthService authService, JwtUtils jwtUtils, GithubService githubService) {
        this.authService = authService;
        this.jwtUtils = jwtUtils;
        this.githubService = githubService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = authService.login(request.email(), request.password());
            String token = jwtUtils.generateToken(user.getEmail());

            Map<String, Object> data = new HashMap<>();
            data.put("user", AuthUserResponse.fromUser(user));
            data.put("token", token);

            return ResponseEntity.ok(ApiResponse.success("Login exitoso", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthUserResponse>> signup(@Valid @RequestBody AuthRequest request) {
        System.out.println("Signup request: " + request);
        try {
            User user = authService.signup(request.username(), request.email(), request.password());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Usuario creado correctamente", AuthUserResponse.fromUser(user)
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<String>> signout() {
        return ResponseEntity.ok(ApiResponse.success(authService.signout(), null));
    }

    @PostMapping("/forgotpassword")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            String message = authService.forgotPassword(request.email());
            return ResponseEntity.ok(ApiResponse.success(message, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/resetpassword")
    public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            String message = authService.resetPassword(request.email(), request.newPassword());
            return ResponseEntity.ok(ApiResponse.success(message, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/deleteaccount")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@RequestBody DeleteAccountRequest request) {
        try {
            String message = authService.deleteAccount(request.email(), request.password());
            return ResponseEntity.ok(ApiResponse.success(message, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/updateaccount")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAccount(
            @Valid @RequestBody UpdateAccountRequest request,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Authentication required"));
            }

            User user = authService.updateAccount(
                    authentication.getName(),
                    request.currentPassword(),
                    request.newUsername(),
                    request.newEmail(),
                    request.newPassword(),
                    request.skills(),
                    request.emailNotificationsEnabled()
            );

            Map<String, Object> data = new HashMap<>();
            data.put("user", AuthUserResponse.fromUser(user));
            data.put("token", jwtUtils.generateToken(user.getEmail()));

            return ResponseEntity.ok(ApiResponse.success("Cuenta actualizada correctamente", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/github/start")
    public ResponseEntity<Void> startGithubLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", githubService.buildAuthorizationUrl())
                .build();
    }

    @GetMapping("/github/callback")
    public ResponseEntity<Void> githubCallback(String code, String state) {
        try {
            GithubService.GithubLoginResult result = githubService.authenticateWithGithub(code, state);
            AuthUserResponse authUser = AuthUserResponse.fromUser(result.user());

            String redirectUrl = frontendUrl
                    + "/auth/github/callback"
                    + "?token=" + encode(result.token())
                    + "&id=" + authUser.id()
                    + "&username=" + encode(authUser.username())
                    + "&email=" + encode(authUser.email())
                    + "&githubId=" + authUser.githubId()
                    + "&githubUsername=" + encode(nullSafe(authUser.githubUsername()))
                    + "&profileImageUrl=" + encode(nullSafe(authUser.profileImageUrl()))
                    + "&firstGithubLogin=" + result.firstGithubLogin();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", frontendUrl + "/auth/login?error=" + encode(e.getMessage()))
                    .build();
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
