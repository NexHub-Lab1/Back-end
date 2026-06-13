package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.taskinvitation.TaskInvitationRequest;
import com.nexhub.backend.dto.taskinvitation.TaskInvitationResponse;
import com.nexhub.backend.model.User;
import com.nexhub.backend.service.TaskInvitationService;
import com.nexhub.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task-invitations")
@RequiredArgsConstructor
public class TaskInvitationController {

    private final TaskInvitationService taskInvitationService;
    private final UserService userService;

    @PostMapping
    public ApiResponse<TaskInvitationResponse> createInvitation(
            Authentication authentication,
            @Valid @RequestBody TaskInvitationRequest request
    ) {
        try {
            User sender = userService.getUserByEmail(authentication.getName());
            TaskInvitationResponse response = taskInvitationService.createInvitation(sender, request);
            return new ApiResponse<>("success", "Invitation sent successfully", response);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/accept")
    public ApiResponse<TaskInvitationResponse> acceptInvitation(
            Authentication authentication,
            @PathVariable Long id
    ) {
        try {
            User receiver = userService.getUserByEmail(authentication.getName());
            TaskInvitationResponse response = taskInvitationService.acceptInvitation(receiver, id);
            return new ApiResponse<>("success", "Invitation accepted successfully", response);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @PutMapping("/{id}/reject")
    public ApiResponse<TaskInvitationResponse> rejectInvitation(
            Authentication authentication,
            @PathVariable Long id
    ) {
        try {
            User receiver = userService.getUserByEmail(authentication.getName());
            TaskInvitationResponse response = taskInvitationService.rejectInvitation(receiver, id);
            return new ApiResponse<>("success", "Invitation rejected successfully", response);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/pending")
    public ApiResponse<PagedResponse<TaskInvitationResponse>> getPendingInvitations(
            Authentication authentication,
            Pageable pageable
    ) {
        try {
            User user = userService.getUserByEmail(authentication.getName());
            PagedResponse<TaskInvitationResponse> response = taskInvitationService.getPendingInvitations(user, pageable);
            return new ApiResponse<>("success", "Pending invitations retrieved successfully", response);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<List<TaskInvitationResponse>> getInvitationsByTask(
            @PathVariable Long taskId
    ) {
        try {
            List<TaskInvitationResponse> response = taskInvitationService.getInvitationsByTask(taskId);
            return new ApiResponse<>("success", "Task invitations retrieved successfully", response);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
