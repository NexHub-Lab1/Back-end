package com.nexhub.backend.dto.project;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ProjectUpdateRequest(
        @NotNull(message = "El ID del proyecto es obligatorio")
        Long id,

        @Size(max = 100, message = "El nombre del proyecto no puede superar los 100 caracteres")
        String name,

        @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
        String description,

        @Size(max = 255, message = "La URL del repositorio es demasiado larga")
        String githubRepo,

        @Size(max = 50, message = "El estado no puede superar los 50 caracteres")
        String status,

        Set<String> tags
) {
}
