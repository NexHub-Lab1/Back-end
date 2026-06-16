package com.nexhub.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El email o username es obligatorio")
        @Size(max = 80, message = "El identificador no puede superar los 80 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 40, message = "La contraseña no puede superar los 40 caracteres")
        String password
) {
}
