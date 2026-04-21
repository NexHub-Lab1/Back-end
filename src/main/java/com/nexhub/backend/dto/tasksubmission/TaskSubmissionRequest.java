package com.nexhub.backend.dto.tasksubmission;

public record TaskSubmissionRequest(
        Long assignmentId,
        String pullRequestUrl
) {
}
