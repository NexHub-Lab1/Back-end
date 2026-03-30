package com.nexhub.backend.dto;

import com.nexhub.backend.model.User;

import java.sql.Date;

public record ProjectRequest (
        String name,
        User owner,
        String description,
        String gitRepo,
        String status
) {}