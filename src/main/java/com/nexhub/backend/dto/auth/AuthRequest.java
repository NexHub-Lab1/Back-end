package com.nexhub.backend.dto.auth;

public record AuthRequest(
        String username,
        String email,
        String password
) {
}
