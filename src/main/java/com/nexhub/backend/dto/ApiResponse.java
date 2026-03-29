package com.nexhub.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApiResponse<T> {
    private String status;      // "success" o "error"
    private String message;     // Un mensaje amigable
    private T data;             // El contenido real (User, Project, List, etc.)
    private LocalDateTime timestamp;

    public ApiResponse(String status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    // Métodos estáticos para facilitar el uso
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("success", message, data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("error", message, null);
    }
}