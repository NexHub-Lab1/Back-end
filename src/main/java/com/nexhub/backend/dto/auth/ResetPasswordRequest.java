package com.nexhub.backend.dto.auth;

public record ResetPasswordRequest(
        String email,
        String newPassword
) {
}
