package com.nexhub.backend.dto.tasksubmission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskSubmissionRequest(
        @NotNull(message = "El ID de asignación es obligatorio")
        Long assignmentId,

        @Size(max = 500, message = "La URL del Pull Request no puede superar los 500 caracteres")
        String pullRequestUrl,

        @Size(max = 500, message = "La URL de Figma o diseño no puede superar los 500 caracteres")
        String designUrl,

        String description,

        @Size(max = 500, message = "La URL de demostración no puede superar los 500 caracteres")
        String demoUrl
) {
}
