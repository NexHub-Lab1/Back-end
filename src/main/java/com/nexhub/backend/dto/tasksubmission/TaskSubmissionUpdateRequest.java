package com.nexhub.backend.dto.tasksubmission;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskSubmissionUpdateRequest(
        @NotNull(message = "El ID de la entrega es obligatorio")
        Long id,

        @Size(max = 500, message = "La URL del Pull Request no puede superar los 500 caracteres")
        String pullRequestUrl,

        @Size(max = 50, message = "El estado no puede superar los 50 caracteres")
        String status,

        @Size(max = 2000, message = "Los comentarios de revisión no pueden superar los 2000 caracteres")
        String reviewComments,

        Long reviewerId
) {
}
