package com.nexhub.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El email o username es obligatorio")
        @Size(max = 255, message = "El identificador no puede superar los 255 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 100, message = "La contraseña es demasiado larga")
        String password
) {
}
