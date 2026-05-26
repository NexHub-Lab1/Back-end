package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.model.Notification;
import com.nexhub.backend.model.User;
import com.nexhub.backend.service.NotificationService;
import com.nexhub.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping
    public ApiResponse<List<Notification>> getNotifications(Authentication authentication) {
        try {
            User user = userService.getUserByEmail(authentication.getName());
            return new ApiResponse<>("success", "Notifications retrieved", notificationService.getNotificationsForUser(user));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        try {
            notificationService.markAsRead(id);
            return new ApiResponse<>("success", "Notification marked as read", null);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
