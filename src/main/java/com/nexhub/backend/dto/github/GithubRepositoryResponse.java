package com.nexhub.backend.dto.github;

public record GithubRepositoryResponse(
        Long id,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        boolean isPrivate
) {
}
