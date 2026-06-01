package com.nexhub.backend.dto.task;

import com.nexhub.backend.model.Task;

import java.math.BigDecimal;
import java.sql.Date;
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
        Date createdAt,
        Date updatedAt,
        List<String> recommendedSkills
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
                task.getCreated_at(),
                task.getUpdated_at(),
                task.getRecommendedSkills() == null
                        ? List.of()
                        : task.getRecommendedSkills().stream()
                        .map(tag -> tag.getName())
                        .sorted(String::compareToIgnoreCase)
                        .toList()
        );
    }
}
