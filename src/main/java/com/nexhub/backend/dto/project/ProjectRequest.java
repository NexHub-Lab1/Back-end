package com.nexhub.backend.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ProjectRequest(
        @NotNull(message = "El ID del propietario es obligatorio")
        Long ownerId,

        @NotBlank(message = "El nombre del proyecto es obligatorio")
        @Size(max = 100, message = "El nombre del proyecto no puede superar los 100 caracteres")
        String name,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
        String description,

        @NotBlank(message = "El repositorio de GitHub es obligatorio")
        @Size(max = 255, message = "La URL del repositorio es demasiado larga")
        String githubRepo,

        @Size(max = 50, message = "El estado no puede superar los 50 caracteres")
        String status,

        Set<String> tags
) {
}
