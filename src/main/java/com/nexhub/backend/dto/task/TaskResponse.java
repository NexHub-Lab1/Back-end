package com.nexhub.backend.dto.task;

import com.nexhub.backend.model.Task;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;

public record TaskResponse(
        Long id,
        Long projectId,
        String projectName,
        String title,
        String description,
        String deliverables,
        BigDecimal rewardAmount,
        String rewardCurrency,
        Date deadline,
        String status,
        String fundingStatus,
        Integer maxAttempts,
        Integer minReputation,
        Date createdAt,
        Date updatedAt,
        Boolean collaborative,
        List<String> recommendedSkills,
        String taskType
        Long githubIssueId,
        Integer githubIssueNumber,
        String githubIssueUrl,
        String githubIssueState,
        String githubIssueSyncStatus,
        String githubIssueLastError,
        Timestamp githubIssueLastSyncedAt
) {
    public static TaskResponse fromTask(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProject() != null ? task.getProject().getId() : null,
                task.getProject() != null ? task.getProject().getName() : null,
                task.getTitle(),
                task.getDescription(),
                task.getDeliverables(),
                task.getRewardAmount(),
                task.getRewardCurrency(),
                task.getDeadline(),
                task.getStatus(),
                task.getFundingStatus() == null || task.getFundingStatus().isBlank()
                        ? "unfunded"
                        : task.getFundingStatus(),
                task.getMaxAttempts(),
                task.getMinReputation() != null ? task.getMinReputation() : 0,
                task.getCreated_at(),
                task.getUpdated_at(),
                Boolean.TRUE.equals(task.getCollaborative()),
                task.getRecommendedSkills() == null
                        ? List.of()
                        : task.getRecommendedSkills().stream()
                        .map(tag -> tag.getName())
                        .sorted(String::compareToIgnoreCase)
                        .toList(),
                task.getTaskType()
                task.getGithubIssueId(),
                task.getGithubIssueNumber(),
                task.getGithubIssueUrl(),
                task.getGithubIssueState(),
                task.getGithubIssueSyncStatus(),
                task.getGithubIssueLastError(),
                task.getGithubIssueLastSyncedAt()
        );
    }
}
