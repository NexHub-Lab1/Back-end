package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionRequest;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionUpdateRequest;
import com.nexhub.backend.service.TaskSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/task-submissions")
@RequiredArgsConstructor
public class TaskSubmissionController {
    private final TaskSubmissionService taskSubmissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TaskSubmissionResponse>>> getAll(
            @PageableDefault(size = 9, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("List of task submissions", taskSubmissionService.getAllSubmissions(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task submission found", taskSubmissionService.getSubmissionById(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<PagedResponse<TaskSubmissionResponse>>> getByTask(
            @PathVariable Long taskId,
            @PageableDefault(size = 9, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task submissions", taskSubmissionService.getSubmissionsByTask(taskId, pageable)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<ApiResponse<PagedResponse<TaskSubmissionResponse>>> getByAssignment(
            @PathVariable Long assignmentId,
            @PageableDefault(size = 9, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Assignment submissions", taskSubmissionService.getSubmissionsByAssignment(assignmentId, pageable)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PagedResponse<TaskSubmissionResponse>>> getByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 9, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success("User task submissions", taskSubmissionService.getSubmissionsByUser(userId, pageable)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/reviewer/{reviewerId}")
    public ResponseEntity<ApiResponse<PagedResponse<TaskSubmissionResponse>>> getByReviewer(
            @PathVariable Long reviewerId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 9, sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Reviewer task submissions",
                    taskSubmissionService.getSubmissionsToReview(reviewerId, status, pageable)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> create(@Valid @RequestBody TaskSubmissionRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Task submission created correctly", taskSubmissionService.createSubmission(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/updatesubmission")
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> update(@Valid @RequestBody TaskSubmissionUpdateRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task submission updated correctly", taskSubmissionService.updateSubmission(request)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> delete(@RequestBody Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task submission deleted successfully", taskSubmissionService.deleteSubmission(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }
}
