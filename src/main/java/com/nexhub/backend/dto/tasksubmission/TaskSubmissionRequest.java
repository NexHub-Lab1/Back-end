package com.nexhub.backend.dto.tasksubmission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskSubmissionRequest(
        @NotNull(message = "El ID de asignación es obligatorio")
        Long assignmentId,

        @NotBlank(message = "La URL del Pull Request es obligatoria")
        @Size(max = 500, message = "La URL del Pull Request no puede superar los 500 caracteres")
        String pullRequestUrl
) {
}
