package com.nexhub.backend.model;

import java.util.Locale;

public enum TaskType {
    DEVELOPMENT,
    DESIGN;

    public static TaskType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return DEVELOPMENT;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("VISUAL".equals(normalized)) {
            return DESIGN;
        }

        try {
            return TaskType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Task type must be DEVELOPMENT or DESIGN");
        }
    }
}
