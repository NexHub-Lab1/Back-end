package com.nexhub.backend.dto.dashboard;

public record UserStatsDTO(
        Integer totalPoints,
        Integer reputationScore,
        Integer streakDay
) {}
