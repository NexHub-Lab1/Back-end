package com.nexhub.backend.dto;

import com.nexhub.backend.model.User;

import java.sql.Date;

public record UserDetailsResponse(
        Long id,
        String username,
        String email,
        String bio,
        Integer streakDay,
        String image_url,
        Date last_active_at,
        Date created_at
) {
    public static UserDetailsResponse fromUser(User user) {
        return new UserDetailsResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBio(),
                user.getStreak_day(),
                user.getProfile_image_url(),
                user.getLast_active_at(),
                user.getCreated_at()
        );
    }
}
