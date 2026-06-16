package com.nexhub.backend.dto.task;

import java.util.List;

public record FeaturedTaskResponse(
        TaskResponse task,
        Integer recommendationScore,
        List<String> recommendationReasons,
        List<String> matchedSkills,
        Boolean eligible
) {
}
