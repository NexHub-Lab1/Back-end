package com.nexhub.backend.dto.task;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.Set;

public record TaskRequest(
        @NotNull(message = "El ID del proyecto es obligatorio")
        Long projectId,

        @NotBlank(message = "El título de la tarea es obligatorio")
        @Size(max = 100, message = "El título no puede superar los 100 caracteres")
        String title,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 5000, message = "La descripción no puede superar los 5000 caracteres")
        String description,

        @Size(max = 5000, message = "Los entregables no pueden superar los 5000 caracteres")
        String deliverables,

        @NotNull(message = "El monto de la recompensa es obligatorio")
        @DecimalMin(value = "0.0", inclusive = true, message = "La recompensa no puede ser negativa")
        BigDecimal rewardAmount,

        @NotBlank(message = "La moneda de la recompensa es obligatoria")
        @Size(max = 10, message = "La moneda no puede superar los 10 caracteres")
        String rewardCurrency,

        Date deadline,

        @Size(max = 50, message = "El estado no puede superar los 50 caracteres")
        String status,

        @NotNull(message = "El número máximo de intentos es obligatorio")
        @Min(value = 1, message = "Debe haber al menos 1 intento permitido")
        Integer maxAttempts,

        Integer minReputation,

        Boolean collaborative,

        Set<String> recommendedSkills,

        @Size(max = 20, message = "El tipo de tarea no puede superar los 20 caracteres")
        String taskType
) {
}
