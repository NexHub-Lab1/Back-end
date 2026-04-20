package com.nexhub.backend.dto.taskassignment;

public record TaskAssignmentRequest(
        Long taskId,
        Long userId
) {
}
