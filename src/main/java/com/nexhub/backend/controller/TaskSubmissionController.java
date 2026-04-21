package com.nexhub.backend.controller;

import com.nexhub.backend.dto.ApiResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionRequest;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionUpdateRequest;
import com.nexhub.backend.service.TaskSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/task-submissions")
@RequiredArgsConstructor
public class TaskSubmissionController {
    private final TaskSubmissionService taskSubmissionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TaskSubmissionResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("List of task submissions", taskSubmissionService.getAllSubmissions()));
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
    public ResponseEntity<ApiResponse<List<TaskSubmissionResponse>>> getByTask(@PathVariable Long taskId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Task submissions", taskSubmissionService.getSubmissionsByTask(taskId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<ApiResponse<List<TaskSubmissionResponse>>> getByAssignment(@PathVariable Long assignmentId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Assignment submissions", taskSubmissionService.getSubmissionsByAssignment(assignmentId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<TaskSubmissionResponse>>> getByUser(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success("User task submissions", taskSubmissionService.getSubmissionsByUser(userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> create(@RequestBody TaskSubmissionRequest request) {
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
    public ResponseEntity<ApiResponse<TaskSubmissionResponse>> update(@RequestBody TaskSubmissionUpdateRequest request) {
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
