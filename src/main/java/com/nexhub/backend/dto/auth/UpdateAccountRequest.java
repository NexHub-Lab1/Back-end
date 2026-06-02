package com.nexhub.backend.dto.auth;

import java.util.Set;

public record UpdateAccountRequest(
        String currentEmail,
        String currentPassword,
        String newUsername,
        String newEmail,
        String newPassword,
        Set<String> skills,
        Boolean emailNotificationsEnabled
) {
}
