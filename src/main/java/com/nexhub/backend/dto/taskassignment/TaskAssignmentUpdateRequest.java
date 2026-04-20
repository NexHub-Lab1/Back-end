package com.nexhub.backend.dto.taskassignment;

public record TaskAssignmentUpdateRequest(
        Long id,
        String status,
        Integer attemptsUsed
) {
}
