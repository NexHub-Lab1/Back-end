package com.nexhub.backend.dto.auth;

import com.nexhub.backend.model.User;

public record AuthUserResponse(
        Long id,
        String username,
        String email,
        Integer githubId,
        String githubUsername,
        String profileImageUrl
) {
    public static AuthUserResponse fromUser(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getGithub_id(),
                user.getGithub_username(),
                user.getProfile_image_url()
        );
    }
}
