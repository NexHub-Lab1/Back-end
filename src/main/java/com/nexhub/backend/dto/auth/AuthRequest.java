package com.nexhub.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 3, max = 20, message = "El nombre de usuario debe tener entre 3 y 20 caracteres")
        String username,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El formato del email es inválido")
        @Size(max = 255, message = "El email no puede superar los 255 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres")
        String password
) {
}
