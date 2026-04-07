package com.nexhub.backend.dto.project;

import java.util.Set;

public record ProjectRequest(
        Long ownerId,
        String name,
        String description,
        String githubRepo,
        String status,
        Set<String> tags
) {
}
