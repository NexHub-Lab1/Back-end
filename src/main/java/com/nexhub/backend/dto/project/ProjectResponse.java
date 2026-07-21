package com.nexhub.backend.dto.project;

import com.nexhub.backend.model.Project;

import java.sql.Date;
import java.util.List;

public record ProjectResponse(
        Long id,
        Long ownerId,
        String ownerUsername,
        String name,
        String description,
        String githubRepo,
        String status,
        Date createdAt,
        Date updatedAt,
        Date lastActiveAt,
        Long completedTasksCount,
        Long starsCount,
        int contributorCount,
        List<String> tags,
        String githubWebhookStatus,
        String githubWebhookLastError,
        Date githubWebhookConnectedAt,
        Date githubWebhookLastDeliveryAt,
        String figmaFileUrl,
        String figmaFileName,
        String figmaThumbnailUrl
) {
    public ProjectResponse(
            Long id,
            Long ownerId,
            String ownerUsername,
            String name,
            String description,
            String githubRepo,
            String status,
            Date createdAt,
            Date updatedAt,
            Date lastActiveAt,
            Long completedTasksCount,
            Long starsCount,
            int contributorCount,
            List<String> tags
    ) {
        this(
                id,
                ownerId,
                ownerUsername,
                name,
                description,
                githubRepo,
                status,
                createdAt,
                updatedAt,
                lastActiveAt,
                completedTasksCount,
                starsCount,
                contributorCount,
                tags,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ProjectResponse fromProject(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getOwner() != null ? project.getOwner().getId() : null,
                project.getOwner() != null ? project.getOwner().getUsername() : null,
                project.getName(),
                project.getDescription(),
                project.getGithubRepo(),
                project.getStatus(),
                project.getCreated_at(),
                project.getUpdated_at(),
                project.getLast_active_at(),
                project.getCompleted_tasks_count(),
                project.getStars_count(),
                project.getContributors() != null ? project.getContributors().size() : 0,
                project.getTags() == null
                        ? List.of()
                        : project.getTags().stream().map(tag -> tag.getName()).sorted(String::compareToIgnoreCase).toList(),
                project.getGithubWebhookStatus(),
                project.getGithubWebhookLastError(),
                project.getGithubWebhookConnectedAt(),
                project.getGithubWebhookLastDeliveryAt(),
                project.getFigmaFileUrl(),
                project.getFigmaFileKey(),
                project.getFigmaThumbnailUrl()
        );
    }
}
