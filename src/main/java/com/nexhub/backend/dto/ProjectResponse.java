package com.nexhub.backend.dto;

import com.nexhub.backend.model.User;

import java.sql.Date;

public record ProjectResponse (
        String name,
        User owner,
        String description,
        String gitRepo,
        String status,
        Date created_at,
        Date updated_at,
        Long stars_count,
        Long contri_count
) {}
