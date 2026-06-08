package com.nexhub.backend.dto.task;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.Set;

public record TaskUpdateRequest(
        @NotNull(message = "El ID de la tarea es obligatorio")
        Long id,

        Long projectId,

        @Size(max = 100, message = "El título no puede superar los 100 caracteres")
        String title,

        @Size(max = 5000, message = "La descripción no puede superar los 5000 caracteres")
        String description,

        @Size(max = 5000, message = "Los entregables no pueden superar los 5000 caracteres")
        String deliverables,

        @DecimalMin(value = "0.0", inclusive = true, message = "La recompensa no puede ser negativa")
        BigDecimal rewardAmount,

        @Size(max = 10, message = "La moneda no puede superar los 10 caracteres")
        String rewardCurrency,

        Date deadline,

        @Size(max = 50, message = "El estado no puede superar los 50 caracteres")
        String status,

        @Min(value = 1, message = "Debe haber al menos 1 intento permitido")
        Integer maxAttempts,

        Set<String> recommendedSkills
) {
}
