package com.nexhub.backend.dto.auth;

import com.nexhub.backend.model.User;

public record AuthUserResponse(
        Long id,
        String username,
        String email
) {
    public static AuthUserResponse fromUser(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
    }
}
