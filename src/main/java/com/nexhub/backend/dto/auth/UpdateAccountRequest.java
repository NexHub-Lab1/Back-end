package com.nexhub.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateAccountRequest(
        @Size(max = 80, message = "El email actual es demasiado largo")
        String currentEmail,

        @Size(max = 40, message = "La contraseña actual es demasiado larga")
        String currentPassword,

        @Size(max = 20, message = "El nuevo nombre de usuario no puede superar los 20 caracteres")
        String newUsername,

        @Email(message = "El formato del nuevo email es inválido")
        @Size(max = 80, message = "El nuevo email no puede superar los 80 caracteres")
        String newEmail,

        @Size(max = 40, message = "La nueva contraseña es demasiado larga")
        String newPassword,
        Set<String> skills,
        Boolean emailNotificationsEnabled
) {
}
