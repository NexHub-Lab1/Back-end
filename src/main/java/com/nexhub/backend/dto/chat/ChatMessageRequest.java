package com.nexhub.backend.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        @NotNull(message = "El ID de asignación es obligatorio")
        Long assignmentId,

        @NotBlank(message = "El contenido del mensaje no puede estar vacío")
        @Size(max = 1000, message = "El mensaje no puede superar los 1000 caracteres")
        String content
) {
}
