package com.nexhub.backend.dto.tasksubmission;

public record TaskSubmissionUpdateRequest(
        Long id,
        String pullRequestUrl,
        String status,
        String reviewComments,
        Long reviewerId
) {
}
