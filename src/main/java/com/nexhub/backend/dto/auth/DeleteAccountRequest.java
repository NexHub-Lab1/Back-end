package com.nexhub.backend.dto.auth;

public record DeleteAccountRequest(
        String email,
        String password
) {
}
