package com.nexhub.backend.dto.task;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Set;

public record TaskRequest(
        Long projectId,
        String title,
        String description,
        String deliverables,
        BigDecimal rewardAmount,
        String rewardCurrency,
        Date deadline,
        String status,
        Integer maxAttempts,
        Set<String> recommendedSkills
) {
}
