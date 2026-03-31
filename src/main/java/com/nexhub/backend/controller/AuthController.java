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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthUserResponse>> login(@RequestBody LoginRequest request) {
        try {
            User user = authService.login(request.email(), request.password());
            return ResponseEntity.ok(ApiResponse.success("Login exitoso", AuthUserResponse.fromUser(user)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthUserResponse>> signup(@RequestBody AuthRequest request) {
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
    public ResponseEntity<ApiResponse<AuthUserResponse>> updateAccount(@RequestBody UpdateAccountRequest request) {
        try {
            User user = authService.updateAccount(
                    request.currentEmail(),
                    request.currentPassword(),
                    request.newUsername(),
                    request.newEmail(),
                    request.newPassword()
            );
            return ResponseEntity.ok(ApiResponse.success("Cuenta actualizada correctamente", AuthUserResponse.fromUser(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
