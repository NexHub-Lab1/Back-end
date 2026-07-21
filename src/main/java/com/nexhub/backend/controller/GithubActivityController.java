package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.github.GithubPullRequestCommentResponse;
import com.nexhub.backend.service.GithubActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/github/activity")
@RequiredArgsConstructor
public class GithubActivityController {
    private final GithubActivityService githubActivityService;

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<List<GithubPullRequestCommentResponse>>> getTaskActivity(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        try {
            if (authentication == null || authentication.getName() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Authentication required"));
            }
            return ResponseEntity.ok(ApiResponse.success(
                    "GitHub activity loaded",
                    githubActivityService.getTaskActivity(taskId, authentication.getName())
            ));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        }
    }
}
