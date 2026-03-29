package com.nexhub.backend.dto.auth;

public record UpdateAccountRequest(
        String currentEmail,
        String currentPassword,
        String newUsername,
        String newEmail,
        String newPassword
) {
}
