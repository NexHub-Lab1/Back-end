package com.nexhub.backend.dto.project;

import java.util.Set;

public record ProjectUpdateRequest(
        Long id,
        String name,
        String description,
        String githubRepo,
        String status,
        Set<String> tags
) {
}
