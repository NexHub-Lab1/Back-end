package com.nexhub.backend.dto.taskinvitation;

import jakarta.validation.constraints.NotNull;

public record TaskInvitationRequest(
        @NotNull(message = "Task ID is required") Long taskId,
        @NotNull(message = "Receiver ID is required") Long receiverId
) {}
