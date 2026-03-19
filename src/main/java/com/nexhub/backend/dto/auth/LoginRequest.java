package com.nexhub.backend.dto.auth;

public record LoginRequest(
        String email,
        String password
) {
}
