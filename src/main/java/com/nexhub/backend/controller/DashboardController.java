package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.dashboard.ProfileDashboardDTO;
import com.nexhub.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/profile/{userId}")
    public ResponseEntity<ApiResponse<ProfileDashboardDTO>> getProfileDashboard(@PathVariable Long userId) {
        try {
            ProfileDashboardDTO dashboard = dashboardService.getProfileDashboard(userId);
            return ResponseEntity.ok(ApiResponse.success("Dashboard data fetched successfully", dashboard));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
