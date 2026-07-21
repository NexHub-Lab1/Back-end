package com.nexhub.backend.dto;

import com.nexhub.backend.model.User;

import java.sql.Date;
import java.util.List;

public record UserDetailsResponse(
        Long id,
        String username,
        String email,
        String bio,
        List<String> skills,
        Integer streakDay,
        String image_url,
        Date last_active_at,
        Date created_at,
        Integer reputationScore,
        Integer totalPoints,
        String githubUsername,
        String figmaUsername
) {
    public static UserDetailsResponse fromUser(User user) {
        return new UserDetailsResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getBio(),
                user.getSkills() == null
                        ? List.of()
                        : user.getSkills().stream()
                        .map(tag -> tag.getName())
                        .sorted(String::compareToIgnoreCase)
                        .toList(),
                user.getStreak_day(),
                user.getProfile_image_url(),
                user.getLast_active_at(),
                user.getCreated_at(),
                user.getReputation_score() == null ? 0 : user.getReputation_score(),
                user.getTotal_points() == null ? 0 : user.getTotal_points(),
                user.getGithub_username(),
                user.getFigma_username()
        );
    }
}
